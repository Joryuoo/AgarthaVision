# Implementation Notes: Sign-out — core (PR1 of 2, Settings scope)

## Changed files

| File | Change summary |
|---|---|
| `domain/repository/AuthRepository.kt` | Added `suspend fun signOut()` with KDoc describing ADR-008 semantics |
| `domain/usecase/auth/SignOutUseCase.kt` (new) | `invoke(): Result<Unit>`; fails via `check()` while `SessionManager.state` is `Active`; otherwise delegates to `authRepository.signOut()` |
| `domain/usecase/auth/HasActiveSessionUseCase.kt` | Deleted — confirmed dead (only self-reference, no callers) after the ADR-007 `LoginViewModel` rewrite removed the cold-start auto-forward flow it backed |
| `data/repository/SupabaseAuthRepository.kt` | Implemented `signOut()`: `runCatching { supabase.auth.signOut() }` (best-effort remote revoke) then unconditionally clears all three `LocalIdentity` DataStore keys |
| `commitlint.config.js` | Added `settings` to the `scope-enum` allowlist, ahead of PR2 which will use it |
| `CONTEXT.md` | Added **ADR-008** to §8 (full local sign-out, warn-don't-block via an Active-session guard, rejected token-only alternative, partial mitigation of the ADR-007 shared-device fail-open consequence) |
| `TODO.md` | Marked ADR-007 known-issue arm (a) "no sign-out flow" as resolved by this PR |
| `app/src/test/.../SignOutUseCaseTest.kt` (new) | Two tests covering the success path and the Active-session guard |
| `app/src/test/.../GenerateSessionReportUseCaseTest.kt`, `GetRecordsUseCaseTest.kt`, `GetSampleDetailUseCaseTest.kt` | Added `override suspend fun signOut() = Unit` to each file's local `AuthRepository` fake — required by the new interface method, unrelated to those tests' actual assertions |

## Decisions made

- **Followed the scope's recommended sign-out semantics as-is**: full local sign-out (token + cached identity both cleared), not the token-only alternative. Reasoning is recorded in ADR-008 rather than repeated here.
- **Did not wire `FlaggedFrameStore.clear()` into `SignOutUseCase`.** The scope draft anticipated this, but `FlaggedFrameStore.clear()` already early-returns unless `sessionManager.state` is `Active` — and `SignOutUseCase`'s own guard means it only ever runs when the state is *not* Active. Calling `clear()` there would always be a no-op, so it was left out rather than shipped as dead code. Documented as a KDoc cross-reference instead: any flagged frames belonging to the just-ended session are already cleared by the normal end-session path, consistent with the existing "flagged frames lost on logout" behavior.
- **No DI module changes needed.** `AuthRepository` was already bound (`RepositoryModule.bindAuthRepository`); `SignOutUseCase`'s two dependencies (`AuthRepository`, `SessionManager`) are both existing `@Inject`-constructor/`@Singleton` types.
- **Detekt**: verified zero new findings from every file touched in this PR (grepped detekt's full output against each changed filename — no matches). The pre-existing 40-finding baseline is unchanged.

## Deferred (not in this PR)

- Everything in PR2 per the scope split: `SettingsScreen`, `SettingsViewModel`, `PendingSyncCounts`, `ObservePendingSyncCountsUseCase`, DAO count queries, navigation wiring, strings.xml, and deleting `SettingsScreenPlaceholder.kt`.
- `schema.ts` — not touched; this PR has no schema change (token + DataStore only).

## Tests added / updated

| Test file | What it covers |
|---|---|
| `domain/usecase/auth/SignOutUseCaseTest.kt` (new) | Signs out and calls `authRepository.signOut()` when no session is active; fails with `Result.failure` and never calls `signOut()` when `SessionManager.state` is `Active` |
| `domain/usecase/records/GenerateSessionReportUseCaseTest.kt` | Interface-compliance fix only (`ReportAuthRepository` fake) |
| `domain/usecase/records/GetRecordsUseCaseTest.kt` | Interface-compliance fix only (`FakeAuthRepository` fake) |
| `domain/usecase/records/GetSampleDetailUseCaseTest.kt` | Interface-compliance fix only (`DetailAuthRepository` fake) |

## Verification

- `bun run build` equivalent (`:app:compileDebugKotlin`): BUILD SUCCESSFUL
- `:app:compileDebugUnitTestKotlin`: BUILD SUCCESSFUL
- `bun run test` (`:app:testDebugUnitTest`): BUILD SUCCESSFUL, all tests pass
- `ktlintCheck`: 0 violations
- `detekt`: 40 findings, identical to the pre-existing tracked baseline; zero attributable to this PR's files
