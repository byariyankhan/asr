package io.joinasr.app.earn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The camera, turned into poses.
 *
 * Every frame goes from CameraX to MediaPipe's Pose Landmarker, which runs
 * on the phone and answers with 33 points; the eight a push-up is judged by
 * are kept and the frame is dropped. Nothing here writes a file, holds a
 * bitmap past the next frame, or has a network call to make: the model is
 * an asset in the APK, and the whole path from lens to [PushUpPose] is in
 * this file.
 *
 * LIVE_STREAM mode, so a slow phone drops frames rather than falling
 * behind: [PoseLandmarker.detectAsync] returns at once and the result
 * arrives on MediaPipe's own thread, from where it is handed to the main
 * thread. The counter downstream expects one thread and one clock.
 *
 * The lite model on the CPU. GPU is faster on the phones where it works
 * and a crash on the ones where it does not; the lite model on a CPU
 * keeps up with a push-up, which is not a fast movement.
 *
 * One worker thread owns the model from creation to close: loading it
 * takes long enough to be felt on the main thread, and MediaPipe is not
 * a thing to close from one thread while another is feeding it.
 */
class PoseTracker(
    context: Context,
    private val onPose: (PushUpPose?, Long) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val worker = Executors.newSingleThreadExecutor()
    private var lastTimestamp = 0L

    /** Touched on [worker] only. */
    private var landmarker: PoseLandmarker? = null

    @Volatile
    private var closed = false

    init {
        worker.execute {
            if (closed) return@execute
            landmarker = runCatching {
                PoseLandmarker.createFromOptions(
                    context,
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(MODEL_ASSET)
                                .setDelegate(Delegate.CPU)
                                .build(),
                        )
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumPoses(1)
                        .setMinPoseDetectionConfidence(0.5f)
                        .setMinPosePresenceConfidence(0.5f)
                        .setMinTrackingConfidence(0.5f)
                        .setResultListener(::deliver)
                        .setErrorListener { e -> fail("Pose detection failed.", e) }
                        .build(),
                )
            }.getOrElse { e ->
                // The screen says what the library said: a message with a
                // cause in it is the difference between a fix and a guess.
                // Shown, not sent: whether this failure should reach
                // Crashlytics is a "what leaves the phone" decision
                // (AGENTS.md) that has not been put to the founder.
                fail("Pose detection could not start on this phone. ${e.message.orEmpty().take(160)}", e)
                null
            }
        }
    }

    /** Where CameraX must deliver frames: the thread the model lives on. */
    val executor: Executor get() = worker

    fun analyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { proxy -> analyse(proxy) }

    private fun analyse(proxy: ImageProxy) {
        proxy.use { frame ->
            val detector = landmarker
            if (closed || detector == null) return
            // RGBA_8888 output, one plane, so the buffer is the bitmap --
            // sized by the row stride rather than the width, because some
            // cameras pad each row, and a bitmap the width of the picture
            // filled from a padded buffer shears every row along by a bit.
            val plane = frame.planes[0]
            val raw = Bitmap.createBitmap(plane.rowStride / 4, frame.height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(plane.buffer)
            // Cropped to the picture and turned upright, as the model was
            // trained; the sensor delivers the frame in its own orientation.
            val matrix = Matrix().apply { postRotate(frame.imageInfo.rotationDegrees.toFloat()) }
            val upright = Bitmap.createBitmap(raw, 0, 0, frame.width, frame.height, matrix, true)
            // MediaPipe insists each timestamp is later than the last.
            val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1)
            lastTimestamp = timestamp
            runCatching { detector.detectAsync(BitmapImageBuilder(upright).build(), timestamp) }
                .onFailure { e -> fail("Pose detection failed.", e) }
        }
    }

    private fun deliver(result: PoseLandmarkerResult, input: MPImage) {
        if (closed) return
        val points = result.landmarks().firstOrNull()?.map {
            Landmark(x = it.x(), y = it.y(), visibility = it.visibility().orElse(0f))
        }
        val pose = points?.let {
            PushUpPose.fromNormalised(it, input.width.toFloat() / input.height.coerceAtLeast(1))
        }
        val at = result.timestampMs()
        mainExecutor.execute { if (!closed) onPose(pose, at) }
    }

    private fun fail(message: String, cause: Throwable) {
        Log.w(TAG, message, cause)
        mainExecutor.execute { if (!closed) onError(message) }
    }

    fun close() {
        closed = true
        worker.execute {
            runCatching { landmarker?.close() }
            landmarker = null
        }
        worker.shutdown()
    }

    private companion object {
        const val TAG = "PoseTracker"
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}

/**
 * The live camera view for the push-up screen.
 *
 * The front camera, because the phone is propped up facing the person and
 * that is the side the screen is on: they see their count. Back camera if
 * there is no front one. Bound to the composition's lifecycle, so leaving
 * the screen -- back, home, the reward screen replacing it -- releases the
 * camera and the model in the same breath; there is no way to keep the
 * lens open from anywhere else.
 */
@Composable
fun PushUpCameraView(
    onPose: (PushUpPose?, Long) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnPose by rememberUpdatedState(onPose)
    val latestOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val tracker = PoseTracker(
            context,
            onPose = { pose, at -> latestOnPose(pose, at) },
            onError = { latestOnError(it) },
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        // Set on the main thread by onDispose, read on the main thread by
        // the listener: a provider that arrives after the screen has gone
        // must not bind a camera nothing will ever unbind.
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            val bound = runCatching { providerFuture.get() }.getOrNull()
            if (bound == null) {
                latestOnError("The camera could not be opened.")
                return@addListener
            }
            provider = bound
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(tracker.executor, tracker.analyzer()) }
            bound.unbindAll()
            val opened = listOf(CameraSelector.DEFAULT_FRONT_CAMERA, CameraSelector.DEFAULT_BACK_CAMERA)
                .any { selector ->
                    runCatching { bound.bindToLifecycle(lifecycleOwner, selector, preview, analysis) }.isSuccess
                }
            if (!opened) latestOnError("The camera could not be opened.")
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            tracker.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
