/** Tolerated clock drift for `iat`, matching Firebase's own verification. */
const IAT_SKEW_SEC = 60;

/**
 * Firebase ID token claim rules (https://firebase.google.com/docs/auth/admin/verify-id-tokens).
 * Returns the uid (`sub`) or null; pure so the time rules are testable with a fixed clock.
 */
export function checkClaims(payload: unknown, projectId: string, nowSec: number): string | null {
  if (typeof payload !== "object" || payload === null) return null;
  const { aud, iss, sub, iat, exp } = payload as Record<string, unknown>;
  if (aud !== projectId) return null;
  if (iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (typeof exp !== "number" || exp <= nowSec) return null;
  if (typeof iat !== "number" || iat > nowSec + IAT_SKEW_SEC) return null;
  if (typeof sub !== "string" || sub.length === 0) return null;
  return sub;
}
