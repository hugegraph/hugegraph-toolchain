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

const loadResponseRejectHandler = modulePath => {
    jest.resetModules();

    const responseHandlers = [];
    const messageError = jest.fn();
    const clearLogin = jest.fn();
    const instance = {
        interceptors: {
            request: {
                use: jest.fn(),
            },
            response: {
                use: jest.fn((resolve, reject) => {
                    responseHandlers.push({resolve, reject});
                }),
            },
        },
        get: jest.fn(),
        post: jest.fn(),
        put: jest.fn(),
        delete: jest.fn(),
    };

    jest.doMock('axios', () => ({
        create: jest.fn(() => instance),
    }));
    jest.doMock('antd', () => ({
        message: {
            error: messageError,
        },
    }));
    jest.doMock('../i18n', () => ({
        t: key => key,
    }));
    jest.doMock('../utils/user', () => ({
        clearLogin,
    }));

    require(modulePath);
    return {
        reject: responseHandlers[0].reject,
        messageError,
        clearLogin,
    };
};

describe.each(['./request', './request2'])('%s error semantics', modulePath => {
    beforeEach(() => {
        delete window.location;
        window.location = {
            pathname: '/navigation',
            search: '?from=test',
            href: '',
        };
    });

    afterEach(() => {
        jest.dontMock('axios');
        jest.dontMock('antd');
        jest.dontMock('../i18n');
        jest.dontMock('../utils/user');
        localStorage.clear();
        sessionStorage.clear();
    });

    it('keeps non-401 HTTP errors rejected after showing the error message', async () => {
        const {reject, messageError} = loadResponseRejectHandler(modulePath);
        const error = {
            response: {
                status: 500,
                data: {
                    message: 'boom',
                    path: '/api/v1.3/graphs',
                },
            },
        };

        await expect(reject(error)).rejects.toBe(error);
        expect(messageError).toHaveBeenCalledWith('request.error');
    });

    it('keeps network errors rejected after showing the fallback message', async () => {
        const {reject, messageError} = loadResponseRejectHandler(modulePath);
        const error = new Error('Network Error');

        await expect(reject(error)).rejects.toBe(error);
        expect(messageError).toHaveBeenCalledWith('request.error');
    });

    it('keeps the existing 401 sentinel response and redirects to login', () => {
        const {reject, clearLogin} = loadResponseRejectHandler(modulePath);
        const error = {
            response: {
                status: 401,
                data: {
                    status: 401,
                    message: 'Unauthorized',
                },
            },
        };

        expect(reject(error)).toEqual({
            data: {
                status: 401,
                message: 'Unauthorized',
            },
        });
        expect(clearLogin).toHaveBeenCalledTimes(1);
        expect(sessionStorage.getItem('redirect')).toBe('/navigation?from=test');
        expect(window.location.href).toBe('/login?redirect=%2Fnavigation%3Ffrom%3Dtest');
    });
});
