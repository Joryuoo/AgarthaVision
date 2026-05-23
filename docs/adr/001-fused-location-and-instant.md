# ADR-001: Fused Location Provider and Instant for GPS Capture

## Status
Accepted

## Context

Sprint 1 §1.3 requires GPS coordinates on each sample capture — latitude, longitude, and
accuracy. Two decisions were needed: (1) which location API to use, and (2) which timestamp
type to use for `LocationResult.capturedAt`.

**Location API options considered:**
- Android's legacy `LocationManager` — requires manual `LocationListener` registration /
  de-registration, callback-heavy, no built-in sensor fusion.
- Google Play Services `FusedLocationProviderClient` — single coroutine-friendly call,
  handles sensor fusion (GPS + cell + Wi-Fi), well-tested on the device fleet.

**Timestamp type options considered:**
- `Long` (epoch millis) — no type safety, easy to confuse with other numeric fields.
- `java.util.Date` — mutable, timezone-ambiguous, deprecated in idiomatic Kotlin.
- `java.time.Instant` — immutable, unambiguous UTC, available without desugaring at `minSdk=26`.

## Decision

**Location API: FusedLocationProviderClient** (`play-services-location:21.3.0`)

- `getCurrentLocation(PRIORITY_HIGH_ACCURACY, cancellationToken)` wrapped in
  `suspendCancellableCoroutine` + `withTimeoutOrNull(5_000L)`.
- Permission check via `ContextCompat.checkSelfPermission` before touching the client —
  returns `null` on denial; never throws `SecurityException` (acceptance criterion §1.5).
- `CancellationTokenSource` wires Kotlin coroutine cancellation to the GMS task so the OS
  request is cancelled when the calling coroutine is cancelled.
- Returns `null` on timeout or when the OS delivers no fix.

**Timestamp type: `java.time.Instant`**

- `minSdk = 26` means `java.time` is fully available without core library desugaring.
- Consistent with `SampleEntity.capturedAt: Instant` (Bundle 1 / ADR-002).
- Room stores it as `Long` epoch millis via `InstantConverter`.

## Consequences

**Easier:**
- One call handles GPS fix with no manual listener lifecycle.
- The `null` return contract is enforced at the interface level — callers cannot accidentally
  ignore the permission-denied case at compile time.
- `CancellationTokenSource` integration means the OS location request is always cleaned up
  when the coroutine scope is cancelled (e.g., ViewModel cleared mid-capture).

**Harder:**
- Adds Google Play Services as a required runtime dependency. Devices without GMS (de-Googled
  Android, some tablets) cannot use the app in this configuration.
- If GMS-free support is needed in the future, a `LocationManager`-backed implementation
  can be provided behind the `LocationProvider` interface without changing callers.
