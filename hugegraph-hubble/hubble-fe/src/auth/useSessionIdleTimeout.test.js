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
import {useSessionIdleTimeout, SESSION_ACTIVITY_PREFIX, SESSION_SETTINGS_EVENT} from './useSessionIdleTimeout';
import * as api from '../api';
import * as user from '../utils/user';
import {replaceBrowserLocation} from '../utils/browserNavigation';
import {clearPersistedAlgorithmFormsForUser} from '../modules/algorithm/algorithmsForm/algorithmFormPersistence';

jest.mock('../api', () => ({auth: {getSessionSettings: jest.fn(), logout: jest.fn()}}));
jest.mock('../utils/browserNavigation', () => ({replaceBrowserLocation: jest.fn()}));
jest.mock('../modules/algorithm/algorithmsForm/algorithmFormPersistence', () => ({
    clearPersistedAlgorithmFormsForUser: jest.fn(),
}));
const scope = 'test-login-scope';
const storageKey = SESSION_ACTIVITY_PREFIX + scope;
const settings = (seconds = 900, nonce = scope) => ({status: 200,
    data: {idle_timeout_seconds: seconds, activity_scope: nonce}});
const flush = async () => { await act(async () => {}); };
const advance = async ms => { await act(async () => jest.advanceTimersByTime(ms)); };
const configure = (seconds, nonce = scope) => fireEvent(window,
    new CustomEvent(SESSION_SETTINGS_EVENT, {detail: {timeoutSeconds: seconds, activityScope: nonce}}));
const storeActivity = value => {
    localStorage.setItem(storageKey, JSON.stringify(value));
    fireEvent(window, new StorageEvent('storage', {key: storageKey, newValue: JSON.stringify(value)}));
};
beforeEach(() => {
    jest.useFakeTimers(); jest.setSystemTime(new Date('2026-10-29T09:00:00Z'));
    jest.clearAllMocks(); localStorage.clear(); sessionStorage.clear();
    user.endLogoutTransition(); user.setUser({id: 'one', user_name: 'one'});
    api.auth.getSessionSettings.mockResolvedValue(settings());
    api.auth.logout.mockResolvedValue({status: 200});
});
afterEach(() => jest.useRealTimers());

test('expires on human inactivity despite background API traffic and clears login/drafts', async () => {
    sessionStorage.setItem('redirect', '/old-route');
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(899000);
    // API completions/settings reads never renew human activity.
    configure(900);
    expect(api.auth.logout).not.toHaveBeenCalled();
    await advance(1000); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
    expect(api.auth.logout).toHaveBeenCalledWith(scope);
    expect(user.getUser()).toEqual({});
    expect(clearPersistedAlgorithmFormsForUser).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem('redirect')).toBeNull();
    expect(replaceBrowserLocation).toHaveBeenCalledWith('/login');
});

