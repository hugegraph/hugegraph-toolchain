# Hubble contributor guide

## Authentication and connection boundary

The `1.8/master` path is the source of truth. The backend detects authentication mode from HugeGraph Server and uses one connection resolver. The resolver chooses either a direct server URL or an address discovered from PD; callers must not reimplement `usePD` or infer connection state from page-local flags. In PD mode the server address returned by discovery is authoritative, so a manual server URL is not required.

Use the unauthenticated HugeGraph client for anonymous mode. Do not manufacture an empty token or an administrator session. Anonymous mode has no account context and account/permission routes are hidden or rejected at the capability boundary.

## Compatibility policy

Compatibility is intentionally one-way:

- `1.8/master`: modern GraphSpace/auth contracts and the complete UI.
- `1.7`: thin fallback for the legacy response shape; keep the core workflow usable without adding version branches to controllers or React pages.
- `1.5` standalone: core graph/schema/data operations only. GraphSpace management is unsupported and should degrade with an explicit capability response. Do not add a PD variant for 1.5.

Version checks belong in the client compatibility adapter and connection resolver. New code should consume capabilities, not compare literal versions. When an old image cannot satisfy a capability, mark the test as `needs input` or `skipped` with the exact image tag and reason.

## Verification

Follow the change-to-check table in [the verification guide](docs/verification.md). Reuse the existing unit, controlled-browser and live runners; do not introduce another test framework for a feature.

Before implementation, verify the remote base and existing issues/PRs, state the expected behavior, and ask the user to choose unresolved requirements or design alternatives. Continue independent inspection while waiting for a required answer.

For UI changes, run `pnpm verify:ui` from `hubble-fe` and use Chrome to exercise the affected flow, login/non-auth mode, connection switching, and account/GraphSpace visibility. Static inspection and unit tests are not a substitute. The controlled-browser gate uses fixture APIs and does not prove real authentication, PD discovery or Vermeer execution; use the existing live runners when those contracts change.

For a bug fix, add a regression that fails on the old behavior when practical. Investigate failures rather than weakening assertions or extending timeouts just to obtain a pass. Report the source revision, checks actually run, evidence location, failures and unverified scope; missing input is not a pass, and test counts do not establish full acceptance.

Keep routine screenshots and reports in CI artifacts or PR attachments. Commit only selected core screenshots with lasting documentation value, referenced by `README.md` or the relevant guide and labeled with their validation scope.
