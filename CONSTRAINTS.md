# Constraints: Faculty Dashboard

Last reviewed: 2026-09-29

## Floor (always enforced, no setup required)

- No new suppression comments: `@SuppressWarnings` without documented justification, `@ts-ignore`, `@ts-nocheck`, `eslint-disable`
- No unimplemented stubs: `throw new UnsupportedOperationException("Not implemented")`, `throw new Error("Not implemented")`, empty `catch {}`
- No skipped or deleted tests without explicit justification in the commit message (`@Disabled`, `.skip`, etc.)
- No secrets in source (API secrets, credentials, client secrets in environment variables only)
- Strict IDOR prevention: Every faculty-scoped query derives user identity from server-side security context, never from client input parameters
- This file does not get weakened to make a change pass

## Enforced with numbers

| Dimension | Rule | Checked by | Runs at |
|---|---|---|---|
| Backend Compile | Zero compiler warnings/errors | `mvn compile -q` | every edit |
| Frontend Types | Zero TypeScript errors | `npm run typecheck` (`tsc --noEmit`) | every edit |
| Secrets | No secrets committed in repo | `node scripts/check-secrets.mjs` | every edit, CI |
| Architecture Boundaries | Zero layer or module boundary violations | `mvn test -Dtest=*ArchTest*` (ArchUnit) | task end, CI |
| Unit & Integration Tests | 100% passing tests | `mvn test` & `npm test` | task end, CI |
| Coverage | Changed lines ≥ 80% covered | `mvn jacoco:report` & `vitest run --coverage` | task end, CI |
| File Upload Security | Sniff real MIME bytes (Tika), reject disallowed types, validate max size, opaque UUID keys | Upload integration tests | task end, CI |
| Accessibility | Zero critical or serious WCAG AA violations | Component accessibility audit | task end, preview |

## Measured, not yet enforced

| Metric | Today | Direction |
|---|---|---|
| Project test coverage | Baseline 100% of authored business logic | must not fall |
| Fast edit check duration | < 5 seconds | must not exceed 5s |
| Task verification check duration | < 90 seconds | must not exceed 90s |

## Exceptions

| ID | Rule | Path | Reason | Owner | Expires |
|---|---|---|---|---|---|
| (None) | — | — | No current exceptions | — | — |
