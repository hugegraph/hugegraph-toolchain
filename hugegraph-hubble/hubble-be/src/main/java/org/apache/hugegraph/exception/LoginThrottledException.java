/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * Licensed under the Apache License, Version 2.0.
 */

package org.apache.hugegraph.exception;

import lombok.Getter;

@Getter
public class LoginThrottledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long retrySeconds;

    public LoginThrottledException(long retrySeconds) {
        super("auth.login.throttled");
        this.retrySeconds = retrySeconds;
    }
}
