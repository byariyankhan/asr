package io.joinasr.app.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The auth layer against a real HTTP server that answers what the real one
 * answers. These run on the JVM, which matters: this project's CI is the only
 * place the app can be built at all, and a test needing a device would never
 * run.
 */
class AuthApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = AuthApi(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `sign up returns the token from the set-auth-token header`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("set-auth-token", "tok_abc123")
                .setBody("""{"user":{"id":"u1"}}"""),
        )

        val result = api.signUp("someone@example.com", "correct horse battery")

        assertEquals(ApiResult.Ok("tok_abc123"), result)
        val sent = server.takeRequest()
        assertEquals("/api/auth/sign-up/email", sent.path)
    }

    @Test
    fun `sign up sends a name because Better Auth requires one`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("set-auth-token", "t"))

        api.signUp("ariyan@example.com", "12345678")

        // The designed screen collects no name, so the local part stands in
        // until the About You screen sends the real one. If this assertion
        // ever fails, sign-up is silently 400ing on a required field.
        val body = server.takeRequest().body.readUtf8()
        assertTrue("name missing from $body", body.contains("\"name\":\"ariyan\""))
    }

    @Test
    fun `a 200 without a token is a failure, not a silent signed-in state`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"user":{"id":"u1"}}"""))

        val result = api.signIn("someone@example.com", "hunter2")

        val failure = result as ApiResult.Failure
        assertEquals("no_session_token", failure.error)
    }

    @Test
    fun `the server's own message is what the person is shown`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"code":"USER_ALREADY_EXISTS","message":"User already exists"}"""),
        )

        val result = api.signUp("taken@example.com", "12345678")

        val failure = result as ApiResult.Failure
        assertEquals(422, failure.code)
        assertEquals("USER_ALREADY_EXISTS", failure.error)
        assertEquals("User already exists", failure.message)
    }

    @Test
    fun `a body that is not json still produces a sentence`() = runTest {
        // What a proxy or a misconfigured nginx returns. Showing this raw in
        // a form is worse than a generic apology.
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("<html><head><title>502 Bad Gateway</title></head></html>"),
        )

        val result = api.signIn("someone@example.com", "hunter2")

        val failure = result as ApiResult.Failure
        assertEquals(502, failure.code)
        assertTrue("html leaked: ${failure.message}", !failure.message.contains("<"))
        assertTrue(failure.message.isNotBlank())
    }

    @Test
    fun `a rate limit carries its Retry-After`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "60")
                .setBody("""{"error":"rate_limited","message":"Slow down."}"""),
        )

        val result = api.signIn("someone@example.com", "hunter2")

        assertEquals(60, (result as ApiResult.Failure).retryAfterSeconds)
    }

    @Test
    fun `no server at all reads as offline, not as a refusal`() = runTest {
        val url = server.url("/").toString().trimEnd('/')
        server.shutdown() // Nothing is listening now.

        val result = AuthApi(OkHttpClient(), url).signIn("someone@example.com", "hunter2")

        assertTrue("expected Offline, got $result", result is ApiResult.Offline)
    }
}
