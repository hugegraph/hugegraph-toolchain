# Hubble 2.0 Audit Follow-up

## non-PD My Profile contract

- Date: 2026-07-11 (Asia/Shanghai)
- Environment: isolated non-PD/RocksDB release-package Hubble at `127.0.0.1:38088`
- Browser: user Chrome through the Chrome skill
- Session: real `admin` login; isolated fixture only

Chrome opened `/my`, authenticated, and rendered the English My Profile page. The page used the personal-profile
flow rather than `GET /auth/users/{username}`. It showed public account ID `admin`, nickname `admin_nickname`, and
did not expose the HugeGraph internal ID `-27:admin`.

The nickname was changed to `auditname`, saved, observed on the rendered page, then restored to `admin_nickname` and
observed again after save. The final page had zero visible alerts, no product-controlled Chinese, and no internal ID.
Screenshot: [`nonpd-my-profile.png`](2026-07-11-audit-followup/nonpd-my-profile.png).

Conclusion: the original `GET /auth/users/admin` reproduction mixed a username with an ID endpoint and does not
represent the non-PD My Profile product flow. No P0 personal-profile defect was reproduced. The remaining ID versus
username API contract is a P2 documentation/compatibility concern.

## Targeted RED/GREEN

- `ConsolePrintTest` RED found eight `printStackTrace()` calls in Hubble backend main sources. After replacement with
  structured logging and correct interrupt restoration, the same Java 11 `mvnd` test passed 1/1.
- `AuthSecurityTest#testMissingRequestParameterUsesActionableHttp400` RED failed because
  `ExceptionAdvisor` had no handler for `MissingServletRequestParameterException`. After adding the handler and
  English/Chinese resource messages, the targeted Java 11 `mvnd` batch passed 2/2 together with `ConsolePrintTest`.
- Commands used Temurin 11.0.29 and `/opt/homebrew/Cellar/mvnd@1/1.0.2/bin/mvnd`; no test, assertion, or exit code was
  skipped or ignored.

### Error-contract follow-up batch

- `AuthSecurityTest` RED ran 21 tests and failed exactly two new assertions: exception responses exposed
  `RuntimeException: internal-secret`, and `DELETE /auth/users/super/{id}` swallowed a simulated Server mutation
  failure. After the fix, the expanded suite passed 22/22, including the new PD/HStore dependency contract:
  HTTP/business 503, localized actionable message, and `cause=null`.
- `UserImportSafetyTest` RED ran 2 tests: temporary-file failure returned null and a null CSV caused an NPE instead
  of an actionable exception. After the fix, the combined `AuthSecurityTest,UserImportSafetyTest` batch passed
  24/24 in 14.845s. Temporary user-import files are now removed in `finally`; preparation and parsing failures no
  longer continue into a partial or null mutation.
- The first test attempt had an ambiguous Mockito overload and failed test compilation; it was corrected to target
  `transferTo(File)` before collecting behavioral RED evidence. This setup failure is not counted as a behavioral
  RED or passing gate.

## Audit cleanup batch

- Full Hubble backend unit profile after the batch: 112 tests, 0 failure/error/skip, BUILD SUCCESS in 16.452s.
- Frontend `GraphDetail/index.test.js`: 2/2 passed; scoped `eslint src/api/manage.js`: exit 0.
- Hubble backend main sources contain zero `printStackTrace()` calls, enforced by `ConsolePrintTest` in the full suite.
- The unused graph-storage frontend facade and commented backend endpoint were removed. The fully commented,
  unreachable Audit controller was removed; the accepted design records that Audit is not a Hubble 2.0 published
  surface. No replacement response or fake capability was introduced.
- The non-PD GraphSpace list/management distinction and PD/HStore external status dependencies are recorded in the
  accepted design; exact Server dependency TODOs are present at both Hubble call sites.

## Final follow-up package

- Package SHA-256: `57876e7c43a671a7f3b9c26ecbf5f04cb57040173ca523d829b08b2b00173021`
- Frontend asset: `main.7d134651.js`
- Build: Java 11 `mvnd package -DskipTests -Dmaven.javadoc.skip=true -ntp`, BUILD SUCCESS in 1m49s.
- Distribution audit: 392 JARs, 275 license files, 43 frontend license files, 10 native-bearing JARs.
- Full frontend Jest: 40 suites / 148 tests passed; i18n 1585/1585 and 1106 static keys; scoped ESLint passed.

The package ran against the isolated non-PD/RocksDB Server on `127.0.0.1:8080` with Hubble on
`127.0.0.1:38088`. Authenticated English REST without `type` returned HTTP 400 and exactly
`{"status":400,"message":"The request parameter 'type' is required","cause":null}`. `/vermeer` returned
HTTP/business 200 with `enable=false`; the ordinary graph list still rendered `hugegraph`. Runtime logs contained the
single structured summary `Dashboard Vermeer configuration is unavailable; Vermeer integration is disabled for this
request`, not the former raw HTML response.

Real Chrome opened the English graph edit dialog from this package. The display-name placeholder stated max 48
characters. Nickname `nickname_long1` (longer than the former 12-character limit) saved successfully and appeared on
the graph card, then was restored to `hugegraph`; final visible alerts were zero. Screenshot:
[`final-nickname-restored.png`](2026-07-11-audit-followup/final-nickname-restored.png).

