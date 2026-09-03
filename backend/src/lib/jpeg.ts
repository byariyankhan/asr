/**
 * JPEG validation and metadata removal, without an image library.
 *
 * `sharp` would do this and more, but it ships a native binary per platform,
 * and this project has already lost two deploys to native/ESM files going
 * missing from a Next standalone build. Everything here is byte manipulation
 * on a Buffer: no dependency, no decode, and testable in the same vitest run
 * as everything else.
 *
 * The wire format for an avatar is JPEG and nothing else. That is not a
 * limitation on what a person can choose — the Android client decodes
 * whatever the picker returns and re-encodes it small — it is the server
 * refusing to accept formats it cannot inspect properly.
 */

export class ImageRejected extends Error {
  constructor(readonly reason: string, message: string) {
    super(message);
  }
}

const SOI = 0xd8; // start of image
const EOI = 0xd9;
const SOS = 0xda; // start of scan: entropy-coded data follows, not segments
const APP0 = 0xe0;
const APP15 = 0xef;
const COM = 0xfe;

/** Markers that carry no payload length, so they are two bytes and no more. */
const STANDALONE = new Set([0x01, SOI, EOI, 0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7]);

/** Start-of-frame markers, which is where the dimensions live. */
const SOF = new Set([
  0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf,
]);

export function isJpeg(bytes: Buffer): boolean {
  return bytes.length > 3 && bytes.readUInt8(0) === 0xff && bytes.readUInt8(1) === SOI;
}

export type JpegInfo = { width: number; height: number };

/**
 * Walks the segment structure to the first frame header. Deliberately not a
 * search for the SOF bytes: those two bytes occur inside EXIF thumbnails and
 * inside compressed data all the time, and a scan would happily report the
 * dimensions of a thumbnail as the dimensions of the image.
 */
export function readJpegInfo(bytes: Buffer): JpegInfo {
  if (!isJpeg(bytes)) throw new ImageRejected("not_jpeg", "That file is not a JPEG.");
  let i = 2;
  while (i + 3 < bytes.length) {
    if (bytes.readUInt8(i) !== 0xff) throw new ImageRejected("corrupt", "That JPEG is malformed.");
    const marker = bytes.readUInt8(i + 1);
    if (marker === 0xff) {
      i += 1; // Fill byte; legal padding between segments.
      continue;
    }
    if (STANDALONE.has(marker)) {
      i += 2;
      continue;
    }
    const length = bytes.readUInt16BE(i + 2);
    if (length < 2 || i + 2 + length > bytes.length) {
      throw new ImageRejected("corrupt", "That JPEG is malformed.");
    }
    if (SOF.has(marker)) {
      // SOFn payload: precision(1) height(2) width(2) components(1) ...
      if (length < 7) throw new ImageRejected("corrupt", "That JPEG is malformed.");
      return {
        height: bytes.readUInt16BE(i + 5),
        width: bytes.readUInt16BE(i + 7),
      };
    }
    if (marker === SOS) break;
    i += 2 + length;
  }
  throw new ImageRejected("corrupt", "That JPEG has no image in it.");
}

/**
 * Returns the same image with every APP1-APP15 and comment segment removed.
 *
 * That is where EXIF lives, and EXIF on a phone photo routinely carries the
 * GPS coordinates of the place it was taken. An avatar is shown to the
 * witnesses a person invited; handing them the location of someone's bedroom
 * along with their face is not a trade any of them agreed to.
 *
 * APP0 (JFIF) is kept: it carries only pixel density, some decoders expect
 * it, and it cannot identify anybody. Note that stripping EXIF also removes
 * the Orientation tag, so the bytes must already be upright — the Android
 * client rotates before uploading, and this function does not decode and so
 * cannot rotate anything itself.
 */
export function stripJpegMetadata(bytes: Buffer): Buffer {
  if (!isJpeg(bytes)) throw new ImageRejected("not_jpeg", "That file is not a JPEG.");
  const out: Buffer[] = [bytes.subarray(0, 2)];
  let i = 2;
  while (i + 1 < bytes.length) {
    if (bytes.readUInt8(i) !== 0xff) throw new ImageRejected("corrupt", "That JPEG is malformed.");
    const marker = bytes.readUInt8(i + 1);
    if (marker === 0xff) {
      i += 1;
      continue;
    }
    if (STANDALONE.has(marker)) {
      out.push(bytes.subarray(i, i + 2));
      i += 2;
      continue;
    }
    if (i + 3 >= bytes.length) throw new ImageRejected("corrupt", "That JPEG is malformed.");
    const length = bytes.readUInt16BE(i + 2);
    if (length < 2 || i + 2 + length > bytes.length) {
      throw new ImageRejected("corrupt", "That JPEG is malformed.");
    }
    const dropped = (marker > APP0 && marker <= APP15) || marker === COM;
    if (marker === SOS) {
      // Everything from here to the end is compressed data plus EOI. It is
      // copied verbatim: there are no more segments to inspect, and a
      // "marker" found inside it would be a coincidence.
      out.push(bytes.subarray(i));
      break;
    }
    if (!dropped) out.push(bytes.subarray(i, i + 2 + length));
    i += 2 + length;
  }
  return Buffer.concat(out);
}
