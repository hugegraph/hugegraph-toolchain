/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import GraphDetail from './index';
import * as api from '../../api';
import {message} from 'antd';

jest.mock('../../api', () => ({
    manage: {
        getGraphSpace: jest.fn(),
        getGraph: jest.fn(),
        getGraphStatistic: jest.fn(),
        updateGraphStatistic: jest.fn(),
    },
}));

jest.mock('antd', () => ({
    ...jest.requireActual('antd'),
    message: {
        error: jest.fn(),
        success: jest.fn(),
    },
}));

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => ({
        'graph.detail.title': 'Detail',
        'graph.detail.last_update': 'Last Updated: ',
        'graph.detail.update_data': 'Update Data',
        'graph.detail.vertex_total': 'Total vertices',
        'graph.detail.edge_total': 'Total edges',
        'graph.detail.vertex_type': 'Vertex type',
        'graph.detail.edge_type': 'Edge type',
        'graph.detail.count': 'Count',
        'graph.detail.statistics_unavailable': 'Statistics are unavailable. Retry later.',
        'graph.detail.unavailable': 'Graph details are unavailable. Check the server and retry.',
    })[key] || key}),
}));

beforeEach(() => {
    jest.clearAllMocks();
    Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: query => ({
            matches: false,
            media: query,
            addListener: jest.fn(),
            removeListener: jest.fn(),
        }),
    });
});

test('renders one actionable page error when all initialization requests fail', async () => {
    api.manage.getGraphSpace.mockRejectedValue(new Error('connection refused'));
    api.manage.getGraph.mockRejectedValue(new Error('connection refused'));
    api.manage.getGraphStatistic.mockRejectedValue(new Error('connection refused'));

    render(
        <MemoryRouter
            initialEntries={['/graphspace/DEFAULT/graph/g/detail']}
            future={{
                v7_relativeSplatPath: true,
                v7_startTransition: true,
            }}
        >
            <Routes>
                <Route
                    path='/graphspace/:graphspace/graph/:graph/detail'
                    element={<GraphDetail />}
                />
            </Routes>
        </MemoryRouter>
    );

    expect(await screen.findByText(
        'Graph details are unavailable. Check the server and retry.'
    )).toBeInTheDocument();
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(message.error).not.toHaveBeenCalled();
});

test('shows one inline fallback without duplicating the transport toast', async () => {
    api.manage.getGraphSpace.mockResolvedValue({
        status: 200,
        data: {nickname: 'Space'},
    });
    api.manage.getGraph.mockResolvedValue({
        status: 200,
        data: {nickname: 'Graph'},
    });
    api.manage.getGraphStatistic.mockResolvedValue({
        status: 400,
        message: 'Gremlin execution failed, details: ',
    });

    render(
        <MemoryRouter
            initialEntries={['/graphspace/DEFAULT/graph/g/detail']}
            future={{
                v7_relativeSplatPath: true,
                v7_startTransition: true,
            }}
        >
            <Routes>
                <Route
                    path='/graphspace/:graphspace/graph/:graph/detail'
                    element={<GraphDetail />}
                />
            </Routes>
        </MemoryRouter>
    );

    expect(await screen.findByText('Statistics are unavailable. Retry later.'))
        .toBeInTheDocument();
    await waitFor(() => expect(api.manage.getGraphStatistic).toHaveBeenCalledTimes(1));
    expect(message.error).not.toHaveBeenCalled();
});
