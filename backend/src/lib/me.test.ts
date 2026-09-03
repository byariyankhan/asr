import { describe, expect, it } from "vitest";
import { meUpdate } from "./schemas";

describe("meUpdate", () => {
  it("rejects an empty body", () => {
    expect(() => meUpdate.parse({})).toThrow(/nothing to update/);
  });

  it("accepts a real date of birth for someone 13 or older", () => {
    expect(meUpdate.parse({ date_of_birth: "2000-02-29" }).date_of_birth).toBe("2000-02-29");
  });

  it("rejects impossible dates and under-13s", () => {
    expect(() => meUpdate.parse({ date_of_birth: "2001-02-29" })).toThrow();
    expect(() => meUpdate.parse({ date_of_birth: "2000-13-01" })).toThrow();
    const thisYear = new Date().getUTCFullYear();
    expect(() => meUpdate.parse({ date_of_birth: `${thisYear - 5}-01-01` })).toThrow(/13/);
    expect(() => meUpdate.parse({ date_of_birth: `${thisYear - 130}-01-01` })).toThrow();
  });

  it("validates country codes against the ISO list", () => {
    expect(meUpdate.parse({ country: "BD" }).country).toBe("BD");
    expect(() => meUpdate.parse({ country: "bd" })).toThrow();
    expect(() => meUpdate.parse({ country: "ZZ" })).toThrow(/unknown country/);
    expect(() => meUpdate.parse({ country: "BGD" })).toThrow();
  });

  it("allows clearing optional profile fields with null", () => {
    expect(meUpdate.parse({ date_of_birth: null, country: null, gender: null })).toEqual({
      date_of_birth: null,
      country: null,
      gender: null,
    });
  });

  it("restricts gender to the known values", () => {
    expect(meUpdate.parse({ gender: "prefer_not_to_say" }).gender).toBe("prefer_not_to_say");
    expect(() => meUpdate.parse({ gender: "yes" })).toThrow();
  });
});
