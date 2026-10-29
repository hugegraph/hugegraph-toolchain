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
import {formatObservedAge, formatObservedAt} from './topology';

jest.mock('../../api/operations');
jest.mock('../../api/auth');

const MAIN_OBSERVED = Date.UTC(2026, 9, 29, 8, 0, 0);
const COMPUTE_LAST_SUCCESS = Date.UTC(2026, 9, 29, 8, 30, 0);
const COMPUTE_OBSERVED = Date.UTC(2026, 9, 29, 9, 0, 0);
const RETRY_OBSERVED = Date.UTC(2026, 9, 29, 10, 0, 0);

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
            last_success_at: computeStale ? COMPUTE_LAST_SUCCESS : COMPUTE_OBSERVED,
            ready_workers: computeStale ? null : 1};
        getOverview.mockResolvedValue(data);
        const {container} = renderCompute();
        await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
        const header = container.querySelector('.operations-overall-status');
        const expectedObserved = computeStale ? COMPUTE_LAST_SUCCESS : COMPUTE_OBSERVED;
        expect(header).toHaveTextContent(
            formatObservedAt(expectedObserved, 'en-US', 'Unavailable')
        );
        expect(header).not.toHaveTextContent(formatObservedAt(MAIN_OBSERVED, 'en-US', 'Unavailable'));
        if (computeStale) {
            expect(header).not.toHaveTextContent(
                formatObservedAt(COMPUTE_OBSERVED, 'en-US', 'Unavailable')
            );
        }
        expect(Boolean(within(header).queryByText('Stale'))).toBe(computeStale);
        expect(header.querySelector(computeStale ? '.status-unknown' : '.status-up')).not.toBeNull();
        const mainStrip = container.querySelector('.operations-deployment-health > span');
        const mainContext = container.querySelector('.operations-vermeer-main-context');
        expect(Boolean(within(mainStrip).queryByText('Stale'))).toBe(mainStale);
        expect(Boolean(within(mainContext).queryByText('Stale'))).toBe(mainStale);

        await userEvent.click(screen.getByRole('button', {name: 'Back to main cluster'}));
        await screen.findByRole('heading', {level: 2, name: 'Cluster Overview'});
        const mainHeader = container.querySelector('.operations-overall-status');
        expect(mainHeader).toHaveTextContent(formatObservedAt(MAIN_OBSERVED, 'en-US', 'Unavailable'));
        expect(mainHeader).not.toHaveTextContent(formatObservedAt(COMPUTE_OBSERVED, 'en-US', 'Unavailable'));
        expect(Boolean(within(mainHeader).queryByText('Stale'))).toBe(mainStale);
    });

test.each([
    ['UP', true, COMPUTE_OBSERVED],
    ['UNKNOWN', true, COMPUTE_OBSERVED],
    ['UNKNOWN', false, COMPUTE_OBSERVED],
    ['UP', false, RETRY_OBSERVED],
])('compute header status %s and stale=%s choose observation time independently',
    async (status, stale, expectedTime) => {
        jest.spyOn(Date, 'now').mockReturnValue(RETRY_OBSERVED);
        const data = snapshot();
        data.vermeer = {...data.vermeer, status, stale, observed_at: RETRY_OBSERVED,
            last_success_at: COMPUTE_OBSERVED,
            availability: status === 'UNKNOWN' || stale ? 'UNAVAILABLE' : 'AVAILABLE'};
        getOverview.mockResolvedValue(data);
        const {container} = renderCompute();
        await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
        const header = container.querySelector('.operations-overall-status');
        expect(header).toHaveTextContent(formatObservedAt(expectedTime, 'en-US', 'Unavailable'));
        expect(header).toHaveTextContent(formatObservedAge(expectedTime, 'en-US', 'Unavailable'));
        const otherTime = expectedTime === RETRY_OBSERVED ? COMPUTE_OBSERVED : RETRY_OBSERVED;
        expect(header).not.toHaveTextContent(formatObservedAt(otherTime, 'en-US', 'Unavailable'));
        expect(Boolean(within(header).queryByText('Stale'))).toBe(stale);
    });

