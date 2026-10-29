# Hubble verification

Choose checks by the behavior a change can affect. This guide reuses the existing
runners and adds one controlled-browser command; it does not replace the live
distribution gate or require a PD/HStore/Vermeer cluster for every PR.

## Three validation layers

| Layer | Run it for | What a pass establishes |
| --- | --- | --- |
| Quick / unit | Logic, API contracts, translations, types and focused regressions | The named assertions pass in their test environment. DOM mocks do not establish browser layout or real service behavior. |
| Controlled browser | UI, navigation, rendering and user-visible error recovery | The current production frontend completes the named Chrome flows against controlled API responses. It does not establish real authentication, PD discovery, Store counts or Vermeer execution. |
| Live | Service contracts, connection discovery, authentication, packaging and deployment acceptance | Only the requested checks against the identified real services, data and account. Preserve the runner's remaining coverage and missing-input status. |

PR CI retains the existing build/unit checks and real single-graph smoke. The
candidate lane additionally runs the controlled-browser gate once. Broader live
PD/HStore/Vermeer acceptance remains an explicit pre-release or manual run; the
single-graph smoke does not prove that matrix.

## Select the checks

| Change | Minimum checks before claiming it verified |
| --- | --- |
| Documentation only | Check links, commands and factual claims. Run a script's focused tests if its documented interface changes. |
| Frontend logic, types or translations | Relevant Jest tests, ESLint, TypeScript and i18n checks. Widen to the frontend suite for shared behavior. |
| UI, CSS, routes or a visible error state | The frontend checks above, `pnpm verify:ui`, and Chrome interaction with the changed flow. Check login/anonymous visibility, graph/GraphSpace switching, the next action, empty/error states and recovery where affected. Inspect screenshots; their existence alone is not visual approval. |
| Backend/API, permissions or connection resolution | Relevant Java and contract regressions. Add controlled-browser coverage when the response changes UI behavior. Use live verification for claims about real authorization, discovery or service execution. |
| Shared graph rendering, IDs or result size | Relevant visualization/runtime tests, the existing PNG contract, and browser checks for the changed interaction and representative data shape. Report scale/performance separately from small-fixture correctness. |
| SDK, launchers or distribution | Existing SDK provenance checks, packaging/launcher tests and the packaged live gate. Do not substitute a frontend fixture for distribution acceptance. |

The table is a minimum selection rule, not a requirement to run every matrix for
every edit. Reuse a passing check until a new change, failure or unresolved concern
justifies rerunning it. Keep heavy builds bounded on shared machines.

## Commands and evidence

Use the repository's pinned Node/pnpm and install Playwright Chromium. From
`hugegraph-hubble/hubble-fe`, run `pnpm install --frozen-lockfile` once and
`pnpm exec playwright install chromium` if the browser is not installed:

```bash
pnpm lint && pnpm typecheck && pnpm i18n:check
pnpm test:ci
pnpm verify:ui
```

For a narrow local change, run the relevant Jest paths instead of the full suite;
CI still runs its configured suite. The UI command calls the existing frontend
build helper: it reuses a verified production build or performs the frozen install
and rebuild when needed. It then starts a local preview, invokes the existing
deployment regression runner, checks its report, and stops the preview.
It writes `browser-report.json`, `verification.json` and screenshots under
`hugegraph-hubble/target/hubble-ui-fixture` by default. A missing, failed or invalid
report cannot satisfy the gate. The report's named checks define its coverage;
add an affected-flow regression when the existing checks do not cover a bug.

The fixture gate checks core login/anonymous access, GraphSpace editing and
switching, graph-scoped query results, delayed tasks and actionable failures.
It also checks selected query controls and the empty state at desktop and tablet
widths for clipping, obstruction, real clickability and horizontal overflow.
These are functional usability checks, not pixel-golden comparisons or a full
site-wide visual audit. Inspect the saved screenshots for other visual defects.

From the repository root, backend unit tests use the existing profile:

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

For an already running real deployment, configure its credentials through the
environment and provide an existing data/permission fixture. From
`hugegraph-hubble`:

```bash
node hubble-dist/assembly/travis/run_ui_full_acceptance.js --hubble-url "$HUBBLE_URL" --graph-fixture "$HUBBLE_GRAPH_FIXTURE"
```

The [browser regression guide](browser-regression-guide.md) and
[README distribution instructions](../README.md#browser-acceptance) describe the
fixture contract and packaged gate. Preserve `requestedSmokeStatus`,
`overallAcceptanceStatus`, requested checks and remaining coverage. A passing
requested smoke can still report overall acceptance as incomplete. Required
missing inputs produce `needs input` (exit 2); failures exit 1. Neither is a pass.

Keep routine reports, screenshots and logs in CI artifacts or PR attachments.
Commit only selected core images with lasting documentation value and a scope
caption. Record the tested revision, any relevant uncommitted changes, command,
environment and evidence location. Evidence from an older build does not verify
the current change. Never include credentials in reports or command arguments.

## AI iteration checklist

1. Fetch and identify the base; inspect existing issues and PRs to avoid duplicate
   fixes. Read the relevant contributor and compatibility rules.
2. State the intended behavior and its acceptance checks. Resolve factual
   questions by inspection; ask the user to choose unresolved requirements or
   design alternatives before dependent implementation.
3. Reproduce a defect and choose the smallest relevant regression. Where
   practical, show that it fails against the old behavior and passes with the fix.
   Record why a negative control or live reproduction is unavailable.
4. Make the bounded change, then run the selected checks. For UI work, verify the
   actual action and recovery path in Chrome, beyond the presence of an element.
   Preserve a reusable scenario in the existing runner when it prevents recurrence.
5. Investigate failed assertions, unexpected API requests and browser errors.
   Change fixtures, assertions or timeouts only for an explained behavioral or
   measured runtime reason, not merely to turn a check green.
6. Report what passed, failed, needs input or remains untested, with evidence and
   the tested revision. Separate quick/unit, controlled-browser and live results;
   do not infer deployment usability or full acceptance from test counts.
