import { base64UrlEncode } from "./base64url";

export const TOKEN_URL = "https://oauth2.googleapis.com/token";
export const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
/** Renew this long before Google's expiry so a token never dies mid-request. */
const EXPIRY_MARGIN_MS = 60_000;

const RS256 = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" } as const;

/** The two fields used from the `GOOGLE_SERVICE_ACCOUNT` secret (full JSON key file). */
export interface ServiceAccount {
  client_email: string;
  private_key: string;
}

export interface JwtClaims {
  iss: string;
  scope: string;
  aud: string;
  iat: number;
  exp: number;
}

export interface GoogleAuth {
  getAccessToken(sa: ServiceAccount): Promise<string>;
}

export function buildJwtClaims(clientEmail: string, nowSec: number): JwtClaims {
  return { iss: clientEmail, scope: FCM_SCOPE, aud: TOKEN_URL, iat: nowSec, exp: nowSec + 3600 };
}

export async function importPrivateKey(pem: string): Promise<CryptoKey> {
  // A secret pasted by hand may keep the JSON-escaped "\n" sequences literally.
  const body = pem
    .replace(/\\n/g, "\n")
    .replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey("pkcs8", der, RS256, false, ["sign"]);
}

export async function signJwt(claims: JwtClaims, key: CryptoKey): Promise<string> {
  const input = `${base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }))}.${base64UrlEncode(JSON.stringify(claims))}`;
  const signature = await crypto.subtle.sign(RS256, key, new TextEncoder().encode(input));
  return `${input}.${base64UrlEncode(new Uint8Array(signature))}`;
}

/**
 * The PKCS#8 import (the costly cold-path step) and the OAuth token live in the
 * isolate's memory, so a warm isolate signs nothing (CPU plan B step (a), §14b.7).
 */
export function createGoogleAuth(deps: { fetch: typeof fetch; now: () => number }): GoogleAuth {
  let key: { pem: string; value: Promise<CryptoKey> } | null = null;
  let token: { email: string; value: string; renewAt: number } | null = null;

  function keyFor(pem: string): Promise<CryptoKey> {
    if (key?.pem !== pem) {
      const value = importPrivateKey(pem);
      key = { pem, value };
      // A bad key must not stay cached as a rejected promise.
      value.catch(() => {
        if (key?.value === value) key = null;
      });
    }
    return key.value;
  }

  return {
    async getAccessToken(sa) {
      const now = deps.now();
      if (token && token.email === sa.client_email && now < token.renewAt) return token.value;

      const assertion = await signJwt(buildJwtClaims(sa.client_email, Math.floor(now / 1000)), await keyFor(sa.private_key));
      const res = await deps.fetch(TOKEN_URL, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
      });
      if (!res.ok) throw new Error(`OAuth token request failed: ${res.status}`);
      const body = (await res.json()) as { access_token?: unknown; expires_in?: unknown };
      if (typeof body.access_token !== "string" || body.access_token.length === 0) {
        throw new Error("OAuth token response without access_token");
      }
      const expiresInSec = typeof body.expires_in === "number" ? body.expires_in : 0;
      token = { email: sa.client_email, value: body.access_token, renewAt: now + expiresInSec * 1000 - EXPIRY_MARGIN_MS };
      return token.value;
    },
  };
}
