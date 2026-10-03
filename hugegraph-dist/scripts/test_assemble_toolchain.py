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
"""Verify the aggregate distribution preserves previously used release trees."""

import io
import pathlib
import subprocess
import sys
import tarfile
import tempfile
import unittest

ASSEMBLER = pathlib.Path(__file__).with_name("assemble-toolchain.sh")


class ToolchainAssemblyTest(unittest.TestCase):
    def test_archives_are_used_without_moving_runtime_data(self):
        with tempfile.TemporaryDirectory(prefix="toolchain-assembly-") as directory:
            root = pathlib.Path(directory)
            (root / "pom.xml").write_text("<project/>")
            docs = root / "hugegraph-dist/release-docs"
            docs.mkdir(parents=True)
            (docs / "LICENSE").write_text("license fixture")
            (docs / "._LICENSE").write_bytes(b"existing documentation metadata")
            if sys.platform == "darwin":
                subprocess.run(["xattr", "-w", "com.apple.metadata:hugegraph-archive-test",
                                "documentation attribute", str(docs / "LICENSE")], check=True)
            sentinels = []
            for module in ("hubble", "loader", "tools"):
                name = f"apache-hugegraph-{module}-1.8.0"
                existing = root / f"hugegraph-{module}" / name / "data"
                existing.mkdir(parents=True)
                sentinel = existing / "user.mv.db"
                sentinel.write_bytes(b"preserve runtime data")
                sentinels.append(sentinel)
                target = root / f"hugegraph-{module}/target"
                target.mkdir()
                with tarfile.open(target / f"{name}.tar.gz", "w:gz") as package:
                    data = module.encode()
                    member = tarfile.TarInfo(f"{name}/lib/app.jar")
                    member.size = len(data)
                    package.addfile(member, io.BytesIO(data))
                    sidecar = tarfile.TarInfo(f"{name}/lib/._app.jar")
                    sidecar.size = len(data)
                    package.addfile(sidecar, io.BytesIO(data))
            previous = root / "apache-hugegraph-toolchain-1.8.0/user.mv.db"
            previous.parent.mkdir()
            previous.write_bytes(b"preserve runtime data")
            sentinels.append(previous)

            for _ in range(2):
                subprocess.run(["bash", str(ASSEMBLER), str(root), "1.8.0"],
                               check=True, capture_output=True)
                archive = root / "target/apache-hugegraph-toolchain-1.8.0.tar.gz"
                with tarfile.open(archive) as package:
                    names = package.getnames()
                    self.assertFalse(any(name.endswith(".db") for name in names))
                    for module in ("hubble", "loader", "tools"):
                        self.assertIn("apache-hugegraph-toolchain-1.8.0/"
                                      f"apache-hugegraph-{module}-1.8.0/lib/app.jar",
                                      names)
                    self.assertIn("apache-hugegraph-toolchain-1.8.0/LICENSE", names)
                    self.assertFalse(any(pathlib.PurePosixPath(name).name.startswith("._")
                                         for name in names))
                    self.assertEqual(b"license fixture", package.extractfile(
                        "apache-hugegraph-toolchain-1.8.0/LICENSE").read())
                for sentinel in sentinels:
                    self.assertEqual(b"preserve runtime data", sentinel.read_bytes())
                self.assertFalse(list((root / "target").glob("toolchain-stage.*")))

    def test_missing_module_archive_fails(self):
        with tempfile.TemporaryDirectory(prefix="toolchain-assembly-") as directory:
            root = pathlib.Path(directory)
            (root / "pom.xml").write_text("<project/>")
            result = subprocess.run(["bash", str(ASSEMBLER), str(root), "1.8.0"],
                                    capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(list((root / "target").glob("*.tar.gz")))
            self.assertFalse(list((root / "target").glob("toolchain-stage.*")))


if __name__ == "__main__":
    unittest.main()
