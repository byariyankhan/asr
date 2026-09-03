import { describe, expect, it } from "vitest";
import { ImageRejected, isJpeg, readJpegInfo, stripJpegMetadata } from "./jpeg";

/**
 * These build JPEGs byte by byte rather than reading a fixture. The point of
 * the module is that it walks the segment structure correctly, and a
 * hand-built file is the only way to be sure the test contains the exact
 * segment being asserted about - including an APP1 that really does hold
 * GPS-shaped bytes.
 */

function segment(marker: number, payload: Buffer): Buffer {
  const header = Buffer.alloc(4);
  header.writeUInt8(0xff, 0);
  header.writeUInt8(marker, 1);
  header.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([header, payload]);
}

/** SOFn payload: precision, height, width, component count. */
function sof(width: number, height: number): Buffer {
  const p = Buffer.alloc(6);
  p.writeUInt8(8, 0);
  p.writeUInt16BE(height, 1);
  p.writeUInt16BE(width, 3);
  p.writeUInt8(3, 5);
  return p;
}

const SOI = Buffer.from([0xff, 0xd8]);
const EOI = Buffer.from([0xff, 0xd9]);
const jfif = segment(0xe0, Buffer.from("JFIF-density", "latin1"));
const exifWithGps = segment(
  0xe1,
  Buffer.concat([
    Buffer.from("Exif", "latin1"),
    Buffer.from("GPSLatitude 23.8103 GPSLongitude 90.4125", "latin1"),
  ]),
);
const comment = segment(0xfe, Buffer.from("Taken on my phone", "latin1"));
const scan = Buffer.concat([
  segment(0xda, Buffer.from([0x01, 0x01, 0x00])),
  Buffer.from([0x12, 0x34, 0x56]),
]);

function jpeg(...parts: Buffer[]): Buffer {
  return Buffer.concat([SOI, ...parts, EOI]);
}

describe("isJpeg", () => {
  it("recognises the signature and nothing else", () => {
    expect(isJpeg(jpeg(jfif, segment(0xc0, sof(10, 10)), scan))).toBe(true);
    // A PNG, which the Android client re-encodes rather than sending.
    expect(isJpeg(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))).toBe(false);
    expect(isJpeg(Buffer.from("<html>", "latin1"))).toBe(false);
    expect(isJpeg(Buffer.alloc(0))).toBe(false);
  });
});

describe("readJpegInfo", () => {
  it("reads the dimensions from the frame header", () => {
    const bytes = jpeg(jfif, exifWithGps, segment(0xc0, sof(512, 384)), scan);
    expect(readJpegInfo(bytes)).toEqual({ width: 512, height: 384 });
  });

  it("is not fooled by frame-marker bytes inside EXIF", () => {
    // 0xffc0 appearing in metadata is exactly what a naive byte search finds
    // first, and it would report a thumbnail's size as the image's.
    const decoy = segment(
      0xe1,
      Buffer.from([0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x40, 0x00, 0x40, 0x03]),
    );
    const bytes = jpeg(jfif, decoy, segment(0xc2, sof(1024, 768)), scan);
    expect(readJpegInfo(bytes)).toEqual({ width: 1024, height: 768 });
  });

  it("refuses a file with no frame header", () => {
    expect(() => readJpegInfo(jpeg(jfif))).toThrow(ImageRejected);
  });

  it("refuses a truncated segment rather than reading past the end", () => {
    const truncated = Buffer.concat([SOI, Buffer.from([0xff, 0xe1, 0x40, 0x00, 0x01])]);
    expect(() => readJpegInfo(truncated)).toThrow(ImageRejected);
  });
});

describe("stripJpegMetadata", () => {
  const original = jpeg(jfif, exifWithGps, comment, segment(0xc0, sof(512, 384)), scan);

  it("removes the EXIF segment, GPS and all", () => {
    const stripped = stripJpegMetadata(original);
    expect(original.includes("GPSLatitude")).toBe(true);
    expect(stripped.includes("GPSLatitude")).toBe(false);
    expect(stripped.includes("Exif")).toBe(false);
  });

  it("removes comments, which carry whatever a camera app wrote there", () => {
    expect(stripJpegMetadata(original).includes("Taken on my phone")).toBe(false);
  });

  it("keeps JFIF, the frame header and the scan, so the result is still an image", () => {
    const stripped = stripJpegMetadata(original);
    expect(isJpeg(stripped)).toBe(true);
    expect(stripped.includes("JFIF-density")).toBe(true);
    expect(readJpegInfo(stripped)).toEqual({ width: 512, height: 384 });
    // The compressed data survives byte for byte.
    expect(stripped.includes(Buffer.from([0x12, 0x34, 0x56]))).toBe(true);
    expect(stripped.subarray(-2)).toEqual(EOI);
  });

  it("is smaller than what went in, and stable when run twice", () => {
    const once = stripJpegMetadata(original);
    expect(once.length).toBeLessThan(original.length);
    expect(stripJpegMetadata(once)).toEqual(once);
  });

  it("leaves an image that never had metadata untouched", () => {
    const clean = jpeg(jfif, segment(0xc0, sof(64, 64)), scan);
    expect(stripJpegMetadata(clean)).toEqual(clean);
  });

  it("refuses anything that is not a JPEG", () => {
    expect(() => stripJpegMetadata(Buffer.from("<html>not an image</html>", "latin1"))).toThrow(
      ImageRejected,
    );
  });
});
