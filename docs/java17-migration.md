# Java 17 migration and validation

Toolchain 1.8 builds and runs on Java 17. Set `JAVA_HOME` for the client
application, Loader, Tools, Hubble, and Spark driver/executor processes. The
server JVM is independent: connecting to an older server does not require
running the new tools on that server's Java version.

## Upgrade procedure

1. Keep the previous distribution, configuration, and data before extracting
   the new distribution into a separate directory.
2. Select Java 17 and check `java -version`. For a candidate source build, first
   install its same-source dependencies as shown below, then build Toolchain.
3. Review connection and authentication settings, then validate against a
   disposable graph before switching production jobs.

The candidate dependencies retain released Maven version numbers. Resolving
them directly from Central can silently substitute the older Java 11 artifacts.
With Maven 3.9.11 and Java 17 selected, run this from the Toolchain repository
root. Both builds share a fresh Maven repository; the server checkout stays
outside the product tree. The commit matches the candidate pinned in CI.

```bash
toolchain_root=$(pwd)
candidate_dir=$(mktemp -d /tmp/hugegraph-java17.XXXXXX)
export MAVEN_ARGS="-Dmaven.repo.local=$candidate_dir/m2"
export SERVER_REPOSITORY=hugegraph/hugegraph
export SERVER_FETCH_REF=codex/cypher-minimal-compat
server_commit=59ad5374b4bf90ec3ee7aa133b830e66788b51ec
(cd "$candidate_dir" && bash \
  "$toolchain_root/hugegraph-client/assembly/travis/install-hugegraph-from-source.sh" \
  "$server_commit" --build-only) && \
mvn clean install -DskipTests -Dmaven.javadoc.skip=true -ntp
```

These commands build packages without running tests or starting a server. Keep
`MAVEN_ARGS` set for subsequent validation of the same candidate artifacts.

In an extracted Loader distribution, the entry point remains
`bin/hugegraph-loader.sh`. The bundled file example can be run from that
distribution directory:

```bash
bash bin/hugegraph-loader.sh -g hugegraph \
  -f example/file/struct.json -s example/file/schema.groovy
```

In an extracted Tools distribution, use `bin/hugegraph` with the `backup` or
`restore` subcommand. Preserve existing backup files and restore into a separate
graph before comparing schema, vertices, edges, and property values. Both ZIP
and plain JSON backup paths, including HDFS, have been exercised on Java 17.
Backups produced by released Tools 1.7 on Java 11 were also restored by the new
Tools against the candidate server.

Use the packaged launchers for Loader and Hubble. Their scoped
`--add-opens=java.base/java.net=ALL-UNNAMED` supports the embedded Hive ORC
reader; custom launch commands need the same option. Do not apply it globally
to unrelated JVMs.

## Hubble metadata and compatibility

Hubble uses Spring Boot 3 and H2 2.x. Start with a **new metadata database**;
the default is `jdbc:h2:file:./data/hubble-v2;DB_CLOSE_ON_EXIT=FALSE`. Existing
Hubble databases are not automatically migrated, deleted, or rewritten. Keep
the old database with its matching Hubble release. MySQL is still a Loader
source, but is no longer a Hubble metadata store.

Keep the new H2 data directory across Hubble restarts. Release packaging excludes
runtime data without deleting it from the source directory. Backend compilation
and the rebuilt distribution use the same Elasticsearch 7.16.1 clients and
Caffeine 2.8.0; the Java upgrade does not upgrade the external Elasticsearch
protocol.

The intended Hubble compatibility boundary is the modern GraphSpace/auth API on
1.8, a thin legacy-response fallback on 1.7, and core graph/schema/data operations
on standalone 1.5. GraphSpace management and PD mode are not supported for 1.5.
See the [Hubble guide and UI evidence](../hugegraph-hubble/README.md#java-17-ui-validation).

## Validation status

This is the local validation snapshot for 2026-10-03, not a blanket support
claim. “Candidate” means the pinned Java 17 server and its same-source Common/PD
dependencies used by CI. The local Server 1.7 and 1.5 services were built from
their official release tags and ran on Java 11; the separate 1.7 CI baseline
downloads and verifies the published binary archive.

| Area | Passed locally | Remaining validation |
| --- | --- | --- |
| Java Client | Unit tests; API, Cypher and functional suites against candidate and released 1.7; 1.5 graph/schema/data, batch, pagination, Unicode/type round trips, Gremlin and error parsing; candidate PD discovery and HStore CRUD | Candidate HStore Gremlin response handling after legacy requests is under investigation |
| Loader | Unit tests; file and real HDFS suites against candidate and released 1.7; basic CSV/Groovy import against anonymous 1.5 with the updated Client | JDBC/Kafka integration and actual Docker image gate |
| Tools | Functional suites and local/HDFS ZIP/plain round trips against candidate and released 1.7; restore of old Tools backups; label-scoped ZIP/plain data round trips against anonymous 1.5 with the updated Client | Final integrated CI |
| Spark Connector | Spark 3.5/Scala 2.12 suite; real writes/readback against candidate with a separate Java 17 executor | Final packaged-artifact and integrated CI gates |
| Hubble | Backend tests with the final same-source dependencies and default US-ASCII charset; frontend tests and production UI build; packaged standalone 1.5 anonymous navigation, graph/sample creation, schema/query views, and favorite retained after process restart | Final aligned-package 1.7/candidate UI flows, PD mode, and H2 persistence across Docker container recreation |
| Packaging | Full reactor build, Checkstyle, rebuilt Hubble dependency alignment, dependency inventory check | Final-head CI and actual Loader/Hubble Docker builds |

Original Client 1.5 on Java 8 and 1.7 on Java 11 were tested with their original
dependencies against the candidate HStore server. Batch operations, pagination,
numeric/boolean round trips and error parsing passed; Unicode was corrupted
under the test JVMs' default US-ASCII charset. Their Gremlin probes also failed.
New Client 1.8 preserved Unicode, but a later Gremlin request received a reference
count error after legacy requests; that candidate-server issue remains under
investigation. Original Client 1.7 also passed a separate PD-discovery/factory
CRUD probe. Client 1.5 has no corresponding PD entry point.

Spark Connector validation does not cover the separate Spark Loader or Flink CDC
execution modes. Hubble's 1.5 screenshots were collected before the final
distribution dependency alignment; they document that UI session and do not
replace the pending final-package checks. Use the module READMEs and CI suites
for repeatable validation commands; run destructive graph tests only on isolated
test services.
