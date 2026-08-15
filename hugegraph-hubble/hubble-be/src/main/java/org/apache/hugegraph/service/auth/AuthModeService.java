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
        return this.config.get(HubbleOptions.AUTH_ENABLED);
    }

    public boolean anonymous() {
        return !this.enabled();
    }
}
