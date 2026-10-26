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
| T2 | S2 (4.1–4.6) | delegated writer (parallel unit: `worker/`, disjoint from Android) | pending | |
| T3 | S2 (4.8–4.12), S3 | delegated writer (parallel unit: `core/data`, `app`, gradle) | pending | |
| T4 | S2, S3 | independent verifier (Risk items 2 security, 4 concurrency, 5 dependencies) | pending | |
| T5 | S4 | user (manual) | pending | |

## Log

- L1 (2026-10-26, user): "ok implementa las notificaciones con esa soucion."
- L2: context — the user asked "tenemos implementado el sistema de notificaciones push? si no lo tenemos dame una propuesta"; the answer was that Phase 4 existed only as an OpenSpec proposal with 66 open tasks and an escalated judgment-day on TopicSync, with the candidate simplification recommended.
- L3: T1 done inline. Route evidence: T2 and T3 are two independent units with disjoint edit surfaces, each several files and well over 100 lines (Writer trigger a), launched together.