## Final error-contract package

- Date: 2026-07-11 (Asia/Shanghai)
- Toolchain worktree: branch `hubble2`, pre-commit base `1b78c9d220d4ba1d2a877b8d1086d489eafb9d4a`
- Java: Temurin 11; build runner: mvnd 1.0.2 daemon
- Full backend: `mvnd test -P unit-test -pl hugegraph-hubble/hubble-be -ntp`, 117 tests,
  0 failure/error/skip, BUILD SUCCESS in 16.810s
- Real package: run from `hugegraph-hubble/` with
  `mvnd package -DskipTests -Dmaven.javadoc.skip=true -ntp`, all three reactor modules SUCCESS in 1m59s
- Package SHA-256: `2d98000636488f448ac38b269a244945131e51ae8f766075c6b0c49ffba59a3e`
- Frontend asset: `main.7d134651.js`; i18n 1585/1585 and 1106 static keys
- Distribution audit: 392 JARs, 275 license files, 43 frontend license files, 10 native-bearing JARs

The exact package was extracted separately for both modes:

- non-PD/RocksDB: Hubble `127.0.0.1:38088`, `pd.enabled=false`, Server `127.0.0.1:8080`, Hubble PID 39013,
  runtime `/private/tmp/hubble2-final-release.m6Vc8o/apache-hugegraph-hubble-1.8.0`;
- PD/HStore: Hubble `127.0.0.1:38089`, `pd.enabled=true`, PD peers `127.0.0.1:18686`, PD server
  `127.0.0.1:18620`, Server `127.0.0.1:18080`, Hubble PID 41615, runtime
  `/private/tmp/hubble2-final-pd-release.YjxDw2/apache-hugegraph-hubble-1.8.0`.

Authenticated REST with `Accept-Language: en-US` returned identically in both modes:

- `GET /api/v1.3/pds/status`: HTTP 503, business 503,
  `PD status is unavailable because this HugeGraph Server does not expose the required API`, `cause=null`;
- `GET /api/v1.3/services/storage/status`: HTTP 503, business 503,
  `HStore status is unavailable because this HugeGraph Server does not expose the required API`, `cause=null`.

The non-PD missing-parameter case remained HTTP/business 400 with
`The request parameter 'type' is required` and `cause=null`. No endpoint synthesized healthy status or returned a
downstream stack/HTML body.

Chrome used the same package in English mode. non-PD login rendered the full navigation, `/my` showed account ID
`admin` and nickname `admin_nickname` without an internal ID, and `/graphspace/DEFAULT` rendered `hugegraph` with no
visible product alert. PD login rendered the PD navigation and `/graphspace` rendered the isolated `DEFAULT` fixture.
The visible Chinese `超级管理员`/`默认图空间` values are fixture data returned by the external Server, not
product-controlled interface copy. Chrome dev logs contained only the known extension message
`Could not establish connection. Receiving end does not exist`; product-origin fatal console errors were zero.
Screenshots: [`nonpd-my-profile.png`](2026-07-11-audit-followup/nonpd-my-profile.png),
[`final-nickname-restored.png`](2026-07-11-audit-followup/final-nickname-restored.png), and
[`final-pd-graphspace.png`](2026-07-11-audit-followup/final-pd-graphspace.png).

## Independent review follow-up

Independent read-only reviewer `/root/final_followup_reviewer` reviewed the complete base
`1b78c9d220d4ba1d2a877b8d1086d489eafb9d4a` to working-tree diff, ignored evidence and original screenshots. The
first review found one Important issue: a null/extensionless original CSV filename bypassed the localized error, and
a `transferTo(File)` failure could leave the already-created temporary file behind.

The reviewer finding received its own RED/GREEN cycle. Before the fix, `UserImportSafetyTest` ran 4 tests with one
failure and two errors: the created temp file still existed, a null original filename threw NPE, and an extensionless
name threw `StringIndexOutOfBoundsException`. The fix uses a constant safe prefix, a bounded allowlisted extension
or `.tmp`, and retains/deletes the incomplete temp file before throwing the localized exception. Targeted GREEN was
4/4 in 11.806s; the fresh full Hubble backend then passed 119/119, 0 failure/error/skip, in 15.833s.

The same reviewer performed the mandatory targeted re-review and returned PASS: the Important finding was fully
resolved, no new path-injection, resource, security or API compatibility issue was introduced, and no actionable
findings remain.

After re-review, the final package was rebuilt from `hugegraph-hubble/` with the same Java 11 mvnd command. All
Hubble/hubble-be/hubble-dist modules passed in 1m56s; distribution counts remained 392/275/43/10 and the unchanged
frontend asset remained `main.7d134651.js`. The post-review tar SHA-256 is
`77a3c7aacc43d4eef7afc96dc3397571d90f8bff0146e82138f47c40b371ff77`. This exact tar was extracted to
`/private/tmp/hubble2-review-final.OJNOh9/apache-hugegraph-hubble-1.8.0`, started as PID 53049 on non-PD port
38088, authenticated successfully, and returned the expected English HTTP/business 503 PD status with `cause=null`.
The reviewer fix has no frontend consumer or rendered UI change, so the immediately preceding Chrome acceptance of
the identical `main.7d134651.js` asset remains the final UI evidence.
