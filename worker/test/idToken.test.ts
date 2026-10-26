import { beforeAll, describe, expect, test, vi } from "vitest";
import { createIdTokenVerifier, fetchGoogleJwks, GOOGLE_JWKS_URL, parseMaxAge, type Jwks } from "../src/idToken";
import { b64url, generateRsaKeyPair, publicJwk, signJwtForTest } from "./support/jwt";

const PROJECT = "fixit-test";
const T0 = Date.parse("2026-10-08T12:00:00.000Z");

let pairA: CryptoKeyPair;
let pairB: CryptoKeyPair;
let jwkA: JsonWebKey & { kid: string };
let jwkB: JsonWebKey & { kid: string };

beforeAll(async () => {
  pairA = await generateRsaKeyPair();
  pairB = await generateRsaKeyPair();
  jwkA = await publicJwk(pairA, "kidA");
  jwkB = await publicJwk(pairB, "kidB");
});

function claims(nowMs: number, overrides: Record<string, unknown> = {}) {
  const now = Math.floor(nowMs / 1000);
  return {
    aud: PROJECT,
    iss: `https://securetoken.google.com/${PROJECT}`,
    sub: "uid123",
    iat: now - 5,
    exp: now + 3_000,
    ...overrides,
  };
}

function token(pair: CryptoKeyPair, kid: string | undefined, nowMs: number, header: Record<string, unknown> = {}) {
  return signJwtForTest(pair.privateKey, { alg: "RS256", typ: "JWT", ...(kid ? { kid } : {}), ...header }, claims(nowMs));
}

/** Clock and JWKS source both controlled by the test; responses are served in order. */
function setup(responses: Jwks[]) {
  let now = T0;
  let call = 0;
  const getJwks = vi.fn(async (): Promise<Jwks> => {
    const response = responses[Math.min(call, responses.length - 1)];
    call++;
    if (!response) throw new Error("no JWKS response configured");
    return response;
  });
  const verifier = createIdTokenVerifier({ getJwks, now: () => now });
  return {
    getJwks,
    verify: (jwt: string) => verifier.verify(jwt, PROJECT),
    advance: (ms: number) => {
      now += ms;
    },
    now: () => now,
  };
}

describe("parseMaxAge", () => {
  test("reads max-age from Cache-Control", () => {
    expect(parseMaxAge("public, max-age=19302, must-revalidate, no-transform")).toBe(19302);
    expect(parseMaxAge("max-age=60")).toBe(60);
  });

  test("missing or malformed header -> 0 (no caching beyond this request)", () => {
    expect(parseMaxAge(null)).toBe(0);
    expect(parseMaxAge("no-cache")).toBe(0);
    expect(parseMaxAge("max-age=abc")).toBe(0);
  });
});

