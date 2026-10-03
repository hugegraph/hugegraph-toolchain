#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Validate the same-source SDK before and after a Docker product build."""

import argparse
import hashlib
import json
from pathlib import Path

REPOSITORY = "hugegraph/hugegraph"
COMMIT = "4c162f539b906fa06dd83228e3691b77fa5b77d7"
REQUIRED_MODULES = {
    "pom.xml", "hugegraph-commons/pom.xml", "hugegraph-server/pom.xml",
    "hugegraph-pd/pom.xml", "hugegraph-store/pom.xml",
    "hugegraph-commons/hugegraph-common/pom.xml",
    "hugegraph-server/hugegraph-core/pom.xml", "hugegraph-struct/pom.xml",
    "hugegraph-pd/hg-pd-common/pom.xml", "hugegraph-pd/hg-pd-client/pom.xml",
    "hugegraph-pd/hg-pd-grpc/pom.xml", "hugegraph-store/hg-store-common/pom.xml",
    "hugegraph-store/hg-store-client/pom.xml", "hugegraph-store/hg-store-grpc/pom.xml",
}


def digest(path):
    checksum = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def validate_sdk(repository):
    repository = Path(repository)
    manifest = json.loads((repository / "candidate-sdk-manifest.json").read_text())
    if (manifest["repository"], manifest["commit"]) != (REPOSITORY, COMMIT):
        raise RuntimeError("SDK source does not match the locked candidate")
    if manifest["source_revision"] != "1.7.0" or int(manifest["java_version"]) < 17:
        raise RuntimeError("SDK revision or Java version does not match the candidate contract")
    modules = manifest["required_sdk_modules"]
    if len(modules) != len(REQUIRED_MODULES) or {m["source_pom"] for m in modules} != REQUIRED_MODULES:
        raise RuntimeError("Missing or duplicate required same-source SDK module")
    artifacts = {a["path"]: a for a in manifest["artifacts"]}
    if len(artifacts) != len(manifest["artifacts"]):
        raise RuntimeError("Duplicate SDK artifact paths")
    required_files = set()
    for module in modules:
        if module["group_id"] != "org.apache.hugegraph" or module["version"] != "1.7.0":
            raise RuntimeError("Unexpected required SDK coordinates")
        artifact = module["artifact_id"]
        source_pom = module["source_pom"]
        expected_artifact = Path(source_pom).parent.name if source_pom != "pom.xml" else "hugegraph"
        parent = source_pom in {"pom.xml", "hugegraph-commons/pom.xml", "hugegraph-server/pom.xml",
                                "hugegraph-pd/pom.xml", "hugegraph-store/pom.xml"}
        if artifact != expected_artifact or module["packaging"] != ("pom" if parent else "jar"):
            raise RuntimeError("Required SDK module does not match its source coordinates")
        extensions = {"pom", "jar"} if module["packaging"] == "jar" else {"pom"}
        expected = {f"org/apache/hugegraph/{artifact}/1.7.0/{artifact}-1.7.0.{ext}"
                    for ext in extensions}
        if set(module["files"]) != expected:
            raise RuntimeError("Required SDK module has missing or unexpected files")
        required_files.update(expected)
    jars = {}
    for relative, item in artifacts.items():
        path = repository / relative
        if (Path(relative).is_absolute() or ".." in Path(relative).parts or
                not relative.startswith("org/apache/hugegraph/") or
                not path.is_file() or not path.resolve().is_relative_to(repository.resolve())):
            raise RuntimeError(f"Invalid SDK artifact path: {relative}")
        if digest(path) != item["sha256"]:
            raise RuntimeError(f"SDK artifact hash mismatch: {relative}")
        origins = path.parent / "_remote.repositories"
        installed = origins.is_file() and f"{path.name}>=" in origins.read_text().splitlines()
        if relative in required_files and (not item["source_reactor_install"] or not installed):
            raise RuntimeError(f"Required SDK artifact was not installed from source: {relative}")
        if path.suffix == ".jar" and item["source_reactor_install"]:
            if path.name in jars:
                raise RuntimeError(f"Duplicate candidate JAR name: {path.name}")
            jars[path.name] = item["sha256"]
    if not required_files.issubset(artifacts):
        raise RuntimeError("Required SDK artifact is absent from the manifest")
    return manifest, jars


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    args = parser.parse_args()
    manifest, jars = validate_sdk(args.repository)
    print(f"Verified {manifest['repository']}@{manifest['commit']}: {len(jars)} candidate JARs")
