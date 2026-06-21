# Hubble V2 issue 694 plan, implement, and review status

This document tracks plan, implementation evidence, and review status for
GitHub issue #694. The review section is deliberately explicit about passed
gates and remaining product/API boundary notes.

## 1. Baseline

Issue #694 tracks validation and cleanup for the new Hubble frontend and backend
work introduced by PR #632. At review time, PR #632 is still open/WIP and not
merged into `master`; evidence must therefore identify the exact branch and
candidate being tested.

The implementation and review phases must bind every result to a concrete code
baseline:

| Field | Required value |
|-|-|
| Toolchain branch or PR | Branch name, PR number, or release branch |
| Commit | Full commit hash |
| Hubble version | Maven revision and produced artifact name |
| Server baseline | HugeGraph Server branch, tag, or commit |
| Runtime environment | JDK, Maven, Node, Yarn, OS |

The current `master` branch is not enough to close issue #694 unless the Hubble
V2 changes and fixes are merged there. Results from local working directories,
untracked generated files, or old candidate directories must not be used as
release evidence.

## 2. Plan Scope

The plan covers the issue requirements:

| Area | Goal |
|-|-|
| Build | Compile Hubble backend, frontend, and distribution modules from a clean baseline |
| Runtime | Start the packaged Hubble candidate through its scripts and pass health/UI smoke |
| Bug fixes | Identify and fix runtime blockers that prevent normal Hubble operation |
| Frontend/backend integration | Confirm frontend routes reach the new graph backend APIs |
| Server and Loader integration | Confirm GraphServer access and Loader-backed import flow |
| Release readiness | Classify known issues, API gaps, binary contents, and i18n risks |

## 3. Phase Contract

The work should proceed in three phases.

| Phase | Output | Must not include |
|-|-|-|
| Plan | Scope, baseline requirements, commands, gates, and evidence templates | Claims that validation already passed |
| Implement | Code changes, tests, scripts, docs, and candidate generation | Unreviewed local artifacts as final evidence |
| Review | Reproducible command output, CI links, screenshots, issue links, and release sign-off | Unbound summaries without commit/candidate identity |

## 4. Subtasks

### 4.1 Build and Runtime Blockers

Validate the build path in dependency order:

```bash
mvn install -pl hugegraph-client,hugegraph-loader -am -DskipTests -ntp
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
cd hugegraph-hubble
mvn package -DskipTests -ntp
```

The implementation phase must record the first failing module and root cause if
any command fails.

Runtime smoke must use the packaged candidate, not a source tree shortcut:

```bash
cd <candidate-dir>/bin
./start-hubble.sh
curl -s -D - http://127.0.0.1:8088/actuator/health
curl -s -D - http://127.0.0.1:8088/
./stop-hubble.sh
```

### 4.2 Frontend and Backend Integration

Verify that the frontend build is created from the selected baseline and that
the packaged `ui/` directory is served by Hubble.

Minimum routes:

| Route | Check |
|-|-|
| `/` | React root renders and static assets load |
| `/graph-management` | Graph connection list loads |
| `/graph-management/{id}/metadata-configs` | Schema page calls Hubble backend |
| `/graph-management/{id}/data-import/import-manager` | Import job page calls Hubble backend |
| `/graph-management/{id}/data-analyze` | Gremlin and algorithm tabs render |
| `/graph-management/{id}/async-tasks` | Async task page calls Hubble backend |

### 4.3 GraphServer and Loader Flow

Run a non-destructive smoke against a dedicated test graph:

| Flow | Required evidence |
|-|-|
| Graph connection | Hubble API response and Server endpoint information |
| Schema creation | Hubble-created schema visible through direct Server schema APIs |
| File upload and mapping | Hubble upload/mapping API responses |
| Loader-backed import | Job and load task final states |
| Gremlin counts | Hubble and direct Server counts match |
| shortestPath | Hubble graph view result and direct Server traverser result are compared |

### 4.4 API Boundary and Known Issues

Do not describe Server-only or unimplemented Hubble behavior as supported Hubble
functionality.

| Case | Required wording |
|-|-|
| Hubble endpoint exists and passes smoke | Supported in this verified scope |
| Server direct API passes but Hubble has no UI/backend route | Server capability only |
| Hubble returns 404/405 for a frontend path | Hubble UI/API integration gap or product boundary |
| Direct Server request also fails | Server issue, configuration issue, or data issue |
| Release-impacting known issue | Must have issue link, impact, workaround, and release note text |

### 4.5 i18n and UI Layout

Check both static resources and runtime rendering:

| Check | Requirement |
|-|-|
| Resource keys | `zh-CN` and `en-US` keys are symmetric |
| Empty values | No empty visible translations |
| Runtime raw keys | Core routes do not show untranslated key paths |
| Language switch | UI switch changes visible text in both directions |
| Layout | English text does not overlap or break core controls on desktop/mobile |

