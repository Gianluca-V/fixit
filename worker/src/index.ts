import { parseNotifyBody } from "./body";
import { buildFcmMessage, sendFcm, type FcmMessage } from "./fcm";
import { fetchRequest, type FetchRequestResult } from "./firestore";
import { createGoogleAuth, type ServiceAccount } from "./googleAuth";
import { createIdTokenVerifier, fetchGoogleJwks } from "./idToken";
import { decideTarget } from "./rules";

export interface Env {
  /** Public `[vars]` value in wrangler.toml. */
  FIREBASE_PROJECT_ID: string;
  /** Full service account JSON; secret (`wrangler secret put`, `.dev.vars` locally). */
  GOOGLE_SERVICE_ACCOUNT: string;
}

/** Every effect is injected so the handler is tested in plain Node (D23). */
export interface NotifyDeps {
  verifyIdToken(jwt: string, projectId: string): Promise<string | null>;
  fetchRequest(projectId: string, requestId: string, idToken: string): Promise<FetchRequestResult>;
  getAccessToken(sa: ServiceAccount): Promise<string>;
  sendFcm(projectId: string, accessToken: string, message: FcmMessage): Promise<void>;
  now(): number;
}

// Empty bodies: the app ignores the outcome (best effort) and a reason would
// only help a prober.
const status = (code: number, headers?: HeadersInit): Response =>
  new Response(null, headers ? { status: code, headers } : { status: code });

function bearer(request: Request): string | null {
  const match = /^Bearer (\S+)$/.exec(request.headers.get("Authorization") ?? "");
  return match?.[1] ?? null;
}

function parseServiceAccount(raw: string): ServiceAccount | null {
  try {
    const sa = JSON.parse(raw) as Partial<ServiceAccount> | null;
    if (typeof sa?.client_email !== "string" || typeof sa.private_key !== "string") return null;
    return { client_email: sa.client_email, private_key: sa.private_key };
  } catch {
    return null;
  }
}

/**
 * Check order (§14b.7): route 404 → method 405 → body 400 → token 401 → re-read
 * 404/502 → decision 403 → OAuth + FCM 502. The body is validated before the token
 * so a malformed call costs no RS256 verify. No CORS (only the app calls it) and
 * no rate limiting (D18).
 */
export async function handleNotify(request: Request, env: Env, deps: NotifyDeps): Promise<Response> {
  if (new URL(request.url).pathname !== "/notify") return status(404);
  if (request.method !== "POST") return status(405, { Allow: "POST" });

  let json: unknown;
  try {
    json = await request.json();
  } catch {
    return status(400);
  }
  const body = parseNotifyBody(json);
  if (!body) return status(400);

  const idToken = bearer(request);
  if (!idToken) return status(401);
  let uid: string | null;
  try {
    uid = await deps.verifyIdToken(idToken, env.FIREBASE_PROJECT_ID);
  } catch {
    return status(502); // Google's JWKS unavailable: not the caller's fault.
  }
  if (!uid) return status(401);

  const read = await deps.fetchRequest(env.FIREBASE_PROJECT_ID, body.requestId, idToken);
  if (!read.ok) return status(read.status);

  const decision = decideTarget(body.type, read.request, uid, deps.now());
  if ("reject" in decision) return status(decision.reject);

  const sa = parseServiceAccount(env.GOOGLE_SERVICE_ACCOUNT);
  if (!sa) return status(502);
  try {
    const accessToken = await deps.getAccessToken(sa);
    await deps.sendFcm(env.FIREBASE_PROJECT_ID, accessToken, buildFcmMessage(decision.topic, decision.data));
  } catch {
    return status(502);
  }
  return status(204);
}

// Bound wrapper: calling the runtime's fetch detached from globalThis can throw
// "Illegal invocation" in workerd.
const runtimeFetch: typeof fetch = (input, init) => fetch(input, init);

// Module scope = isolate lifetime: JWKS, imported keys and the OAuth token survive
// between requests served by the same isolate (CPU plan B step (a)).
const verifier = createIdTokenVerifier({ getJwks: fetchGoogleJwks(runtimeFetch), now: Date.now });
const googleAuth = createGoogleAuth({ fetch: runtimeFetch, now: Date.now });

const liveDeps: NotifyDeps = {
  verifyIdToken: (jwt, projectId) => verifier.verify(jwt, projectId),
  fetchRequest: (projectId, requestId, idToken) => fetchRequest(projectId, requestId, idToken, runtimeFetch),
  getAccessToken: (sa) => googleAuth.getAccessToken(sa),
  sendFcm: (projectId, accessToken, message) => sendFcm(projectId, accessToken, message, runtimeFetch),
  now: Date.now,
};

export default {
  fetch(request, env) {
    return handleNotify(request, env, liveDeps);
  },
} satisfies ExportedHandler<Env>;
