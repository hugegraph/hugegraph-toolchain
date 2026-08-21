/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hugegraph.handler;

import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.exception.UnauthorizedException;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        boolean authenticated =
                this.hasTextSessionAttribute(request, Constant.TOKEN_KEY) &&
                this.hasTextSessionAttribute(request, Constant.USERNAME_KEY);
        boolean anonymous =
                Boolean.TRUE.equals(request.getSession().getAttribute(
                                    Constant.ANONYMOUS_KEY)) &&
                Constant.ANONYMOUS_USER.equals(request.getSession()
                                                       .getAttribute(
                                                       Constant.USERNAME_KEY));
        if (!authenticated && !anonymous) {
            throw new UnauthorizedException();
        }

        return true;
    }

    private boolean hasTextSessionAttribute(HttpServletRequest request,
                                            String key) {
        Object value = request.getSession().getAttribute(key);
        return value instanceof String && StringUtils.hasText((String) value);
    }
}