### 4.6 Binary Distribution Compliance

The binary inventory must be generated from the final candidate tarball.

Required checks:

| Check | Blocker examples |
|-|-|
| Root structure | Missing `bin`, `conf`, `lib`, `ui`, `LICENSE`, `NOTICE`, or `licenses` |
| Runtime residue | `logs`, `upload-files`, H2 db files, pid files, lock files |
| Node artifacts | `node_modules`, Node runtime, Yarn/npm caches |
| Third-party jars | Missing license or NOTICE coverage |
| Unknown binaries | Unexplained `.so`, `.dll`, `.dylib`, `.exe`, `.bin`, archives |
| Datasets | Unverified provenance or redistribution terms in release artifact |

## 5. Acceptance Gates

Issue #694 can be considered ready for review only when all gates have evidence
bound to the same baseline.

| Gate | Required evidence |
|-|-|
| Build gate | Command output for dependency build, Hubble BE tests, frontend build, dist package |
| Runtime gate | Candidate startup, health, UI root, and shutdown output |
| Integration gate | Browser/API evidence for frontend routes reaching Hubble backend |
| Function gate | Graph connection, schema, import, Gremlin, and shortestPath smoke |
| API boundary gate | Unsupported API list with classification and issue/release-note plan |
| i18n gate | Static key check and runtime language switch/layout evidence |
| Binary gate | Candidate inventory and legal/residue review |
| Known issue gate | Every accepted issue has issue link, impact, workaround, and release note text |

## 6. Review Checklist

Review should reject the work if any of the following is true:

- The report lacks branch, commit, candidate name, or Server baseline.
- Evidence comes from untracked local directories or stale generated candidates.
- Plan text claims implementation results before commands are executed.
- Server direct capability is described as Hubble UI/backend support.
- Non-shortestPath algorithm behavior is claimed as supported without matching
  Hubble backend routes and successful verification.
- Local dataset archives are included in source or binary release artifacts
  without provenance, copyright, license, and redistribution review.
- Binary contents are counted from an expanded runtime directory instead of the
  final tarball.

## 7. Implement Phase Inputs

The implementation phase should start by filling this table:

| Field | Value |
|-|-|
| Toolchain baseline | |
| Server baseline | |
| Candidate name | |
| Build commands | |
| Test commands | |
| Browser smoke command | |
| Live Hubble/Server smoke command | |
| Binary inventory command | |
| Known issue links | |

## 8. Implement Phase Evidence

This section records the current implementation work on branch
`codex/hubble-v2-issue-694-plan`. The starting commit before this local change
set is `077802f015cd771a425f1ad05d6db5761170e154`.

| Field | Value |
|-|-|
| Toolchain baseline | `codex/hubble-v2-issue-694-plan` |
| Server baseline | Docker container `observability-hugegraph-alert-1`, image `hugegraph/hugegraph:1.5.0`, URL `http://127.0.0.1:18082` |
| Candidate name | `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` |
| Candidate timestamp | `2026-06-21 16:44:58 +0800`, `198057826 bytes` |
| Runtime environment | macOS 26.5.1 arm64, JDK 21.0.11, Maven 3.9.16 |
| Build commands | `mvn install -pl hugegraph-client,hugegraph-loader -am -DskipTests -Dmaven.javadoc.skip=true -ntp`; `cd hugegraph-hubble && mvn package -DskipTests -Dmaven.javadoc.skip=true -ntp` |
| Test commands | `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp`; `node hugegraph-hubble/hubble-dist/assembly/travis/check-hubble-i18n.js`; `hugegraph-hubble/hubble-dist/assembly/travis/check-hubble-dist.sh hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` |
| Live Hubble/Server smoke command | `hugegraph-hubble/hubble-dist/assembly/travis/verify-hubble-issue-694.sh hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz http://127.0.0.1:18082` |
| Binary inventory command | `hugegraph-hubble/hubble-dist/assembly/travis/check-hubble-dist.sh hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz --json-output .workflow/hubble-v2-issue-694/evidence/hubble-dist-inventory.json` |
| API inventory command | `python3 hugegraph-hubble/hubble-dist/assembly/travis/run_algorithm_api_inventory.py --json-output .workflow/hubble-v2-issue-694/evidence/algorithm-api-inventory.json --markdown-output .workflow/hubble-v2-issue-694/evidence/algorithm-api-inventory.md` |
| Known issue links | GitHub issue `apache/hugegraph-toolchain#694` |

Implemented fixes:

- Upgrade Lombok to `1.18.30` so the Hubble build can run on JDK 21.
- Make the Hubble BE `unit-test` profile run only `UnitTestSuite`.
- Preserve `shortestPath` path edges in `OltpAlgoService` `GraphView`
  responses, covered by `OltpAlgoServiceTest`.