test.each(['vermeer_upstream_unavailable', 'vermeer_credentials_unavailable', 'vermeer_access_denied'])(
    'a first %s failure has no successful observation clock', async reason => {
        const data = snapshot();
        data.vermeer = {...data.vermeer, availability: 'UNAVAILABLE', status: 'UNKNOWN', reason,
            stale: false, observed_at: RETRY_OBSERVED, last_success_at: null,
            master: null, workers: [], registered_workers: null, ready_workers: null};
        getOverview.mockResolvedValue(data);
        const {container} = renderCompute();
        await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
        const header = container.querySelector('.operations-overall-status');
        expect(header).toHaveTextContent('Observed: Unavailable · Unavailable');
        expect(header).not.toHaveTextContent(formatObservedAt(RETRY_OBSERVED, 'en-US', 'Unavailable'));
        const lastObserved = container.querySelector('.operations-vermeer-facts .operations-fact:last-child');
        expect(lastObserved).toHaveTextContent('Last observedUnavailable');
        expect(within(header).queryByText('Stale')).not.toBeInTheDocument();
    }
);

test('a first collection timeout explains the unconfirmed readiness in Chinese without a successful clock', async () => {
    await i18n.changeLanguage('zh-CN');
    const data = snapshot();
    data.vermeer = {...data.vermeer, availability: 'UNAVAILABLE', status: 'UNKNOWN',
        reason: 'vermeer_upstream_timeout', stale: false, observed_at: RETRY_OBSERVED,
        last_success_at: null, master: null, workers: [], registered_workers: null, ready_workers: null};
    getOverview.mockResolvedValue(data);
    const {container} = renderCompute();
    await screen.findByRole('heading', {level: 2, name: 'Vermeer 计算集群'});
    const header = container.querySelector('.operations-overall-status');
    expect(within(header).getByText('计算集群采集超时，当前就绪状态待确认')).toBeInTheDocument();
    expect(header).not.toHaveTextContent('vermeer_upstream_timeout');
    expect(header).not.toHaveTextContent('vermeer upstream timeout');
    expect(header).toHaveTextContent(`${i18n.t('operations.observed_at')}: 不可用 · 不可用`);
    expect(header).not.toHaveTextContent(formatObservedAt(RETRY_OBSERVED, 'zh-CN', '不可用'));
    expect(header.querySelector('.status-unknown')).not.toBeNull();
});

test.each([
    ['fresh with successful timestamp', 'AVAILABLE', false, COMPUTE_LAST_SUCCESS, RETRY_OBSERVED],
    ['fresh legacy response', 'AVAILABLE', false, null, RETRY_OBSERVED],
    ['retained successful snapshot', 'UNAVAILABLE', true, COMPUTE_LAST_SUCCESS, COMPUTE_LAST_SUCCESS],
    ['retained snapshot without success', 'UNAVAILABLE', true, null, null],
])('%s preserves successful service clocks and the node observation clock',
    async (_, availability, stale, lastSuccess, expectedTime) => {
        const data = snapshot();
        data.vermeer = {...data.vermeer, availability, stale, observed_at: RETRY_OBSERVED,
            last_success_at: lastSuccess};
        getOverview.mockResolvedValue(data);
        const {container} = renderCompute();
        await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
        const expected = formatObservedAt(expectedTime, 'en-US', 'Unavailable');
        expect(container.querySelector('.operations-overall-status')).toHaveTextContent(expected);
        const lastObserved = container.querySelector('.operations-vermeer-facts .operations-fact:last-child strong');
        expect(lastObserved).toHaveTextContent(expected);
        await userEvent.click(screen.getByRole('button', {name: 'worker-01'}));
        const dialog = await screen.findByRole('dialog');
        const expectedNode = formatObservedAt(availability === 'AVAILABLE' && !stale
            ? RETRY_OBSERVED : expectedTime, 'en-US', 'Unavailable');
        expect(dialog.querySelector('.ant-descriptions-row:last-child .ant-descriptions-item-content'))
            .toHaveTextContent(expectedNode);
    });

