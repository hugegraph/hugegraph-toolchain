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

import {act, renderHook} from '@testing-library/react';
import {
    SESSION_ACTIVITY_PREFIX,
    useSessionIdleTimeout,
} from './useSessionIdleTimeout';
import * as api from '../api';
import * as user from '../utils/user';

jest.mock('../api', () => ({
    auth: {
        getSessionSettings: jest.fn(),
        logout: jest.fn(),
    },
}));
jest.mock('../utils/browserNavigation', () => ({replaceBrowserLocation: jest.fn()}));
jest.mock('../modules/algorithm/algorithmsForm/algorithmFormPersistence', () => ({
    clearPersistedAlgorithmFormsForUser: jest.fn(),
}));

const flush = async () => {
    await act(async () => {});
};

beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    user.endLogoutTransition();
    user.setUser({id: 'one', user_name: 'one'});
    api.auth.getSessionSettings.mockResolvedValue({
        status: 200,
        data: {
            idle_timeout_seconds: 900,
            activity_scope: 'current-scope',
        },
    });
    api.auth.logout.mockResolvedValue({status: 200});
});

test('establishing the current login scope removes only stale Hubble activity records', async () => {
    localStorage.setItem(`${SESSION_ACTIVITY_PREFIX}old-one`, '{"lastActivity":1}');
    localStorage.setItem(`${SESSION_ACTIVITY_PREFIX}old-two`, '{"lastActivity":2}');
    localStorage.setItem('unrelated.application.setting', 'keep');

    renderHook(() => useSessionIdleTimeout(true));
    await flush();

    expect(localStorage.getItem(`${SESSION_ACTIVITY_PREFIX}old-one`)).toBeNull();
    expect(localStorage.getItem(`${SESSION_ACTIVITY_PREFIX}old-two`)).toBeNull();
    expect(localStorage.getItem(`${SESSION_ACTIVITY_PREFIX}current-scope`)).not.toBeNull();
    expect(localStorage.getItem('unrelated.application.setting')).toBe('keep');
});
