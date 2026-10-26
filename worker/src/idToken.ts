import { base64UrlDecode } from "./base64url";
import { checkClaims } from "./claims";

export const GOOGLE_JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

/**
 * Minimum gap between downloads triggered by an unknown `kid`: Google rotates keys,
 * so a valid cache may miss the new one, but a caller must not be able to force a
 * download per request. Accepted limitation: a token signed with a key rotated
 * less than 60 s after the last download gets 401 (D18).
 */
export const KID_REFETCH_MIN_INTERVAL_MS = 60_000;

const RS256 = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" } as const;

export interface Jwks {
  keys: JsonWebKey[];
  maxAgeSec: number;
}

export interface IdTokenVerifier {
  /** uid of a valid Firebase ID token, or null. Throws only if the JWKS cannot be downloaded. */
  verify(jwt: string, projectId: string): Promise<string | null>;
}

/** `max-age` seconds from Cache-Control; 0 (refetch next time) when absent or malformed. */
export function parseMaxAge(cacheControl: string | null): number {
  const match = cacheControl?.match(/(?:^|[,\s])max-age=(\d+)(?:$|[,\s])/i);
  return match?.[1] ? Number(match[1]) : 0;
}

export function fetchGoogleJwks(fetchFn: typeof fetch): () => Promise<Jwks> {
  return async () => {
    const res = await fetchFn(GOOGLE_JWKS_URL);
    if (!res.ok) throw new Error(`JWKS download failed: ${res.status}`);
    const body = (await res.json()) as { keys?: unknown };
    if (!Array.isArray(body.keys)) throw new Error("JWKS without keys");
    return { keys: body.keys as JsonWebKey[], maxAgeSec: parseMaxAge(res.headers.get("Cache-Control")) };
  };
}

interface KeyCache {
  jwks: Map<string, JsonWebKey>;
  fetchedAt: number;
  expiresAt: number;
}

/**
 * Holds the JWKS and the imported CryptoKeys for the isolate's lifetime (CPU plan B
 * step (a)): the warm path is a single native RS256 verify.
 */
export function createIdTokenVerifier(deps: { getJwks: () => Promise<Jwks>; now: () => number }): IdTokenVerifier {
  let cache: KeyCache | null = null;
  let inflight: Promise<KeyCache> | null = null;
  const imported = new Map<string, CryptoKey>();

  async function refresh(): Promise<KeyCache> {
    // Concurrent requests in the same isolate share one download.
    inflight ??= (async () => {
      try {
        const { keys, maxAgeSec } = await deps.getJwks();
        const fetchedAt = deps.now();
        const jwks = new Map<string, JsonWebKey>();
        for (const key of keys) {
          const kid = (key as { kid?: unknown }).kid;
          if (typeof kid === "string") jwks.set(kid, key);
        }
        imported.clear();
        cache = { jwks, fetchedAt, expiresAt: fetchedAt + maxAgeSec * 1000 };
        return cache;
      } finally {
        inflight = null;
      }
    })();
    return inflight;
  }

  async function keyFor(kid: string): Promise<CryptoKey | null> {
    const now = deps.now();
    let current = cache;
    if (current === null || now >= current.expiresAt) {
      current = await refresh();
    } else if (!current.jwks.has(kid) && now - current.fetchedAt >= KID_REFETCH_MIN_INTERVAL_MS) {
      current = await refresh();
    }
    const jwk = current.jwks.get(kid);
    if (!jwk || jwk.kty !== "RSA" || !jwk.n || !jwk.e) return null;
    let key = imported.get(kid);
    if (!key) {
      key = await crypto.subtle.importKey("jwk", { kty: "RSA", n: jwk.n, e: jwk.e, ext: true }, RS256, false, [
        "verify",
      ]);
      imported.set(kid, key);
    }
    return key;
  }

  return {
    async verify(jwt, projectId) {
      const parts = jwt.split(".");
      if (parts.length !== 3) return null;
      const [h, p, s] = parts as [string, string, string];
      let header: { alg?: unknown; kid?: unknown };
      let payload: unknown;
      let signature: Uint8Array;
      try {
        header = JSON.parse(new TextDecoder().decode(base64UrlDecode(h)));
        payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(p)));
        signature = base64UrlDecode(s);
      } catch {
        return null;
      }
      // Checked before any key lookup so a forged header cannot trigger a download.
      if (typeof header !== "object" || header === null) return null;
      if (header.alg !== "RS256" || typeof header.kid !== "string" || header.kid.length === 0) return null;

      const key = await keyFor(header.kid);
      if (!key) return null;
      const ok = await crypto.subtle.verify(RS256, key, signature, new TextEncoder().encode(`${h}.${p}`));
      if (!ok) return null;
      return checkClaims(payload, projectId, Math.floor(deps.now() / 1000));
    },
  };
}
