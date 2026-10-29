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
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import Overview from './Overview';
import {getOverview} from '../../api/operations';
import {getDashboard} from '../../api/auth';
import i18n from '../../i18n';
import {formatObservedAt} from './topology';

jest.mock('../../api/operations');
jest.mock('../../api/auth');

const MAIN_OBSERVED = Date.UTC(2026, 9, 29, 8, 0, 0);
const COMPUTE_OBSERVED = Date.UTC(2026, 9, 29, 9, 0, 0);

const snapshot = () => ({
    status: 'DEGRADED', reason: 'cluster_not_ready', observed_at: MAIN_OBSERVED, stale: true,
    main_cluster_status: 'DEGRADED', main_cluster_reason: 'cluster_not_ready',
    sources: {}, facts: {}, nodes: [],
    vermeer: {
        configured: true, availability: 'AVAILABLE', status: 'UP', reason: null,
        observed_at: COMPUTE_OBSERVED, last_success_at: COMPUTE_OBSERVED, stale: false,
        registered_workers: 1, ready_workers: 1,
        master: {id: 'master', name: 'master-01', ready: true, version: '1.8.0'},
        workers: [{id: 'worker-01', name: 'worker-01', ready: true, group: 'default', version: '1.8.0'}],
    },
});

const renderCompute = () => render(
    <MemoryRouter initialEntries={['/operations/overview?service=vermeer']}><Overview /></MemoryRouter>
);

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
    getOverview.mockResolvedValue({...snapshot(), observed_at: 'not-a-date'});

    const {container} = render(
        <MemoryRouter
            initialEntries={['/operations/overview?service=vermeer']}
            future={{v7_startTransition: true, v7_relativeSplatPath: true}}
        >
            <Overview />
        </MemoryRouter>
    );

    expect(await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'}))
        .toBeInTheDocument();
    const status = container.querySelector('.operations-overall-status');
    expect(status).not.toBeNull();
    expect(status.querySelector('.status-up')).not.toBeNull();
    expect(status.querySelector('.status-degraded')).toBeNull();
    expect(within(status).queryByText('Stale')).not.toBeInTheDocument();
    expect(within(status).queryByText('Unavailable')).not.toBeInTheDocument();
});

test.each([
    ['workers_not_ready', 'AVAILABLE', 'DEGRADED', '部分已注册 Worker 尚未就绪', 'workers not ready'],
    ['vermeer_access_denied', 'UNAVAILABLE', 'UNKNOWN', 'Vermeer 拒绝访问集群监测信息', 'vermeer access denied'],
])('the compute header translates its %s reason with the Vermeer dictionary',
    async (reason, availability, status, message, untranslated) => {
        await i18n.changeLanguage('zh-CN');
        const data = snapshot();
        data.vermeer = {...data.vermeer, reason, availability, status, ready_workers: 0};
        if (availability === 'AVAILABLE') {
            data.vermeer.workers[0].ready = false;
        } else {
            data.vermeer.master = null;
            data.vermeer.workers = [];
            data.vermeer.registered_workers = null;
            data.vermeer.ready_workers = null;
        }
        getOverview.mockResolvedValue(data);
        const {container} = renderCompute();
        await screen.findByRole('heading', {level: 2, name: 'Vermeer 计算集群'});
        const header = container.querySelector('.operations-overall-status');
        expect(within(header).getByText(message)).toBeInTheDocument();
        expect(header).not.toHaveTextContent(untranslated);
    });

test('an undeployed compute direct link has an explicit neutral header', async () => {
    const data = snapshot();
    data.vermeer = {...data.vermeer, configured: false, availability: 'UNSUPPORTED', status: 'UNKNOWN',
        reason: 'vermeer_disabled', master: null, workers: [], registered_workers: 0, ready_workers: 0};
    getOverview.mockResolvedValue(data);
    const {container} = renderCompute();
    await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
    const header = container.querySelector('.operations-overall-status');
    expect(within(header).getByText('Not deployed')).toBeInTheDocument();
    expect(within(header).getByText('Vermeer is not enabled in this deployment.')).toBeInTheDocument();
    expect(within(header).queryByText('UNKNOWN')).not.toBeInTheDocument();
});

test.each([
    ['stale main / healthy compute', true, false, 'UP'],
    ['healthy main / stale compute', false, true, 'UNKNOWN'],
])('%s keeps the active service clock and freshness on switch and return',
    async (_, mainStale, computeStale, computeStatus) => {
        const data = snapshot();
        data.stale = mainStale;
        data.reason = mainStale ? 'cluster_not_ready' : 'vermeer_attention';
        data.main_cluster_status = mainStale ? 'DEGRADED' : 'UP';
        data.main_cluster_reason = mainStale ? 'cluster_not_ready' : null;
        data.vermeer = {...data.vermeer, stale: computeStale, status: computeStatus,
            availability: computeStale ? 'UNAVAILABLE' : 'AVAILABLE',
            reason: computeStale ? 'vermeer_upstream_unavailable' : null,
            ready_workers: computeStale ? null : 1};
        getOverview.mockResolvedValue(data);
        const {container} = renderCompute();
        await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
        const header = container.querySelector('.operations-overall-status');
        expect(header).toHaveTextContent(formatObservedAt(COMPUTE_OBSERVED, 'en-US', 'Unavailable'));
        expect(header).not.toHaveTextContent(formatObservedAt(MAIN_OBSERVED, 'en-US', 'Unavailable'));
        expect(Boolean(within(header).queryByText('Stale'))).toBe(computeStale);
        expect(header.querySelector(computeStale ? '.status-unknown' : '.status-up')).not.toBeNull();

        await userEvent.click(screen.getByRole('button', {name: 'Back to main cluster'}));
        await screen.findByRole('heading', {level: 2, name: 'Cluster Overview'});
        const mainHeader = container.querySelector('.operations-overall-status');
        expect(mainHeader).toHaveTextContent(formatObservedAt(MAIN_OBSERVED, 'en-US', 'Unavailable'));
        expect(mainHeader).not.toHaveTextContent(formatObservedAt(COMPUTE_OBSERVED, 'en-US', 'Unavailable'));
        expect(Boolean(within(mainHeader).queryByText('Stale'))).toBe(mainStale);
    });
