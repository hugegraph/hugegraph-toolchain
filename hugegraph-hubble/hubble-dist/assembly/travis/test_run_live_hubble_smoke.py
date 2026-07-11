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

import importlib.util
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).with_name("run_live_hubble_smoke.py")
SPEC = importlib.util.spec_from_file_location("run_live_hubble_smoke", SCRIPT)
SMOKE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SMOKE)


class HubbleReadinessTest(unittest.TestCase):

    def test_readiness_rejects_spa_html(self):
        self.assertFalse(SMOKE.is_hubble_readiness_response(
            '<div id="root"></div>'))

    def test_readiness_requires_about_response_shape(self):
        self.assertFalse(SMOKE.is_hubble_readiness_response(
            {"status": 200, "data": {"name": "HugeGraph-Hubble"}}))
        self.assertTrue(SMOKE.is_hubble_readiness_response({
            "status": 200,
            "data": {"name": "HugeGraph-Hubble", "version": "3.0.0"}
        }))

    @mock.patch.object(SMOKE, "request")
    def test_preflight_uses_strict_about_api(self, request):
        request.return_value = '<div id="root"></div>'

        self.assertFalse(SMOKE.is_healthy("http://127.0.0.1:8088"))
        request.assert_called_once_with(
            "GET", "http://127.0.0.1:8088/about", timeout=2)


class AnalysisBoundaryTest(unittest.TestCase):

    @mock.patch.object(SMOKE, "get_json_status", side_effect=[400, 400])
    def test_environment_dependent_boundaries_are_skipped(self, _status):
        checks = SMOKE.run_analysis_boundary_checks(
            "http://127.0.0.1:8088", "DEFAULT", "hugegraph")

        self.assertEqual(["skipped", "skipped"],
                         [check["status"] for check in checks])
        self.assertEqual([400, 400],
                         [check["http_or_business_status"]
                          for check in checks])

    @mock.patch.object(SMOKE, "get_json_status", side_effect=[200, 503])
    def test_service_failure_is_not_skipped(self, _status):
        with self.assertRaisesRegex(RuntimeError,
                                    "analysis-olap-boundary failed: status 503"):
            SMOKE.run_analysis_boundary_checks(
                "http://127.0.0.1:8088", "DEFAULT", "hugegraph")

    @mock.patch.object(SMOKE, "get_json_status", return_value=200)
    def test_available_boundaries_pass(self, _status):
        checks = SMOKE.run_analysis_boundary_checks(
            "http://127.0.0.1:8088", "DEFAULT", "hugegraph")

        self.assertEqual(["passed", "passed"],
                         [check["status"] for check in checks])


if __name__ == "__main__":
    unittest.main()
