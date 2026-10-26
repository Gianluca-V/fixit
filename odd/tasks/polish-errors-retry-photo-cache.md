# Polish: error wording, retry offers, camera photo cleanup, repo hygiene

## Objective

Fix four small UX and repo details that surfaced while testing on the device.

## Problem

- `error_unknown` says "Si el problema continúa, cree la cuenta nuevamente" for every unmapped error, including publishing and rating. That hint only fits a signed-in account without a profile document.
- Edit profile offers "Reintentar" for non-retryable errors (`Unauthorized`, `NotFound`); the professional profile screen has no retry at all.
- Camera captures stay in `cacheDir/photos/` forever.
- `.codegraph/` (local index) was not ignored; the media and location task document was untracked.

## Constraints

- Keep domain errors exhaustive; tests are JUnit4 + kotlinx-coroutines-test with the `:core:domain` testFixtures fakes.
- UI copy in neutral Spanish ("usted"), code in English.

## Delivery

- Branch `polish/errors-retry-photo-cache`, strategy `ask-on-risk`. Forecast is under 400 lines.

## Tasks

- [x] P4 Ignore `.codegraph/` and track `odd/tasks/create-request-media-location.md` (inline). Commit `abb4baa`.
- [x] P1 (spec updated with the user's approval; tasks.md 2.5.7; no runnable JVM test: Firebase repositories and asMessage() have none, so compile + manual check) Profile-missing error with its own message; `error_unknown` becomes neutral. (Delegated.)
- [x] P2 (commit `6ac4669`; ProProfileViewModelTest RED then GREEN) `isRetryable` shared in `features/professional/common`; used in edit profile; retry added to the professional profile. (Delegated.)
- [x] P3 (commit `4dafd20`; CapturedPhotoNameTest 7 tests RED then GREEN; only FileProvider `photos/` URIs, deleted after a successful write) Delete camera captures after a successful publish. (Delegated.)

## Acceptance criteria

- A publish or rating failure no longer suggests re-creating the account.
- An account without a profile still shows the re-create hint.
- No "Reintentar" button for `Unauthorized` or `NotFound` in the professional screens; the profile screen can retry retryable errors.
- After publishing with camera photos, `cacheDir/photos/` no longer holds them.
- `./gradlew test` passes.

## Progress

- P2, P3 and P4 are done; `./gradlew test` passes. P1 is done after the spec revision. Manual check of the profile-missing message is pending (the device was disconnected).
