import { beforeAll, describe, expect, test, vi } from "vitest";
import { buildJwtClaims, createGoogleAuth, importPrivateKey, signJwt } from "../src/googleAuth";
import { b64urlDecode, generateRsaKeyPair, pkcs8Pem, verifyJwtForTest } from "./support/jwt";

const EMAIL = "fixit-push@fixit-test.iam.gserviceaccount.com";
const T0 = Date.parse("2026-10-08T12:00:00.000Z");

let pair: CryptoKeyPair;
let pem: string;

beforeAll(async () => {
  pair = await generateRsaKeyPair();
  pem = await pkcs8Pem(pair);
});

function decodePart(jwt: string, index: number): Record<string, unknown> {
  return JSON.parse(new TextDecoder().decode(b64urlDecode(jwt.split(".")[index] ?? "")));
}

describe("buildJwtClaims", () => {
  test("has iss, the FCM scope, the token aud and exp = iat + 3600", () => {
    expect(buildJwtClaims(EMAIL, 1_800_000_000)).toEqual({
      iss: EMAIL,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: 1_800_000_000,
      exp: 1_800_003_600,
    });
  });
});

describe("signJwt", () => {
  test("RS256 with a PKCS#8 key, verifiable with the public key", async () => {
    const key = await importPrivateKey(pem);
    const claims = buildJwtClaims(EMAIL, 1_800_000_000);
    const jwt = await signJwt(claims, key);
    expect(decodePart(jwt, 0)).toEqual({ alg: "RS256", typ: "JWT" });
    expect(decodePart(jwt, 1)).toEqual(claims);
    await expect(verifyJwtForTest(pair.publicKey, jwt)).resolves.toBe(true);
    const other = await generateRsaKeyPair();
    await expect(verifyJwtForTest(other.publicKey, jwt)).resolves.toBe(false);
  });

  test("accepts a PEM whose newlines arrive escaped (as in a pasted JSON secret)", async () => {
    const key = await importPrivateKey(pem.replace(/\n/g, "\\n"));
    const jwt = await signJwt(buildJwtClaims(EMAIL, 1), key);
    await expect(verifyJwtForTest(pair.publicKey, jwt)).resolves.toBe(true);
  });
});

describe("getAccessToken", () => {
  function setup(responses: Response[]) {
    let now = T0;
    let call = 0;
    const fetchFn = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => {
      const r = responses[Math.min(call, responses.length - 1)];
      call++;
      if (!r) throw new Error("no response configured");
      return r.clone();
    });
    const auth = createGoogleAuth({ fetch: fetchFn as typeof fetch, now: () => now });
    return {
      fetchFn,
      get: () => auth.getAccessToken({ client_email: EMAIL, private_key: pem }),
      advance: (ms: number) => {
        now += ms;
      },
    };
  }

  test("POSTs a signed jwt-bearer assertion to the token endpoint", async () => {
    const s = setup([Response.json({ access_token: "tok1", expires_in: 3599, token_type: "Bearer" })]);
    await expect(s.get()).resolves.toBe("tok1");
    expect(s.fetchFn).toHaveBeenCalledTimes(1);
    const [url, init] = s.fetchFn.mock.calls[0] ?? [];
    expect(url).toBe("https://oauth2.googleapis.com/token");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("Content-Type")).toBe("application/x-www-form-urlencoded");
    const form = new URLSearchParams(String(init?.body));
    expect(form.get("grant_type")).toBe("urn:ietf:params:oauth:grant-type:jwt-bearer");
    const assertion = form.get("assertion") ?? "";
    await expect(verifyJwtForTest(pair.publicKey, assertion)).resolves.toBe(true);
    expect(decodePart(assertion, 1)).toEqual(buildJwtClaims(EMAIL, Math.floor(T0 / 1000)));
  });

  test("uses the cache until expires_in - 60 s (one call), renews after", async () => {
    const s = setup([
      Response.json({ access_token: "tok1", expires_in: 3600 }),
      Response.json({ access_token: "tok2", expires_in: 3600 }),
    ]);
    await expect(s.get()).resolves.toBe("tok1");
    s.advance((3600 - 60) * 1000 - 1);
    await expect(s.get()).resolves.toBe("tok1");
    expect(s.fetchFn).toHaveBeenCalledTimes(1);
    s.advance(1);
    await expect(s.get()).resolves.toBe("tok2");
    expect(s.fetchFn).toHaveBeenCalledTimes(2);
  });

  test("a non-2xx token response or a body without access_token throws", async () => {
    await expect(setup([new Response("denied", { status: 400 })]).get()).rejects.toThrow();
    await expect(setup([Response.json({ expires_in: 3600 })]).get()).rejects.toThrow();
  });

  test("a failed renewal is not cached: the next call tries again", async () => {
    const s = setup([new Response("busy", { status: 503 }), Response.json({ access_token: "tok1", expires_in: 3600 })]);
    await expect(s.get()).rejects.toThrow();
    await expect(s.get()).resolves.toBe("tok1");
    expect(s.fetchFn).toHaveBeenCalledTimes(2);
  });
});
