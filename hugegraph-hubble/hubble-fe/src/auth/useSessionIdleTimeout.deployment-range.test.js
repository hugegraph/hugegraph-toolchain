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

import {act, fireEvent, renderHook} from '@testing-library/react';
import {SESSION_ACTIVITY_PREFIX, SESSION_SETTINGS_EVENT, useSessionIdleTimeout} from './useSessionIdleTimeout';
import * as api from '../api';
import * as user from '../utils/user';

jest.mock('../api', () => ({auth: {getSessionSettings: jest.fn(), logout: jest.fn()}}));
jest.mock('../utils/browserNavigation', () => ({replaceBrowserLocation: jest.fn()}));
jest.mock('../modules/algorithm/algorithmsForm/algorithmFormPersistence', () => ({
    clearPersistedAlgorithmFormsForUser: jest.fn(),
}));

const settings = seconds => ({status: 200, data: {
    idle_timeout_seconds: seconds,
    activity_scope: 'deployment-scope',
}});
const flush = async () => { await act(async () => {}); };
const advance = async milliseconds => {
    await act(async () => jest.advanceTimersByTime(milliseconds));
};

beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-10-29T09:00:00Z'));
    jest.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    user.endLogoutTransition();
    user.setUser({id: 'one', user_name: 'one'});
    api.auth.logout.mockResolvedValue({status: 200});
});

afterEach(() => jest.useRealTimers());

test('uses a positive deployment timeout below the editable minimum without polling forever', async () => {
    api.auth.getSessionSettings.mockResolvedValue(settings(600));
    renderHook(() => useSessionIdleTimeout(true));
    await flush();

    await advance(120000);
    expect(api.auth.getSessionSettings).toHaveBeenCalledTimes(1);
    expect(api.auth.logout).not.toHaveBeenCalled();

    await advance(480000);
    await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test.each([0, -1])('treats disabled deployment timeout %s as loaded without polling or logout', async seconds => {
    api.auth.getSessionSettings.mockResolvedValue(settings(seconds));
    renderHook(() => useSessionIdleTimeout(true));
    await flush();

    await advance(180000);
    expect(api.auth.getSessionSettings).toHaveBeenCalledTimes(1);
    expect(api.auth.logout).not.toHaveBeenCalled();
    expect(JSON.parse(localStorage.getItem(
        SESSION_ACTIVITY_PREFIX + 'deployment-scope'
    )).timeoutSeconds).toBe(seconds);
});

test.each([0, -1])('can enable an editable idle limit after loading disabled deployment value %s', async seconds => {
    api.auth.getSessionSettings.mockResolvedValue(settings(seconds));
    renderHook(() => useSessionIdleTimeout(true));
    await flush();
    await advance(1000);
    fireEvent(window, new CustomEvent(SESSION_SETTINGS_EVENT, {detail: {
        timeoutSeconds: 900, activityScope: 'deployment-scope', source: 'update', requestedAt: Date.now(),
    }}));
    await advance(898999);
    expect(api.auth.logout).not.toHaveBeenCalled();
    await advance(1);
    await flush();
    expect(api.auth.getSessionSettings).toHaveBeenCalledTimes(1);
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test('segments a deployment timeout longer than the browser maximum timer delay', async () => {
    const seconds = 2592000;
    const maximumTimerDelay = 2147483647;
    api.auth.getSessionSettings.mockResolvedValue(settings(seconds));
    renderHook(() => useSessionIdleTimeout(true));
    await flush();

    await advance(maximumTimerDelay);
    expect(api.auth.getSessionSettings).toHaveBeenCalledTimes(1);
    expect(api.auth.logout).not.toHaveBeenCalled();

    await advance(seconds * 1000 - maximumTimerDelay - 1);
    expect(api.auth.logout).not.toHaveBeenCalled();
    await advance(1);
    await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});
