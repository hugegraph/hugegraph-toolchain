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

import tempfile
import unittest
from pathlib import Path

from diagnose_hubble_servers import redact, tail


class ServerLogRedactionTest(unittest.TestCase):

    def test_removes_credentials_and_multiline_private_key(self):
        log = """Started HugeGraphServer
Authorization: Basic canary-basic
Cookie: session=canary-cookie
Picked up JAVA_TOOL_OPTIONS: -Dcustom=canary-env
{"password":"canary-password"}
auth.admin_pa=canary-admin
http://user:canary-url@localhost:8080
"token":
  "canary-next-line"
-----BEGIN PRIVATE KEY-----
canary-key-body
-----END PRIVATE KEY-----
java.lang.OutOfMemoryError: Java heap space
    at org.apache.hugegraph.Test.run(Test.java:1)
"""
        cleaned = redact(log)
        self.assertNotIn("canary", cleaned)
        self.assertIn("Started HugeGraphServer", cleaned)
        self.assertIn("OutOfMemoryError", cleaned)
        self.assertIn("Test.java:1", cleaned)
    def test_bounded_tail_discards_partial_sensitive_line(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "server.log"
            log.write_text("password=" + "canary" * 12000 +
                           "\njava.lang.OutOfMemoryError: heap\n", encoding="utf-8")
            cleaned = tail(log)
            self.assertNotIn("canary", cleaned)
            self.assertIn("OutOfMemoryError", cleaned)



if __name__ == "__main__":
    unittest.main()
