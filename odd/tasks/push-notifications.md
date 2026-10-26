# Push notifications (fixit-mvp Phase 4)

Objective: implement FCM push notifications sent by a Cloudflare Worker, with the simplified topic sync. Branch: `feat/push-notifications`. Delivery: `ask-on-risk`. Test runners: `./gradlew testDebugUnitTest` (Android), `npm test` in `worker/` (vitest).

## Specs

- S1. "implementa las notificaciones con esa solucion" — the solution is the topic-sync simplification: "en cada inicio, desuscribirse de todo lo que no corresponde y suscribirse a lo que sí", because FCM topic operations are idempotent and there are only 8 categories.
- S2. Behavior, contracts and scenarios: `openspec/changes/fixit-mvp/specs/notifications/spec.md` (all requirements), design `openspec/changes/fixit-mvp/design.md` §14b (14b.4 revised 2026-10-26) and tasks `openspec/changes/fixit-mvp/tasks.md` Phase 4 (4.1–4.6, 4.8–4.12). No changes to `core/domain` nor `features/*`.
- S3. Topic sync (revised, spec "Ciclo de vida de las suscripciones"): every reconciliation subscribes every desired topic, unsubscribes every non-desired `category_*` of the eight and every registered non-desired `user_*`; only `user_*` topics are persisted (added before subscribing, removed only after a successful unsubscribe); on any failure the whole reconciliation repeats with backoff 30 s doubling to 10 min while the app is open.
- S4. Manual steps stay with the user (assumption: no remote action without explicit authorization): Cloudflare account, service account, `wrangler secret put`, `wrangler deploy`, device checklist (tasks 4.7 and 4.13).

## Tasks

| ID | Specs | Route | Status | Evidence |
|---|---|---|---|---|
| T1 | S1, S3 | inline | done | spec, design 14b.4/D26, tasks 4.9.5–4.9.13, state.yaml revised |
| T2 | S2 (4.1–4.6) | delegated writer (parallel unit: `worker/`, disjoint from Android) | done | 85b20ff; parent spot check `npm test` 152 passed, `tsc --noEmit` ok |
| T3 | S2 (4.8–4.12), S3 | delegated writer (parallel unit: `core/data`, `app`, gradle) | done | f3ecf7d, d05b2e0; parent spot check `testDebugUnitTest assembleDebug` exit 0 |
| T4 | S2, S3 | independent verifier (Risk items 2 security, 3 shared binding, 4 concurrency, 5 dependencies) | done | 1 blocker fixed in 75b0727; recheck on `git archive HEAD`: `testDebugUnitTest assembleDebug` exit 0 |
| T5 | S4 | user (manual) | pending | |

## Log

- L1 (2026-10-26, user): "ok implementa las notificaciones con esa soucion."
- L2: context — the user asked "tenemos implementado el sistema de notificaciones push? si no lo tenemos dame una propuesta"; the answer was that Phase 4 existed only as an OpenSpec proposal with 66 open tasks and an escalated judgment-day on TopicSync, with the candidate simplification recommended.
- L3: T1 done inline. Route evidence: T2 and T3 are two independent units with disjoint edit surfaces, each several files and well over 100 lines (Writer trigger a), launched together.
- L4: T2 returned. Deviations accepted: extra pinned `vite` + `.npmrc legacy-peer-deps` (vitest 5 peer crash in npm); JWKS download failure and malformed secret map to 502. Advisory: 3 dev-only `npm audit` findings via wrangler (fix needs wrangler newer than two weeks). Task 4.1.3 done by the parent.
- L5: T3 returned. Deviations accepted: `NavigationState.open` is a member (private `startTab`); notice title is `app_name`, channel name "Avisos de solicitudes"; firebase-messaging 26 `onRegistered` also calls `resubscribe()`; shared `parsePushEvent`/`PushKeys` and a testable `PendingPushEvent`; small icon reuses `ic_launcher_monochrome`. Writer fixed `Task<Void>.await()` returning null being read as a timeout. README `PUSH_WORKER_URL` added by the parent. Risk: items 2, 3, 4, 5 -> T4.
- L6: Engram project is `fixit` (user, 2026-10-26).
- L7: T4 verdict: PASS on S1-S3, security, shared binding, concurrency, dependencies and wire contract; worker probes 24/24. BLOCKER: commit 022009f carried staged deletions of `ic_launcher_background/foreground.xml` from the uncommitted logo work, so committed HEAD failed `processDebugResources`. Fixed in 75b0727 (restored in the branch only; the working tree keeps the logo deletion) plus the stale spec bullet on editing categories. Recheck limited to the blocker: exit 0. Advisories left open: `checkClaims` does not check `auth_time`; a cancelled GMS Task rethrows CancellationException in `FirebaseTopicMessaging` and ends that reconciliation without retry until the next key or token; pre-existing D18 items (no `category` validation in the create rule, no rate limit).
- Next: T5 manual (tasks 4.7 and 4.13) by the user; push, PR and merge are the user's decision.
