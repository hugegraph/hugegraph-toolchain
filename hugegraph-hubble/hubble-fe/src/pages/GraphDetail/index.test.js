/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
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
    })[key] || key}),
}));

beforeEach(() => {
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
        <MemoryRouter initialEntries={['/graphspace/DEFAULT/graph/g/detail']}>
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