test('fresh node details preserve an explicit node observation distinct from the collector clock', async () => {
    const data = snapshot();
    data.vermeer.workers[0].observed_at = COMPUTE_LAST_SUCCESS;
    getOverview.mockResolvedValue(data);
    const {container} = renderCompute();
    await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
    expect(container.querySelector('.operations-overall-status')).toHaveTextContent(
        formatObservedAt(COMPUTE_OBSERVED, 'en-US', 'Unavailable')
    );
    await userEvent.click(screen.getByRole('button', {name: 'worker-01'}));
    const dialog = await screen.findByRole('dialog');
    expect(dialog.querySelector('.ant-descriptions-row:last-child .ant-descriptions-item-content'))
        .toHaveTextContent(formatObservedAt(COMPUTE_LAST_SUCCESS, 'en-US', 'Unavailable'));
});

test.each([-15, 15])('fresh source time is preserved when the Hubble clock differs by %s minutes', async offset => {
    const collectorTime = COMPUTE_OBSERVED + offset * 60 * 1000;
    jest.spyOn(Date, 'now').mockReturnValue(collectorTime);
    const data = snapshot();
    data.vermeer.last_success_at = collectorTime;
    data.vermeer.workers[0].observed_at = COMPUTE_OBSERVED;
    getOverview.mockResolvedValue(data);
    const {container} = renderCompute();
    await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
    const expected = formatObservedAt(COMPUTE_OBSERVED, 'en-US', 'Unavailable');
    const header = container.querySelector('.operations-overall-status');
    expect(header).toHaveTextContent(expected);
    expect(header).not.toHaveTextContent(formatObservedAt(collectorTime, 'en-US', 'Unavailable'));
    expect(container.querySelector('.operations-vermeer-facts .operations-fact:last-child strong'))
        .toHaveTextContent(expected);
    await userEvent.click(screen.getByRole('button', {name: 'worker-01'}));
    const dialog = await screen.findByRole('dialog');
    expect(dialog.querySelector('.ant-descriptions-row:last-child .ant-descriptions-item-content'))
        .toHaveTextContent(expected);
});

test('fresh snapshots without a source timestamp fall back to the successful collection clock', async () => {
    const data = snapshot();
    data.vermeer.observed_at = null;
    getOverview.mockResolvedValue(data);
    const {container} = renderCompute();
    await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
    const expected = formatObservedAt(COMPUTE_OBSERVED, 'en-US', 'Unavailable');
    expect(container.querySelector('.operations-overall-status')).toHaveTextContent(expected);
    expect(container.querySelector('.operations-vermeer-facts .operations-fact:last-child strong'))
        .toHaveTextContent(expected);
    await userEvent.click(screen.getByRole('button', {name: 'worker-01'}));
    const dialog = await screen.findByRole('dialog');
    expect(dialog.querySelector('.ant-descriptions-row:last-child .ant-descriptions-item-content'))
        .toHaveTextContent(expected);
});

test('a stale formerly UP main snapshot remains visibly stale beside fresh compute health', async () => {
    const data = snapshot();
    data.main_cluster_status = 'UP';
    data.main_cluster_reason = null;
    getOverview.mockResolvedValue(data);
    const {container} = renderCompute();
    await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
    const header = container.querySelector('.operations-overall-status');
    expect(header.querySelector('.status-up')).not.toBeNull();
    expect(within(header).queryByText('Stale')).not.toBeInTheDocument();
    for (const mainStatus of [container.querySelector('.operations-deployment-health > span'),
        container.querySelector('.operations-vermeer-main-context')]) {
        expect(within(mainStatus).getByText('UP')).toBeInTheDocument();
        expect(within(mainStatus).getByText('Stale')).toBeInTheDocument();
    }
});

