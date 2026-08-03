#!/bin/bash
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
HOME_PATH=$(dirname "$0")
HOME_PATH=$(cd "${HOME_PATH}"/.. && pwd)
BIN_PATH=${HOME_PATH}/bin
PID_FILE=${BIN_PATH}/pid
# Seconds to wait for a graceful shutdown before resorting to SIGKILL
STOP_TIMEOUT=${STOP_TIMEOUT:-30}

if [[ -f ${PID_FILE} ]]; then
    pid=$(cat "${PID_FILE}")
    if kill -0 "${pid}" > /dev/null 2>&1; then
        # SIGTERM first so the Spring shutdown hooks run: they pause the
        # running load tasks and close the H2 database cleanly. Only escalate
        # to SIGKILL if the process is still alive after STOP_TIMEOUT.
        kill "${pid}" > /dev/null 2>&1
        waited=0
        while [ "${waited}" -lt "${STOP_TIMEOUT}" ]; do
            if ! kill -0 "${pid}" > /dev/null 2>&1; then
                break
            fi
            sleep 1
            waited=$((waited + 1))
        done
        if kill -0 "${pid}" > /dev/null 2>&1; then
            echo "HugeGraphHubble did not exit within ${STOP_TIMEOUT}s, killing it"
            kill -9 "${pid}" > /dev/null 2>&1
        fi
        echo "stopped HugeGraphHubble"
    else
        echo "process ${pid} not exist"
    fi
    rm "${PID_FILE}"
else
    echo "HugeGraphHubble not running"
fi
