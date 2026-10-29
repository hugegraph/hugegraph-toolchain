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

import {render, screen, within} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import Overview from './Overview';
import {getOverview} from '../../api/operations';
import {getDashboard} from '../../api/auth';
import i18n from '../../i18n';

jest.mock('../../api/operations');
jest.mock('../../api/auth');

beforeAll(() => {
    Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: query => ({
            matches: false,
            media: query,
            onchange: null,
            addListener: () => {},
            removeListener: () => {},
            addEventListener: () => {},
            removeEventListener: () => {},
            dispatchEvent: () => false,
        }),
    });
});

beforeEach(() => {
    getDashboard.mockReturnValue(new Promise(() => {}));
});

afterEach(() => {
    jest.restoreAllMocks();
    jest.clearAllMocks();
    i18n.changeLanguage('en-US');
});

test('uses the Vermeer snapshot for the dedicated view header', async () => {
    getOverview.mockResolvedValue({
        status: 'DEGRADED',
        reason: 'cluster_store_unavailable',
        observed_at: 'not-a-date',
        stale: true,
        main_cluster_status: 'DEGRADED',
        main_cluster_reason: 'cluster_store_unavailable',
        sources: {},
        facts: {},
        nodes: [],
        vermeer: {
            configured: true,
            availability: 'AVAILABLE',
            status: 'UP',
            reason: null,
            observed_at: Date.now(),
            last_success_at: Date.now(),
            stale: false,
            registered_workers: 0,
            ready_workers: 0,
            master: null,
            workers: [],
            metrics_availability: 'UNAVAILABLE',
        },
    });

    const {container} = render(
        <MemoryRouter
            initialEntries={['/operations?service=vermeer']}
            future={{v7_startTransition: true, v7_relativeSplatPath: true}}
        >
            <Overview />
        </MemoryRouter>
    );

    expect(await screen.findByRole('heading', {name: 'Vermeer cluster'}))
        .toBeInTheDocument();
    const status = container.querySelector('.operations-overall-status');
    expect(status).not.toBeNull();
    expect(status.querySelector('.status-up')).not.toBeNull();
    expect(status.querySelector('.status-degraded')).toBeNull();
    expect(status.querySelector('.operations-health-stale')).toBeNull();
    expect(within(status).queryByText('Unavailable')).not.toBeInTheDocument();
});
