/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {normalizeDashboardUrl, probeDashboard} from './dashboard';

test.each([
    ['127.0.0.1:8092', 'http', 'http://127.0.0.1:8092'],
    ['dashboard.example:8443', 'https', 'https://dashboard.example:8443'],
])('normalizes configured dashboard address %s', (address, protocol, expected) => {
    expect(normalizeDashboardUrl(address, protocol)).toBe(expected);
});

test.each(['', 'ftp://dashboard.example', 'dashboard.example/path',
    'user@dashboard.example', 'dashboard.example?probe=true'])(
    'rejects unsafe dashboard address %s',
    address => expect(() => normalizeDashboardUrl(address)).toThrow()
);

test.each(['ftp', 'file', 'javascript'])(
    'rejects unsafe dashboard protocol %s',
    protocol => expect(() => normalizeDashboardUrl('dashboard.example', protocol)).toThrow()
);

test('reports an unreachable dashboard after the browser probe fails', async () => {
    const fetchImpl = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(probeDashboard('http://127.0.0.1:8092', fetchImpl, 50))
        .resolves.toBe(false);
});

test('accepts an opaque no-cors response as network reachable', async () => {
    const fetchImpl = jest.fn().mockResolvedValue({type: 'opaque'});
    await expect(probeDashboard('https://dashboard.example', fetchImpl, 50))
        .resolves.toBe(true);
    expect(fetchImpl).toHaveBeenCalledWith(
        'https://dashboard.example',
        expect.objectContaining({mode: 'no-cors', credentials: 'omit'})
    );
});