- Add `shortpath` as a compatibility alias for FE shortest-path requests while
  preserving the canonical `shortestPath` backend route, covered by
  `OltpAlgoControllerTest`.
- Include both `csv` and `txt` in the default Hubble upload file whitelist,
  covered by `HubbleOptionsTest`.
- Emit scalar Loader IDs for customized vertex IDs and customized edge
  source/target IDs by default, covered by `LoadTaskServiceTest`.
- Make `start-hubble.sh -f` a real no-argument foreground mode and update the
  Docker entrypoint to use it.
- Use `nohup` in packaged daemon mode so background startup survives after the
  parent shell exits.
- Package root-level `LICENSE`, `NOTICE`, and `licenses/` into the Hubble
  binary distribution.
- Add repeatable Hubble #694 verification helpers for i18n resources, expanded
  binary inventory, API boundary inventory, packaged UI routes, Hubble API
  routes, browser UI smoke, runtime i18n smoke, and local GraphServer access.
- Extend the live smoke helper with a full optional `--loader-flow` covering
  schema creation, CSV upload, mapping, load task polling, Gremlin count
  comparison, and shortestPath comparison.

Commands run in this pass:

| Gate | Command | Result |
|-|-|-|
| Hubble package | `cd hugegraph-hubble && mvn -o package -DskipTests -Dmaven.javadoc.skip=true -ntp` | Passed |
| Hubble BE unit tests | `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp` | Passed, 16 tests |
| i18n static check | `node hugegraph-hubble/hubble-dist/assembly/travis/check-hubble-i18n.js` | Passed |
| Binary check | `hugegraph-hubble/hubble-dist/assembly/travis/check-hubble-dist.sh hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz --json-output .workflow/hubble-v2-issue-694/evidence/hubble-dist-inventory.json` | Passed; JARs `270`, license files `275`, FE license files `43`, native-bearing JARs `7` |
| API boundary inventory | `python3 hugegraph-hubble/hubble-dist/assembly/travis/run_algorithm_api_inventory.py ...` | Passed; FE algorithms `16`, Hubble BE supported `1`, FE-only `15` |
| Whitespace check | `git diff --check` | Passed |
| Packaged Hubble + Server smoke | `verify-hubble-issue-694.sh ... http://127.0.0.1:18082` | Passed; Hubble candidate started, route fallback passed, Server `/versions` passed, graph connection was created, and schema/job-manager/async-tasks APIs returned `status:200` |
| Foreground Hubble smoke | `./bin/start-hubble.sh -f` from unpacked candidate, then `curl /actuator/health` | Passed, `{"status":"UP"}` |
| Route fallback smoke | `curl` core Hubble routes while packaged candidate was running | Root and core route fallback served React root |
| UI full acceptance | `node run_ui_full_acceptance.js ...` | Aggregator ran; API inventory subcheck passed, browser and runtime i18n subchecks failed because local environment lacks `playwright` |
| UI full acceptance with system Chrome | `NODE_PATH=/opt/homebrew/Cellar/playwright-cli/0.1.14/libexec/lib/node_modules/@playwright/cli/node_modules node run_ui_full_acceptance.js --hubble-url http://127.0.0.1:8088 --conn-id 1 --output-dir .workflow/hubble-v2-issue-694/evidence/ui --json-output .workflow/hubble-v2-issue-694/evidence/ui-full-acceptance.json --chromium-executable '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'` | Script support was implemented and JS syntax checks passed; Codex could not execute the browser run because approval reviewer returned `503 Service Unavailable` for the required escalated localhost/browser command |
| Earlier sandbox Loader-flow attempt | `python3 run_live_hubble_smoke.py ... --server-url http://127.0.0.1:18082 --bind-host 127.0.0.1 --loader-flow ...` | Failed before checks because the sandbox could not bind `127.0.0.1:8088`; superseded by the successful user-terminal run below |
| Live Loader flow | `python3 run_live_hubble_smoke.py ... --server-url http://127.0.0.1:18082 --bind-host 127.0.0.1 --loader-flow --data-prefix issue_694_1725 ...` | Passed from the user's terminal; evidence has schema, upload, mapping, Loader task `SUCCEED`, job `SUCCESS`, Hubble/Server Gremlin counts `3/2`, and shortestPath Hubble/direct Server graph/path comparison `3/2` |
| UI script syntax checks | `node --check run_ui_browser_smoke.js`; `node --check run_ui_i18n_switch_smoke.js`; `node --check run_ui_full_acceptance.js` | Passed after adding `--chromium-executable` / `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` / `CHROME_PATH` support |
| UI full acceptance with system Chrome | `NODE_PATH=/opt/homebrew/Cellar/playwright-cli/0.1.14/libexec/lib/node_modules/@playwright/cli/node_modules node run_ui_full_acceptance.js --hubble-url http://127.0.0.1:8088 --conn-id 1 --output-dir .workflow/hubble-v2-issue-694/evidence/ui --json-output .workflow/hubble-v2-issue-694/evidence/ui-full-acceptance.json --chromium-executable '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'` | Passed from the user's terminal after starting packaged Hubble from `/private/tmp/hubble-issue694-live-1725/apache-hugegraph-hubble-1.7.0`; evidence includes 5 route screenshots/API matches and zh-CN/en-US i18n screenshots |

