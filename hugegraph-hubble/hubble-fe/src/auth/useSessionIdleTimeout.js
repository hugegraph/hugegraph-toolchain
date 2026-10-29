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

import {useEffect, useState} from 'react';
import * as api from '../api';
import {beginLogoutTransition, clearLogin, getUser, isLogoutTransition, USER_CHANGE_EVENT} from '../utils/user';
import {replaceBrowserLocation} from '../utils/browserNavigation';
import {clearPersistedAlgorithmFormsForUser} from '../modules/algorithm/algorithmsForm/algorithmFormPersistence';

export const SESSION_SETTINGS_EVENT = 'hubble.session-settings-change';
export const SESSION_ACTIVITY_PREFIX = 'hubble.session.activity.';
const MAX_TIMER_DELAY = 2147483647;
const validDeploymentTimeout = seconds => Number.isSafeInteger(seconds);
const validTimeout = seconds => validDeploymentTimeout(seconds) && seconds > 0;
const validUpdatedTimeout = seconds => validTimeout(seconds) && seconds >= 900;
const newerSetting = (nextAt, nextSource, currentAt, currentSource) => nextAt > currentAt
    || (nextAt === currentAt && (nextSource === 'update' || currentSource !== 'update'));
const read = key => {
    try { return JSON.parse(localStorage.getItem(key)); }
    catch { return null; }
};
const write = (key, value) => {
    try { localStorage.setItem(key, JSON.stringify(value)); }
    catch { /* Storage can be disabled; the current tab still enforces its deadline. */ }
};
const removeStaleActivityScopes = currentKey => {
    try {
        for (let index = localStorage.length - 1; index >= 0; index--) {
            const candidate = localStorage.key(index);
            if (candidate?.startsWith(SESSION_ACTIVITY_PREFIX) && candidate !== currentKey) {
                localStorage.removeItem(candidate);
            }
        }
    }
    catch { /* Storage can be disabled; stale records do not affect the current tab timer. */ }
};