describe("verifyIdToken", () => {
  test("accepts a well-signed RS256 JWT and returns the uid", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
    await expect(s.verify(await token(pairA, "kidA", s.now()))).resolves.toBe("uid123");
    expect(s.getJwks).toHaveBeenCalledTimes(1);
  });

  test("rejects an altered signature or payload", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
    const jwt = await token(pairA, "kidA", s.now());
    const [h, p, sig] = jwt.split(".");
    const forged = await signJwtForTest(pairB.privateKey, { alg: "RS256", kid: "kidA" }, claims(s.now()));
    await expect(s.verify(`${h}.${p}.${forged.split(".")[2]}`)).resolves.toBeNull();
    const tamperedPayload = b64url(JSON.stringify(claims(s.now(), { sub: "victim" })));
    await expect(s.verify(`${h}.${tamperedPayload}.${sig}`)).resolves.toBeNull();
  });

  test("rejects alg other than RS256 without fetching keys", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
    await expect(s.verify(await token(pairA, "kidA", s.now(), { alg: "HS256" }))).resolves.toBeNull();
    await expect(s.verify(await token(pairA, "kidA", s.now(), { alg: "none" }))).resolves.toBeNull();
    expect(s.getJwks).not.toHaveBeenCalled();
  });

  test("rejects a missing kid", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
    await expect(s.verify(await token(pairA, undefined, s.now()))).resolves.toBeNull();
  });

  test.each(["", "abc", "a.b", "a.b.c.d", "!!.@@.##", `${b64url("{")}.${b64url("{}")}.sig`])(
    "rejects malformed token %j",
    async (jwt) => {
      const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
      await expect(s.verify(jwt)).resolves.toBeNull();
    },
  );

  test("rejects a well-signed token whose claims fail", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
    const jwt = await signJwtForTest(pairA.privateKey, { alg: "RS256", kid: "kidA" }, claims(s.now(), { aud: "other" }));
    await expect(s.verify(jwt)).resolves.toBeNull();
  });

  test("key cache: no refetch before max-age expires, refetch after", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 100 }]);
    await expect(s.verify(await token(pairA, "kidA", s.now()))).resolves.toBe("uid123");
    s.advance(99_000);
    await expect(s.verify(await token(pairA, "kidA", s.now()))).resolves.toBe("uid123");
    expect(s.getJwks).toHaveBeenCalledTimes(1);
    s.advance(2_000);
    await expect(s.verify(await token(pairA, "kidA", s.now()))).resolves.toBe("uid123");
    expect(s.getJwks).toHaveBeenCalledTimes(2);
  });

  test("unknown kid with a valid cache: one refetch after >= 60 s, accepted if the rotated key appears", async () => {
    const s = setup([
      { keys: [jwkA], maxAgeSec: 3600 },
      { keys: [jwkA, jwkB], maxAgeSec: 3600 },
    ]);
    await expect(s.verify(await token(pairA, "kidA", s.now()))).resolves.toBe("uid123");
    s.advance(60_000);
    await expect(s.verify(await token(pairB, "kidB", s.now()))).resolves.toBe("uid123");
    expect(s.getJwks).toHaveBeenCalledTimes(2);
  });

  test("unknown kid still unknown after the refetch -> rejected", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
    await s.verify(await token(pairA, "kidA", s.now()));
    s.advance(61_000);
    await expect(s.verify(await token(pairB, "kidB", s.now()))).resolves.toBeNull();
    expect(s.getJwks).toHaveBeenCalledTimes(2);
  });

  test("a second unknown kid within 60 s of the last download -> rejected without another download", async () => {
    const s = setup([
      { keys: [jwkA], maxAgeSec: 3600 },
      { keys: [jwkA], maxAgeSec: 3600 },
    ]);
    await s.verify(await token(pairA, "kidA", s.now()));
    s.advance(61_000);
    await expect(s.verify(await token(pairB, "kidB", s.now()))).resolves.toBeNull();
    expect(s.getJwks).toHaveBeenCalledTimes(2);
    s.advance(59_000);
    await expect(s.verify(await token(pairB, "kidX", s.now()))).resolves.toBeNull();
    await expect(s.verify(await token(pairB, "kidB", s.now()))).resolves.toBeNull();
    expect(s.getJwks).toHaveBeenCalledTimes(2);
  });

  test("an unknown kid right after the first download does not trigger a download", async () => {
    const s = setup([{ keys: [jwkA], maxAgeSec: 3600 }]);
    await s.verify(await token(pairA, "kidA", s.now()));
    s.advance(10_000);
    await expect(s.verify(await token(pairB, "kidB", s.now()))).resolves.toBeNull();
    expect(s.getJwks).toHaveBeenCalledTimes(1);
  });
});

describe("fetchGoogleJwks", () => {
  test("downloads the securetoken JWKS and reads max-age from Cache-Control", async () => {
    const fetchFn = vi.fn(async (_input: RequestInfo | URL) =>
      Response.json({ keys: [jwkA] }, { headers: { "Cache-Control": "public, max-age=21600, must-revalidate" } }),
    );
    await expect(fetchGoogleJwks(fetchFn as typeof fetch)()).resolves.toEqual({ keys: [jwkA], maxAgeSec: 21600 });
    expect(fetchFn).toHaveBeenCalledWith(GOOGLE_JWKS_URL);
    expect(GOOGLE_JWKS_URL).toBe(
      "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com",
    );
  });

  test("a failed download throws (an infrastructure failure, not a token verdict)", async () => {
    const fetchFn = vi.fn(async () => new Response("oops", { status: 503 }));
    await expect(fetchGoogleJwks(fetchFn as unknown as typeof fetch)()).rejects.toThrow();
    const verifier = createIdTokenVerifier({ getJwks: fetchGoogleJwks(fetchFn as unknown as typeof fetch), now: () => T0 });
    await expect(verifier.verify(await token(pairA, "kidA", T0), PROJECT)).rejects.toThrow();
  });
});
