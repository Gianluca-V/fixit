# Create request: real photos, real location, map picker

## Objective

Make "create request" usable on a real device: real photos (system Photo Picker + camera), real location (GPS + reverse geocoding), and a free map picker with address search.

## Problem

- Publishing fails with "Ocurrió un error inesperado... cree la cuenta nuevamente". Root cause (logcat): `ImageCompressor.compress` throws `IllegalArgumentException: Cannot decode image android.resource://.../drawable/sample_repair_1` because the Phase 1 sample photos are vector XML drawables, which `BitmapFactory` cannot decode, and the error mapped to `DomainError.Unknown` silently.
- The add-photo button only adds those sample vectors.
- Location is `FakeLocation`: a fixed point in Florencio Varela. The address text field never changes the coordinates.

## Constraints

- No credit card / billing (specs header): no Google Maps or Places. Use MapLibre Native with OpenStreetMap raster tiles (osmdroid was archived on 2024-11-20) and the Android `Geocoder` for forward and reverse geocoding.
- Follow `openspec/changes/fixit-mvp/specs/media/spec.md` (Phase 3 gallery and camera) and `specs/location/spec.md` (Phase 3 permissions and real GPS).
- Domain contract unchanged: a request needs a `Place(GeoPoint, address)`; photos stay `List<String>` URIs uploaded to Cloudinary.
- Tests: JUnit4 + kotlinx-coroutines-test with the `:core:domain` testFixtures fakes.

## Delivery

- Branch `feat/phase3-media-location`, strategy `ask-on-risk`.
- Forecast: about 900 authored changed lines, which exceeds the 400-line budget. The chain strategy is asked before the PR.

## Tasks

- [x] T1 Log unmapped errors in `FirebaseCalls.toDomainError` (inline). Commit `bff1394`.
- [x] T1b Read the Cloudinary response body on the IO dispatcher (`NetworkOnMainThreadException` found after T2 unblocked uploads). Inline. Commit `6eb4129`. A publish with real photos was verified on the device.
- [x] T2 (commit `7d15b74`; ViewModel tests 13/13; gallery and camera verified on the Moto G71) Photos: FileProvider + `res/xml/file_paths.xml` in `:app`; `activity-compose` in `:core:ui`; rewrite `PhotoPickerButtons` with `PickVisualMedia`/`PickMultipleVisualMedia(missing)` and `TakePicture`; hide camera when the device has none; remove the sample-photo path from the create flow. (Delegated: 2+ non-trivial files.)
- [x] T3 (commit `3f50147`; 16 new unit tests; verified on the device; sign-up gate added inline; edit-profile gate in `6e7456e` after merging the teammate work) Real location: manifest permissions; `device/FusedLocationProvider` (10 s timeout leads to `LocationUnavailable`); `device/GeocoderAddressResolver`; swap the bindings in `RepositoryModule`; permission helper in `:core:ui` with rationale and a Settings action. (Delegated.)
- [ ] T4 Map picker + address search: MapLibre map with a center pin to pick a point (reverse geocode on confirm); address search with debounced `Geocoder` suggestions that set point + address; ViewModel `onPlacePicked`. (Delegated.)

## Acceptance criteria

- A request with gallery or camera photos publishes successfully on the Moto G71.
- "Usar mi ubicación" returns the real position and address.
- An address can be searched or picked on the map, and both update the coordinates.
- `./gradlew test` passes.

## Progress

- T1, T1b and T2 are done; publishing a request now works on the device.
- Review assessment (base `main`, committed only, untracked excluded): medium, `under_budget` (210 lines), so it stays pending in the slice.
- T3 done. Merged to local `main` (`3656623`) on top of the teammate commits (`ba478cd`). Review fixes in `6e7456e`. `./gradlew test` passes: 334 tests, 0 failures.
- Next step: T4 (map picker + address search). Push to origin is pending the user's decision.