Review status:

| Gate | Status | Notes |
|-|-|-|
| Build gate | Passed | Dependency build, Hubble BE unit tests, Hubble package, i18n static check, binary check, and whitespace check passed |
| Runtime gate | Passed for Hubble-only smoke | Packaged Hubble started, `/actuator/health` returned `UP`, and `/` plus core static routes served React HTML/root |
| Integration gate | Passed | Hubble + Server smoke passed; browser evidence shows graph management, metadata configs, data import, data analyze, and async tasks requested expected Hubble backend APIs |
| Function gate | Passed for planned live scope | Unit coverage protects `shortestPath` graph view edge output and Loader customized ID mapping; Hubble + Server smoke passed; Loader-flow live evidence passed with Gremlin and shortestPath direct Server comparison |
| API boundary gate | Passed with scope notes | Static inventory confirms only FE shortest-path is backed by Hubble BE (`shortpath`/`shortestPath`); non-shortestPath FE slugs remain product-boundary/API-gap items and are not claimed as supported |
| i18n gate | Passed | Static key check passed; runtime zh-CN/en-US switch passed with screenshots and changed visible text |
| Binary gate | Passed | Final tarball includes `bin`, `conf`, `lib`, `ui`, `README.md`, `LICENSE`, `NOTICE`, and `licenses/`, with no runtime residue detected |
| Known issue gate | Open | No additional release-impacting issue was filed in this pass |

Issue #694 checkbox review:

| Issue item | Status | Evidence or gap |
|-|-|-|
| Compile the new Hubble frontend and BE module | Passed on this branch | Hubble package command completed successfully |
| Identify existing runtime bugs in the backend | Passed for issue scope | Found JDK 21/Lombok compile blocker, foreground start blocker, daemon startup shell-lifetime issue, shortestPath missing edges, default txt upload whitelist gap, and Hubble binary legal-file gap |
| Fix minor bugs that prevent normal operation | Passed for issue scope | Fixes are implemented and covered by unit, static, runtime, live Loader, and browser checks |
| Verify all fixes with appropriate testing | Passed for issue scope | Unit, package, static i18n, binary, whitespace, Hubble runtime, live GraphServer/Loader, browser route/API, and runtime i18n checks pass |
| Start the backend service successfully | Passed for Hubble-only scope | Current rebuilt candidate starts from package scripts and returns health `UP` |
| Access the new graph backend through the frontend | Passed | Browser evidence shows each core route matched the expected Hubble backend API and saved screenshots |
| Confirm integrated system `GraphServer + GraphLoader` | Passed | Loader-backed import completed through Hubble against Docker Server `1.5.0`, with direct Server schema, Gremlin count, and shortestPath comparison |

## 9. Final Review Against Issue 694

The current branch now has evidence for the issue #694 gates covered by the
Feishu plan. The only remaining scope note is product/API boundary wording for
algorithm routes that are present in the FE inventory but not implemented by
Hubble BE.

| Item | Status | Evidence or note |
|-|-|-|
| Browser frontend/backend evidence | Passed | `evidence/ui-full-acceptance.json` plus route screenshots under `evidence/ui/` |
| Frontend runtime interaction | Passed | API matches for graph list, schema, import manager, data analyze, and async tasks |
| Runtime i18n/layout | Passed | `ui-i18n-switch-smoke.json`, `i18n-zh-CN.png`, and `i18n-en-US.png` |
| Logs and screenshots | Passed locally | Packaged Hubble logs remain under `/private/tmp/hubble-issue694-live-1725/.../logs`; screenshots are saved under `.workflow/hubble-v2-issue-694/evidence/ui/` |
| Environment reconciliation | A bot comment mentions Java 11, Node 18.20.8, and port 36320, while this branch uses JDK 21, frontend-maven-plugin Node 16.20.2, and Hubble port 8088 | State which source is authoritative for this branch, and record the exact versions used in the final review |
| Non-shortestPath algorithm slugs | Scope note | API inventory still shows FE algorithms `16`, Hubble BE supported `1`, FE-only `15`; do not describe those FE-only slugs as Hubble BE supported |
