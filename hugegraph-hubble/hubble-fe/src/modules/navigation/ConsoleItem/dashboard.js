/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

const normalizeDashboardUrl = address => {
    const value = typeof address === 'string' ? address.trim() : '';
    if (!value) {
        throw new Error('Dashboard address is empty');
    }
    if (/^[a-z][a-z\d+.-]*:\/\//i.test(value) && !/^https?:\/\//i.test(value)) {
        throw new Error('Dashboard address must use HTTP or HTTPS');
    }
    const url = new URL(/^https?:\/\//i.test(value) ? value : `http://${value}`);
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) {
        throw new Error('Dashboard address must use HTTP or HTTPS');
    }
    return url.toString().replace(/\/$/, '');
};

const probeDashboard = async (url, fetchImpl = window.fetch.bind(window), timeout = 3000) => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), timeout);
    try {
        await fetchImpl(url, {
            method: 'GET',
            mode: 'no-cors',
            credentials: 'omit',
            cache: 'no-store',
            referrerPolicy: 'no-referrer',
            signal: controller.signal,
        });
        return true;
    }
    catch {
        return false;
    }
    finally {
        window.clearTimeout(timer);
    }
};

export {normalizeDashboardUrl, probeDashboard};