test('keyboard/pointer activity renews the deadline while focus alone only checks it', async () => {
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(800000); fireEvent.keyDown(window, {key: 'a'});
    await advance(800000); fireEvent.pointerDown(window);
    await advance(800000); fireEvent.focus(window);
    expect(api.auth.logout).not.toHaveBeenCalled();
    await advance(100000); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test('same-session tab activity renews all tabs and a different session cannot renew this one', async () => {
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(800000);
    storeActivity({lastActivity: Date.now(), timeoutSeconds: 900, settingsUpdatedAt: Date.now()});
    await advance(800000);
    fireEvent(window, new StorageEvent('storage', {key: SESSION_ACTIVITY_PREFIX + 'another-session',
        newValue: JSON.stringify({lastActivity: Date.now()})}));
    expect(api.auth.logout).not.toHaveBeenCalled();
    await advance(100000); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test('idle tab reload and waking after suspended timers cannot renew an expired session', async () => {
    localStorage.setItem(storageKey, JSON.stringify({lastActivity: Date.now() - 901000,
        timeoutSeconds: 900, settingsUpdatedAt: Date.now() - 901000}));
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
    expect(replaceBrowserLocation).toHaveBeenCalledWith('/login');
});

test('timeout changes apply immediately locally and from another same-session tab', async () => {
    api.auth.getSessionSettings.mockResolvedValue(settings(3600));
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(1000000); configure(900); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test('increasing the timeout in another tab takes effect without a new server poll', async () => {
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(800000);
    storeActivity({lastActivity: Date.now() - 800000, timeoutSeconds: 3600, settingsUpdatedAt: Date.now()});
    await advance(100000); expect(api.auth.logout).not.toHaveBeenCalled();
    await advance(2700000); await flush(); expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test('remote expiry clears this tab without sending a second logout request', async () => {
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    storeActivity({lastActivity: Date.now(), timeoutSeconds: 900, expiredAt: Date.now()});
    expect(api.auth.logout).not.toHaveBeenCalled();
    expect(replaceBrowserLocation).toHaveBeenCalledWith('/login');
    expect(user.getUser()).toEqual({});
});

test('anonymous and signed-out pages never request or create a session', async () => {
    const view = renderHook(({enabled}) => useSessionIdleTimeout(enabled), {initialProps: {enabled: false}});
    await flush(); expect(api.auth.getSessionSettings).not.toHaveBeenCalled();
    act(() => user.clearLogin()); view.rerender({enabled: true}); await flush();
    fireEvent.pointerMove(window); await advance(604800000);
    expect(api.auth.getSessionSettings).not.toHaveBeenCalled();
    expect(api.auth.logout).not.toHaveBeenCalled(); expect(localStorage.length).toBe(0);
});

test('identity changes reject stale settings and isolate an old login scope', async () => {
    let resolve;
    api.auth.getSessionSettings.mockImplementationOnce(() => new Promise(done => {resolve = done;}));
    renderHook(() => useSessionIdleTimeout(true));
    api.auth.getSessionSettings.mockResolvedValue(settings(3600, 'new-session'));
    act(() => user.setUser({id: 'two', user_name: 'two'})); await flush();
    await act(async () => resolve(settings(900, 'old-session')));
    configure(900, 'old-session'); await advance(900000);
    expect(api.auth.logout).not.toHaveBeenCalled();
    expect(localStorage.getItem(SESSION_ACTIVITY_PREFIX + 'old-session')).toBeNull();
    await advance(2700000); await flush(); expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test('unmount removes listeners and timers and a failed logout still clears local login', async () => {
    const view = renderHook(() => useSessionIdleTimeout(true)); await flush(); view.unmount();
    await advance(900000); expect(api.auth.logout).not.toHaveBeenCalled();
    api.auth.logout.mockRejectedValue(new Error('offline'));
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
    expect(user.getUser()).toEqual({}); expect(replaceBrowserLocation).toHaveBeenCalledWith('/login');
});

test('a hung logout request cannot retain local login past the five-second cleanup deadline', async () => {
    api.auth.logout.mockReturnValue(new Promise(() => {}));
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(900000); expect(api.auth.logout).toHaveBeenCalledTimes(1);
    expect(user.getUser().id).toBe('one');
    await advance(5000); await flush();
    expect(user.getUser()).toEqual({});
    expect(replaceBrowserLocation).toHaveBeenCalledWith('/login');
});

test('a late initial GET cannot overwrite a newer PUT received before the session scope was known', async () => {
    let resolve;
    api.auth.getSessionSettings.mockImplementationOnce(() => new Promise(done => {resolve = done;}));
    renderHook(() => useSessionIdleTimeout(true));
    await advance(1000);
    fireEvent(window, new CustomEvent(SESSION_SETTINGS_EVENT, {detail: {
        timeoutSeconds: 3600, activityScope: scope, requestedAt: Date.now(), source: 'update',
    }}));
    await act(async () => resolve(settings(900)));
    expect(JSON.parse(localStorage.getItem(storageKey)).timeoutSeconds).toBe(3600);
    await advance(900000); expect(api.auth.logout).not.toHaveBeenCalled();
});

test('a same-millisecond PUT wins over stale GET events and responses', async () => {
    let resolve;
    api.auth.getSessionSettings.mockImplementationOnce(() => new Promise(done => {resolve = done;}));
    renderHook(() => useSessionIdleTimeout(true));
    for (const [source, seconds] of [['update', 3600], ['load', 900]]) {
        fireEvent(window, new CustomEvent(SESSION_SETTINGS_EVENT, {detail: {
            timeoutSeconds: seconds, activityScope: scope, requestedAt: Date.now(), source,
        }}));
    }
    await act(async () => resolve(settings(900)));
    configure(900, 'other-login');
    expect(JSON.parse(localStorage.getItem(storageKey)).timeoutSeconds).toBe(3600);
    // Once established, a later completion of the same old load is still stale.
    fireEvent(window, new CustomEvent(SESSION_SETTINGS_EVENT, {detail: {
        timeoutSeconds: 900, activityScope: scope, requestedAt: Date.now(), source: 'load',
    }}));
    await advance(900000); expect(api.auth.logout).not.toHaveBeenCalled();
});

test('a slow GET preserves newer same-session settings saved by another tab', async () => {
    let resolve;
    api.auth.getSessionSettings.mockImplementationOnce(() => new Promise(done => {resolve = done;}));
    renderHook(() => useSessionIdleTimeout(true));
    await advance(1000);
    localStorage.setItem(storageKey, JSON.stringify({lastActivity: Date.now(), timeoutSeconds: 3600,
        settingsUpdatedAt: Date.now(), settingsSource: 'update'}));
    await act(async () => resolve(settings(900)));
    expect(JSON.parse(localStorage.getItem(storageKey)).timeoutSeconds).toBe(3600);
    await advance(900000); expect(api.auth.logout).not.toHaveBeenCalled();
});

test('activity after suspended timers checks the expired deadline before renewing it', async () => {
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    jest.setSystemTime(Date.now() + 901000);
    fireEvent.pointerDown(window); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});

test('retrying failed settings load retains the original activity deadline', async () => {
    api.auth.getSessionSettings.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(settings());
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(30000); await flush();
    expect(api.auth.getSessionSettings).toHaveBeenCalledTimes(2);
    await advance(870000); await flush();
    expect(api.auth.logout).toHaveBeenCalledTimes(1);
});


test.each([404, 405])('does not repeatedly poll a server that does not support session settings (%s)', async status => {
    api.auth.getSessionSettings.mockResolvedValue({status});
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(120000); await flush();
    expect(api.auth.getSessionSettings).toHaveBeenCalledTimes(1);
    expect(api.auth.logout).not.toHaveBeenCalled();
});

test('does not retry an explicit unsupported HTTP rejection', async () => {
    api.auth.getSessionSettings.mockRejectedValue({response: {status: 404}});
    renderHook(() => useSessionIdleTimeout(true)); await flush();
    await advance(120000); await flush();
    expect(api.auth.getSessionSettings).toHaveBeenCalledTimes(1);
});


test('human input before the cached deadline is retained while the initial metadata response is slow', async () => {
    let resolve;
    localStorage.setItem(storageKey, JSON.stringify({lastActivity: Date.now() - 899000,
        timeoutSeconds: 900, settingsUpdatedAt: Date.now() - 899000}));
    api.auth.getSessionSettings.mockImplementationOnce(() => new Promise(done => {resolve = done;}));
    renderHook(() => useSessionIdleTimeout(true));
    fireEvent.keyDown(window, {key: 'a'});
    await advance(10000);
    await act(async () => resolve(settings()));
    expect(api.auth.logout).not.toHaveBeenCalled();
    await advance(890000); await flush(); expect(api.auth.logout).toHaveBeenCalledTimes(1);
});
