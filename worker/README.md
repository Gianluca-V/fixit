# FixIt push Worker

Cloudflare Worker (TypeScript, free plan) that sends the FixIt FCM push notices. The app calls it after creating, accepting or cancelling a request. The Worker checks the caller's Firebase ID token, re-reads the request from Firestore and sends a data-only message to an FCM topic. Design: `openspec/changes/fixit-mvp/design.md` §14b.7 (D23, D27).

## Requirements

- **Node.js v22** (tested with v22.19.0; `>=22.12.0` is required by vitest). The code and tests rely on the global WebCrypto (`crypto.subtle`), `fetch`, `Request` and `Response` that Node 22 provides.
- npm. Run `npm install` (or `npm ci`) inside `worker/`. `.npmrc` sets `legacy-peer-deps=true` because npm crashes ("reading 'edgesOut'") while resolving vitest 5's optional peer chain; `vite`, a required peer of vitest, is pinned explicitly in `devDependencies` for that reason.

## Tests

```sh
cd worker
npm test            # vitest run, plain Node, no Miniflare (D23)
npx tsc --noEmit    # strict type check
```

The tests only exercise pure functions or code with injected `fetch`, clock and JWKS. The RSA keys are generated inside the tests with `crypto.subtle.generateKey`. No real key or service account is ever stored in the repository. Real signatures, Firestore and FCM are checked by hand (tasks 4.7 and 4.13).

## Local run

```sh
npx wrangler dev    # or: npm run dev
```

`wrangler dev` reads secrets from `worker/.dev.vars`. That file is git-ignored and must never be committed:

```
GOOGLE_SERVICE_ACCOUNT={"type":"service_account","client_email":"...","private_key":"-----BEGIN PRIVATE KEY-----\n...","...":"..."}
```

## Configuration

| Name | Kind | Where |
|---|---|---|
| `FIREBASE_PROJECT_ID` | public var | `wrangler.toml` `[vars]` |
| `GOOGLE_SERVICE_ACCOUNT` | secret | the full JSON key of a **dedicated** service account with only the *Firebase Cloud Messaging API Admin* role (not the Admin SDK account). Set it with `wrangler secret put`; locally it lives in `.dev.vars`. |

The Worker reads Firestore with the caller's own ID token, so the service account needs no database role. The project also needs the "Firebase Cloud Messaging API" enabled, which needs no billing.

## Deploy (manual, tasks 4.7)

These steps are not automated. Run them yourself with your own accounts:

1. Create a free Cloudflare account (no card). In the Firebase project, enable "Firebase Cloud Messaging API". Create the dedicated service account and download its JSON **outside** the repository.
2. `npx wrangler login`
3. `npx wrangler secret put GOOGLE_SERVICE_ACCOUNT` and paste the JSON.
4. `npx wrangler deploy`. Note the `https://<name>.<account>.workers.dev` URL. It goes into the app as `PUSH_WORKER_URL`.
5. Check with `git status` and a search for `private_key` that no key file got into the repository.
6. Measure the CPU time of a **cold** request (a new isolate) in the Workers dashboard. If it exceeds 10 ms, apply plan B of §14b.7 in order.

## `/notify` contract

```
POST /notify
Authorization: Bearer <Firebase ID token>
Content-Type: application/json
{ "type": "NEW_REQUEST" | "REQUEST_ACCEPTED" | "REQUEST_CANCELLED", "requestId": "<[A-Za-z0-9]{1,40}>" }
```

Any other body field is ignored. The topic and the category always come from the Firestore document.

| Status | Meaning |
|---|---|
| 204 | FCM accepted the message (delivery is not awaited) |
| 400 | invalid body |
| 401 | missing or invalid ID token |
| 403 | the event checks failed: state, caller, or freshness (server mark at most 2 min old and at most 60 s in the future) |
| 404 | unknown route, or the request does not exist |
| 405 | method other than `POST` (`Allow: POST`) |
| 502 | Firestore, Google keys, OAuth or FCM failure, or a malformed secret |

The checks run in this order: route → method → body → token → Firestore re-read → decision → OAuth + FCM. Every response has an empty body.

| Event | Firestore must show | Caller (`uid`) | Topic | FCM `data` | Android delivery |
|---|---|---|---|---|---|
| `NEW_REQUEST` | `OPEN`, fresh `createdAt` | `clientId` | `category_{CATEGORY}` | `{ type, requestId, category }` | `NORMAL`, `3600s` |
| `REQUEST_ACCEPTED` | `ACCEPTED`, fresh `acceptedAt` | `professionalId` | `user_{clientId}` | `{ type, requestId }` | `HIGH`, `86400s` |
| `REQUEST_CANCELLED` | `CANCELLED` with `professionalId`, fresh `cancelledAt` | `clientId` | `user_{professionalId}` | `{ type, requestId }` | `HIGH`, `86400s` |

Messages are data-only. They have no `notification` block and carry no personal data or coordinates, because topics are not private (D18).

## Layout

| File | Role |
|---|---|
| `src/index.ts` | `handleNotify` (check order, injected deps) and the Worker entry with isolate-level caches |
| `src/body.ts` | `parseNotifyBody` |
| `src/idToken.ts` / `src/claims.ts` | RS256 verification with the cached securetoken JWKS; claim rules |
| `src/firestore.ts` | `fetchRequest`, RFC 3339 timestamps truncated to ms |
| `src/rules.ts` | `decideTarget`: state, caller and event freshness |
| `src/googleAuth.ts` | service-account JWT and cached OAuth token |
| `src/fcm.ts` | `buildFcmMessage` / `sendFcm` (FCM HTTP v1) |