test('a stale compute snapshot without a successful observation does not reuse the failed-attempt clock', async () => {
    jest.spyOn(Date, 'now').mockReturnValue(RETRY_OBSERVED);
    const data = snapshot();
    data.vermeer = {...data.vermeer, status: 'UNKNOWN', stale: true, availability: 'UNAVAILABLE',
        observed_at: RETRY_OBSERVED};
    delete data.vermeer.last_success_at;
    getOverview.mockResolvedValue(data);
    const {container} = renderCompute();
    await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
    const header = container.querySelector('.operations-overall-status');
    expect(header).toHaveTextContent('Observed: Unavailable · Unavailable');
    expect(header).not.toHaveTextContent(formatObservedAt(RETRY_OBSERVED, 'en-US', 'Unavailable'));
    expect(header).not.toHaveTextContent('0s ago');
    expect(within(header).getByText('Stale')).toBeInTheDocument();
});

test('the main aggregate header keeps its observation time even when it is stale', async () => {
    const lastSuccess = MAIN_OBSERVED - 60 * 60 * 1000;
    const data = {...snapshot(), last_success_at: lastSuccess};
    getOverview.mockResolvedValue(data);
    const {container} = render(<MemoryRouter><Overview /></MemoryRouter>);
    await screen.findByRole('heading', {level: 2, name: 'Cluster Overview'});
    const header = container.querySelector('.operations-overall-status');
    expect(header).toHaveTextContent(formatObservedAt(MAIN_OBSERVED, 'en-US', 'Unavailable'));
    expect(header).not.toHaveTextContent(formatObservedAt(lastSuccess, 'en-US', 'Unavailable'));
    expect(within(header).getByText('Stale')).toBeInTheDocument();
});

test.each([
    ['failed probe without success', 'UNAVAILABLE', false, undefined, null],
    ['successful fresh snapshot', 'AVAILABLE', false, undefined, COMPUTE_OBSERVED],
    ['fresh snapshot with a separate collector clock', 'AVAILABLE', false,
        COMPUTE_LAST_SUCCESS, COMPUTE_LAST_SUCCESS],
    ['retained real success', 'UNAVAILABLE', true, COMPUTE_OBSERVED, COMPUTE_OBSERVED],
    ['stale snapshot without success', 'AVAILABLE', true, undefined, null],
])('a later Hubble refresh failure preserves only successful evidence from %s',
    async (_, availability, stale, lastSuccess, expectedTime) => {
        jest.spyOn(Date, 'now').mockReturnValue(RETRY_OBSERVED);
        const data = snapshot();
        data.vermeer = {...data.vermeer, availability, stale,
            status: availability === 'AVAILABLE' && !stale ? 'UP' : 'UNKNOWN',
            observed_at: availability === 'AVAILABLE' && !stale ? COMPUTE_OBSERVED : RETRY_OBSERVED};
        if (lastSuccess === undefined) {
            delete data.vermeer.last_success_at;
        } else {
            data.vermeer.last_success_at = lastSuccess;
        }
        getOverview.mockResolvedValue(data);
        const {container} = renderCompute();
        await screen.findByRole('heading', {level: 2, name: 'Vermeer Compute Cluster'});
        getOverview.mockRejectedValueOnce(new Error('Hubble network unavailable'));
        await userEvent.click(screen.getByRole('button', {name: 'Refresh'}));
        const header = container.querySelector('.operations-overall-status');
        await within(header).findByText('Cluster refresh failed. The last observed compute snapshot is retained.');
        if (expectedTime === null) {
            expect(header).toHaveTextContent('Observed: Unavailable · Unavailable');
        } else {
            expect(header).toHaveTextContent(formatObservedAt(expectedTime, 'en-US', 'Unavailable'));
            expect(header).toHaveTextContent(formatObservedAge(expectedTime, 'en-US', 'Unavailable'));
        }
        expect(header).not.toHaveTextContent(formatObservedAt(RETRY_OBSERVED, 'en-US', 'Unavailable'));
        expect(header).not.toHaveTextContent('0s ago');
        expect(within(header).getByText('Stale')).toBeInTheDocument();
    });
