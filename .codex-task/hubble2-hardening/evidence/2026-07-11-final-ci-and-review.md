# 2026-07-11 Final CI and Review Evidence

## Frozen implementation

- Branch: `hubble2`
- PR: [#4](https://github.com/hugegraph/hugegraph-toolchain/pull/4)
- Product implementation commit: `a9231067d8446e35e4a865b2dc3e72ed49603af6`
- CI header-only follow-up: `03285ac1081185b3ca239592187115dd97dace0b`
- Final package built from the product implementation: SHA-256
  `7f735a2c6a20223a89534ba1fe8c06006fbdf9f3c8dd521daf3d25f5f5653368`, frontend asset
  `main.e159790d.js`.
- The follow-up changed only ASF comments in test/helper/source files and TODO tracking. It did not change executable
  behavior; the final PR Hubble CI rebuilt the package from `03285ac1` and passed all package/runtime gates.

The complete non-PD/PD route, mutation, failure and English matrices were executed against package
`df94a648448ec3b65f631c811a4f2ab83bf67de357f0aab3c49ac545f5227e14`. The only subsequent executable change
was the Dashboard window lifecycle. Its affected scope was rebuilt as package `7f735a2c...3368` and reverified in
real PD Chrome for reachable, unreachable and popup-blocked cases. The same final package then passed the CI-style
non-PD authentication/API/Loader/UI acceptance. The later `03285ac1` change was comment-only, so the previous full
matrices plus affected-scope replay remain applicable without claiming that the old package is the final binary.

## Fresh local gates

Environment: macOS arm64, Temurin 11.0.29 for the final batch, persistent `mvnd` daemon `43659201` / PID `91368`,
Node 18.20.8 in Maven frontend build, Yarn 1.22.x.

- `mvnd test -P unit-test -pl hubble-be -ntp`: BUILD SUCCESS, 111 tests, 0 failure/error/skip, 13.192s.
- `mvnd test -pl hugegraph-client -Dtest=UnitTestSuite -ntp`: BUILD SUCCESS, 50 tests, 0 failure/error/skip,
  3.891s. `RestClientStatusTest,GraphsAPITest` separately passed 7/7 after status matching was narrowed.
- `mvnd install -pl hugegraph-client,hugegraph-loader -am -DskipTests -Dmaven.javadoc.skip=true -ntp`:
  reactor BUILD SUCCESS, 36.681s. Tests were not represented by this install; the real test results are listed
  separately.
- `mvnd -e compile -Dmaven.javadoc.skip=true -ntp` in `hugegraph-hubble`: BUILD SUCCESS; the frontend i18n and
  production build embedded in the reactor also passed.
- Before the reviewer fix, `yarn test --watchAll=false` passed 40 suites / 147 tests. After the final Role route
  regression was added, the same full command passed 40 suites / 148 tests, 0 failed, 6.04s.
- Scoped final Dashboard ESLint: exit 0. Final i18n: 1585/1585 locale keys and 1106 static keys, exit 0.
- Final post-review-fix `mvnd package -DskipTests -Dmaven.javadoc.skip=true -ntp` in `hugegraph-hubble` reused
  daemon `43659201` and completed BUILD SUCCESS in 1m54s.
  The package audit passed with 392 JARs, 275 license files, 43 frontend license files and 10 native-bearing JARs.
- `verify-hubble-issue-694.sh` on the final package: real non-PD Server authentication, Hubble authentication,
  Schema, CSV Loader/Task, Server REST counts/path, analysis boundary, browser route/API and i18n switch all passed;
  browser console errors were 0. Evidence:
  [`2026-07-11-final-package-acceptance-e159`](2026-07-11-final-package-acceptance-e159/manifest.json).
- `git diff --check`, staged `git diff --cached --check`, all evidence JSON through `jq empty`, and local Markdown
  link existence audit passed. Ignored runtime DB/progress-site files were restored and remained uncommitted.

## Final PR checks

All checks below ran on exact head `03285ac1081185b3ca239592187115dd97dace0b` and completed SUCCESS:

| Workflow/check | Event | Attempt | Run |
| --- | --- | ---: | --- |
| Hubble CI | `pull_request` | 1 | [29145996476](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996476) |
| Java Client CI | `pull_request` | 1 | [29145996477](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996477) |
| Loader CI | `pull_request` | 2 | [29145996501](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996501) |
| Tools CI | `pull_request` | 1 | [29145996467](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996467) |
| Go Client CI | `pull_request` | 1 | [29145996480](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996480) |
| Spark Connector CI | `pull_request` | 1 | [29145996481](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996481) |
| License header + dependency | `pull_request` | 1 | [29145996488](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145996488) |
| PR labeler | `pull_request_target` | 1 | [29145995709](https://github.com/hugegraph/hugegraph-toolchain/actions/runs/29145995709) |

CodeRabbit also reported SUCCESS on the same PR head. GitHub reported 10 successful, 0 failing, 0 pending checks.
The successful Hubble/Loader runs did not publish downloadable Actions artifacts; their authoritative evidence is the
run/job log. Hubble CI itself rebuilt the distribution, audited checksum/signature/package contents, ran 111 backend
tests and executed the real issue-694 package acceptance.

Loader attempt 1 failed only `KafkaLoadTest.testNumberToStringInKafkaSource` with expected 7 / actual 5 after the
other four profiles passed. The immediately preceding product head had a successful Loader run, and the new commit
was license-comment-only. A single finite failed-job retry on the same head passed the entire workflow; there was no
second occurrence and no assertion or test was weakened.

## Independent review

Initial review by `/root/final_independent_reviewer` covered committed range `fd64a83b..03285ac1`, unstaged TODO/CI
evidence and ignored structured evidence. It found no Critical issues and one Important issue: the orphaned PD
`/role/graphspace/:graphspace/:role` deep link remained reachable, was omitted from the final route matrix and rendered
an incompletely localized RoleAuth page. Assessment: **With fixes**.

The route now replace-redirects to `/navigation`; the unused RoleAuth route import was removed. TDD reproduced the
old behavior (12/13 route tests, RoleAuth rendered) and passed after the fix (13/13). Full Jest passed 40 suites/148
tests, scoped ESLint and i18n passed, `CI=true` production build passed, and a fresh package/distribution audit passed.
Final package SHA-256 is `687f7936f0bc3d4d66bdad99ea762efb82e398f36ed68597e18fae82bc20aa7f`, FE asset
`main.4ccb11e5.js`. Real PD Chrome direct navigation ended at `/navigation` with no RoleAuth, HTTP error, unexpected
404, render failure or product-controlled Chinese. Evidence:
[`chrome-role-deep-link.json`](2026-07-11-final-role-redirect/chrome-role-deep-link.json).

Targeted re-review by the same reviewer `/root/final_independent_reviewer`: **passed**. The reviewer independently
reran the 13/13 route-guard suite, confirmed the original Important finding resolved, found no Critical, Important
or Minor issue, and found no new actionable finding. Final assessment for the affected fix: **Ready to merge**,
subject to submitting the worktree and obtaining remote checks for the new head.
