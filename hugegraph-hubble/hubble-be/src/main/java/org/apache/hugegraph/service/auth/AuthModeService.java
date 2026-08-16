/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.service.auth;

import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.options.HubbleOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Single boundary for Hubble authentication mode. Business controllers should
 * not infer mode from sessions or PD settings.
 */
@Service
public final class AuthModeService {

    private final HugeConfig config;

    @Autowired
    public AuthModeService(HugeConfig config) {
        this.config = config;
    }

    public boolean enabled() {
        return !Boolean.FALSE.equals(
                this.config.get(HubbleOptions.AUTH_ENABLED));
    }

    public boolean anonymous() {
        return !this.enabled();
    }
}
