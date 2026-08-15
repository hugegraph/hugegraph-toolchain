/*
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

package org.apache.hugegraph.driver;

import org.apache.hugegraph.util.VersionUtil;

/**
 * Small compatibility boundary shared by Hubble and clients.
 *
 * <p>Version checks belong here so callers can express capabilities instead
 * of branching on server versions in controllers or pages. Unknown versions
 * deliberately use the conservative legacy profile.</p>
 */
public final class ServerCompatibility {

    private static final String GRAPHSPACE_MIN_VERSION = "1.7.0";

    private ServerCompatibility() {
    }

    public static Profile profile(String coreVersion) {
        if (coreVersion == null || coreVersion.trim().isEmpty()) {
            return Profile.LEGACY;
        }
        try {
            String normalized = coreVersion.trim();
            return VersionUtil.gte(normalized, GRAPHSPACE_MIN_VERSION) ?
                   Profile.MODERN : Profile.LEGACY;
        } catch (RuntimeException ignored) {
            return Profile.LEGACY;
        }
    }

    public static boolean supportsGraphSpace(String coreVersion) {
        return profile(coreVersion).supportsGraphSpace();
    }

    public enum Profile {
        LEGACY(false),
        MODERN(true);

        private final boolean graphSpace;

        Profile(boolean graphSpace) {
            this.graphSpace = graphSpace;
        }

        public boolean supportsGraphSpace() {
            return this.graphSpace;
        }
    }
}
