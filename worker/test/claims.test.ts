import { describe, expect, test } from "vitest";
import { checkClaims } from "../src/claims";

const PROJECT = "fixit-test";
const NOW = 1_800_000_000;
const valid = {
  aud: PROJECT,
  iss: `https://securetoken.google.com/${PROJECT}`,
  sub: "uid123",
  iat: NOW - 10,
  exp: NOW + 3_000,
};

describe("checkClaims", () => {
  test("a valid token yields its uid (sub)", () => {
    expect(checkClaims(valid, PROJECT, NOW)).toBe("uid123");
  });

  test("foreign aud is rejected", () => {
    expect(checkClaims({ ...valid, aud: "other-project" }, PROJECT, NOW)).toBeNull();
  });

  test("foreign iss is rejected", () => {
    expect(checkClaims({ ...valid, iss: "https://securetoken.google.com/other-project" }, PROJECT, NOW)).toBeNull();
    expect(checkClaims({ ...valid, iss: "https://accounts.google.com" }, PROJECT, NOW)).toBeNull();
  });

  test("exp <= now is rejected", () => {
    expect(checkClaims({ ...valid, exp: NOW }, PROJECT, NOW)).toBeNull();
    expect(checkClaims({ ...valid, exp: NOW - 1 }, PROJECT, NOW)).toBeNull();
    expect(checkClaims({ ...valid, exp: NOW + 1 }, PROJECT, NOW)).toBe("uid123");
  });

  test("iat > now + 60 is rejected; up to 60 s ahead is tolerated", () => {
    expect(checkClaims({ ...valid, iat: NOW + 61 }, PROJECT, NOW)).toBeNull();
    expect(checkClaims({ ...valid, iat: NOW + 60 }, PROJECT, NOW)).toBe("uid123");
  });

  test("empty or missing sub is rejected", () => {
    expect(checkClaims({ ...valid, sub: "" }, PROJECT, NOW)).toBeNull();
    const { sub: _sub, ...noSub } = valid;
    expect(checkClaims(noSub, PROJECT, NOW)).toBeNull();
  });

  test("missing or mistyped claims are rejected", () => {
    expect(checkClaims({ ...valid, exp: String(NOW + 3_000) }, PROJECT, NOW)).toBeNull();
    expect(checkClaims({ ...valid, iat: undefined }, PROJECT, NOW)).toBeNull();
    expect(checkClaims(null, PROJECT, NOW)).toBeNull();
    expect(checkClaims("payload", PROJECT, NOW)).toBeNull();
  });
});
