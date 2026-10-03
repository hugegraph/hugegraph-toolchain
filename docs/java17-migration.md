# Java 17 migration and candidate validation

Toolchain 1.8 requires Java 17 for the Java Client, ordinary Loader, Tools and
Hubble. Select Java 17 for applications and for every Spark driver and executor
using the new Client. An older server can keep its own supported JVM.

## Build the locked candidate

The Java 17 server candidate retains the Maven version `1.7.0`. Its Common, PD,
gRPC and Store artifacts must come from the same source commit; Maven Central
artifacts with that version are not interchangeable with the candidate.
The locked source is `hugegraph/hugegraph`, branch `codex/cypher-minimal-compat`,
commit `4c162f539b906fa06dd83228e3691b77fa5b77d7`.

Use an explicit JDK directory and check both Java and Maven. On systems where
Java 17 is not registered, a system JDK selector can return another installed
version. Checking the generated class version alone does not identify the JVM
that built or tested it.

From the Toolchain repository root:

```bash
export JAVA_HOME=/absolute/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
mvn -version

toolchain_root="$PWD"
candidate_dir=$(mktemp -d /tmp/hugegraph-java17.XXXXXX)
mkdir "$candidate_dir/m2"
export SERVER_REPOSITORY=hugegraph/hugegraph
export SERVER_FETCH_REF=codex/cypher-minimal-compat
server_commit=4c162f539b906fa06dd83228e3691b77fa5b77d7

bash "$toolchain_root/hugegraph-client/assembly/travis/install-candidate-sdk.sh" \
  "$server_commit" "$candidate_dir/server" "$candidate_dir/m2"
mvn -Dmaven.repo.local="$candidate_dir/m2" clean install \
  -DskipTests -Dmaven.javadoc.skip=true -ntp
```

The SDK helper installs the complete locked server reactor without starting a
server. It requires a new external source directory and a dedicated Maven
repository with no existing HugeGraph artifacts. It records source and artifact
provenance in `candidate-sdk-manifest.json` inside that repository. Reuse the
same explicit `-Dmaven.repo.local` option for subsequent Toolchain validation.
The package command skips test execution; run the module test suites separately.
These source-built packages are candidates, not an ASF release.

The container changes are delivered separately in
[PR #37](https://github.com/hugegraph/hugegraph-toolchain/pull/37).
Apply that change with this cutover before building Loader or Hubble images. Both
container build stages install and verify the locked SDK in an isolated Maven
repository before packaging; their build and runtime JVMs use Java 17.

## Upgrade the core tools

Keep the previous distribution, configuration, metadata and graph data.
Extract the new distribution to a separate directory, select Java 17 and check
its connection settings against a disposable graph before switching jobs.

Loader retains its `bin/hugegraph-loader.sh` entry point. From the extracted
distribution, the bundled file example is:

```bash
bash bin/hugegraph-loader.sh -g hugegraph \
  -f example/file/struct.json -s example/file/schema.groovy
```

The Loader and Hubble launchers supply the scoped `java.net` opening required
by Hive ORC. Custom launch commands need the same option; do not add it to
unrelated JVMs. MySQL, HDFS and Kafka remain ordinary Loader sources.

Tools retains `bin/hugegraph` with `backup` and `restore`. Keep old backups and
restore into a separate graph before comparing schema, vertices, edges and
property values. Both ZIP and plain JSON backups are supported, including HDFS.
Restoring a plain HDFS backup no longer interprets each JSON file as a ZIP.

## Hubble metadata

Hubble uses Spring Boot 3, Jakarta APIs and H2 metadata. Start with a new
metadata database; the default is
`jdbc:h2:file:./data/hubble-v2;DB_CLOSE_ON_EXIT=FALSE`.
Existing databases are checked read-only before schema initialization. A database
without this release's schema marker is rejected without changing its data.
There is no automatic migration or deletion of an old database; retain it with
its matching Hubble distribution. MySQL remains a Loader source.

Retain the new database and configuration across restarts. Local H2 file URL
shorthand and encrypted files are supported; `INIT` settings are rejected before
opening the database. The full initial schema includes the release marker.
Packaging excludes runtime data from archives without deleting local data.
See the [Hubble guide](../hugegraph-hubble/README.md) for configuration and UI
validation evidence.

## Compatibility and peripheral engines

Server 1.7 support is required for all four core tools. Hubble also retains core
graph, schema and data operations on standalone Server 1.5; 1.5 GraphSpace and PD
management are outside that support boundary. New Client/Loader/Tools results
against 1.5 and original Client 1.5/1.7 results against the candidate are recorded
separately, including failures.

The Spark Connector uses Spark 3.5.8 and Scala 2.12.18. Spark supplies its logging
provider; the connector assembly does not bundle a competing SLF4J provider.
Its [guide](../hugegraph-spark-connector/README.md) specifies driver and executor
Guava paths and the tested client deploy mode. Cluster deploy mode requires a
Guava path accessible to the remote driver and separate environment validation.
A successful connector sink job does not establish Spark Loader or Flink CDC
compatibility.

Spark Loader API writes, HBase bulkload, and Flink CDC require their own execution
results. Failed peripheral modes may retain their previous released package
with a documented limitation; they do not disable ordinary Loader sources.
Do not use a new Java 11 variant as a substitute for the Java 17 core tools.

## Validation boundary

The module preparation PRs were tested with unmodified published HugeGraph 1.7
SDK artifacts. Those checks establish the module changes before this candidate
cutover; they do not replace validation against the locked candidate SDK.

The cutover requires fresh core build/test and package evidence, the released
1.7 and Hubble 1.5 support checks, actual browser flows, isolated engine jobs,
and exact runtime dependency/license coverage. Each result must identify the
Toolchain source, server source, test JVM and artifact origin. Pending checks
remain pending; class versions, mocked tests and successful packaging alone do
not establish runtime support. Final results and remaining RC gates accompany
the delivered PRs. Merging and formal release are subsequent steps.