/** Network polling never counts as human activity. The scope is a noncredential per-login nonce. */
export const useSessionIdleTimeout = enabled => {
    const [identityRevision, setIdentityRevision] = useState(0);
    useEffect(() => {
        const changed = () => setIdentityRevision(value => value + 1);
        window.addEventListener(USER_CHANGE_EVENT, changed);
        return () => window.removeEventListener(USER_CHANGE_EVENT, changed);
    }, []);
    useEffect(() => {
        if (!enabled || !getUser()?.id || isLogoutTransition()) return undefined;
        let active = true;
        let expired = false;
        let scope;
        let key;
        let timer;
        let logoutTimer;
        let settingsRetry;
        let pendingSettings;
        const settingsRequestAt = Date.now();
        let timeoutSeconds;
        let lastActivity = settingsRequestAt;
        let firstInputAt;
        let lastInputAt;
        let longestInputGap = 0;
        let lastWrite = 0;
        let settingsUpdatedAt = 0;
        let settingsSource = 'load';
        const finishLogout = () => {
            if (!active) return;
            beginLogoutTransition();
            sessionStorage.removeItem('redirect');
            clearPersistedAlgorithmFormsForUser();
            clearLogin();
            replaceBrowserLocation('/login');
        };
        const expire = (remote = false) => {
            if (!active || expired) return;
            expired = true;
            window.clearTimeout(timer);
            beginLogoutTransition();
            if (key && !remote) {
                write(key, {lastActivity, timeoutSeconds, settingsUpdatedAt, settingsSource, expiredAt: Date.now()});
            }
            if (remote) finishLogout();
            else {
                // Revoke the server session through the same endpoint as explicit logout.
                const deadline = new Promise(resolve => { logoutTimer = window.setTimeout(resolve, 5000); });
                const logout = Promise.resolve().then(() => api.auth.logout(scope)).catch(() => undefined);
                Promise.race([logout, deadline]).finally(() => {
                    window.clearTimeout(logoutTimer);
                    finishLogout();
                });
            }
        };
        const sync = () => {
            if (!key) return;
            const saved = read(key);
            const now = Date.now();
            if (!saved || !Number.isFinite(saved.lastActivity) || saved.lastActivity > now) return;
            if (saved.expiredAt > 0 && saved.expiredAt <= now) {
                expire(true);
                return;
            }
            lastActivity = Math.max(lastActivity, saved.lastActivity);
            if (validTimeout(saved.timeoutSeconds) && saved.settingsUpdatedAt <= now
                && newerSetting(saved.settingsUpdatedAt, saved.settingsSource, settingsUpdatedAt, settingsSource)) {
                timeoutSeconds = saved.timeoutSeconds;
                settingsUpdatedAt = saved.settingsUpdatedAt;
                settingsSource = saved.settingsSource;
            }
        };
        const schedule = () => {
            window.clearTimeout(timer);
            if (!active || expired || !validTimeout(timeoutSeconds)) return;
            const remaining = lastActivity + timeoutSeconds * 1000 - Date.now();
            if (remaining <= 0) expire();
            else timer = window.setTimeout(check, Math.min(remaining, MAX_TIMER_DELAY));
        };
        const check = () => { sync(); schedule(); };
        const save = () => {
            if (!key || expired) return;
            write(key, {lastActivity, timeoutSeconds, settingsUpdatedAt, settingsSource});
            lastWrite = Date.now();
        };
        const activity = () => {
            if (!active || expired || document.visibilityState === 'hidden') return;
            sync();
            // Waking an already idle page must not renew it before expiry is checked.
            if (expired || (validTimeout(timeoutSeconds) && Date.now() >= lastActivity + timeoutSeconds * 1000)) {
                expire();
                return;
            }
            const now = Date.now();
            if (firstInputAt === undefined) firstInputAt = now;
            if (lastInputAt !== undefined) longestInputGap = Math.max(longestInputGap, now - lastInputAt);
            lastInputAt = now;
            lastActivity = now;
            if (Date.now() - lastWrite >= 1000) save();
            schedule();
        };
        const applySettings = (seconds, activityScope, requestedAt, source = 'load') => {
            const valid = source === 'update' ? validUpdatedTimeout(seconds) : validDeploymentTimeout(seconds);
            if (!active || expired || !valid || typeof activityScope !== 'string'
                || !activityScope || activityScope.length > 128) return;
            if (scope && scope !== activityScope) return;
            const firstSettings = !scope;
            scope = activityScope;
            key = SESSION_ACTIVITY_PREFIX + encodeURIComponent(scope);
            if (firstSettings) removeStaleActivityScopes(key);
            // Load existing activity before writing so a tab opening later cannot renew an idle session.
            const saved = read(key);
            const savedActivity = Number.isFinite(saved?.lastActivity) && saved.lastActivity <= Date.now()
                ? saved.lastActivity : undefined;
            if (firstSettings) {
                // Until metadata arrives, input has no known timeout to check against. Rebuild
                // its deadline from the cached activity or initial load, never from that input.
                lastActivity = savedActivity ?? settingsRequestAt;
            }
            else if (savedActivity !== undefined) {
                lastActivity = Math.max(lastActivity, savedActivity);
            }
            sync();
            const pending = pendingSettings?.activityScope === scope ? pendingSettings : null;
            const newest = pending && newerSetting(pending.requestedAt, pending.source, requestedAt, source)
                ? pending : {timeoutSeconds: seconds, requestedAt, source};
            if (!validTimeout(timeoutSeconds)
                || newerSetting(newest.requestedAt, newest.source, settingsUpdatedAt, settingsSource)) {
                timeoutSeconds = newest.timeoutSeconds;
                settingsUpdatedAt = newest.requestedAt;
                settingsSource = newest.source;
            }
            pendingSettings = null;
            // Input received while the initial GET was pending can renew an unexpired session,
            // but an input after the initial deadline (or a long idle gap) cannot revive it.
            const priorSeconds = validTimeout(saved?.timeoutSeconds) ? saved.timeoutSeconds : timeoutSeconds;
            if (firstSettings && firstInputAt !== undefined
                && firstInputAt < lastActivity + priorSeconds * 1000
                && longestInputGap < priorSeconds * 1000) {
                lastActivity = Math.max(lastActivity, lastInputAt);
            }
            schedule();
            save();
        };
        const settingsChanged = event => {
            const detail = event.detail;
            const requestedAt = detail?.requestedAt ?? Date.now();
            const source = detail?.source === 'load' ? 'load' : 'update';
            const valid = source === 'update'
                ? validUpdatedTimeout(detail?.timeoutSeconds)
                : validDeploymentTimeout(detail?.timeoutSeconds);
            if (!valid || typeof detail?.activityScope !== 'string'
                || !detail.activityScope || !Number.isFinite(requestedAt)
                || requestedAt < settingsRequestAt || requestedAt > Date.now()) return;
            const changed = {...detail, requestedAt, source};
            if (!scope) {
                // Only our own current-identity GET establishes the session scope. Keep a newer
                // settings response until that GET resolves, without accepting another login's scope.
                if (!pendingSettings || newerSetting(requestedAt, changed.source,
                    pendingSettings.requestedAt, pendingSettings.source)) {
                    pendingSettings = changed;
                }
            }
            else if (scope === changed.activityScope) {
                applySettings(changed.timeoutSeconds, changed.activityScope, requestedAt, changed.source);
            }
        };
        const storageChanged = event => { if (event.key === key) check(); };
        const visible = () => { if (document.visibilityState === 'visible') check(); };
        const inputs = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart'];
        inputs.forEach(name => window.addEventListener(name, activity, {passive: true}));
        window.addEventListener('storage', storageChanged);
        window.addEventListener('focus', check);
        document.addEventListener('visibilitychange', visible);
        window.addEventListener(SESSION_SETTINGS_EVENT, settingsChanged);
        const loadSettings = () => {
            const requestedAt = Date.now();
            let retrySettings = true;
            const unsupported = status => status === 404 || status === 405;
            api.auth.getSessionSettings().then(response => {
                if (unsupported(response?.status)) retrySettings = false;
                if (response?.status === 200) {
                    applySettings(response.data?.idle_timeout_seconds, response.data?.activity_scope, requestedAt);
                }
            }).catch(error => {
                if (unsupported(error?.response?.status ?? error?.status)) retrySettings = false;
            }).finally(() => {
                if (active && !scope && !expired && retrySettings) {
                    settingsRetry = window.setTimeout(loadSettings, 30000);
                }
            });
        };
        loadSettings();
        return () => {
            active = false;
            window.clearTimeout(timer);
            window.clearTimeout(logoutTimer);
            window.clearTimeout(settingsRetry);
            inputs.forEach(name => window.removeEventListener(name, activity));
            window.removeEventListener('storage', storageChanged);
            window.removeEventListener('focus', check);
            document.removeEventListener('visibilitychange', visible);
            window.removeEventListener(SESSION_SETTINGS_EVENT, settingsChanged);
        };
    }, [enabled, identityRevision]);
};