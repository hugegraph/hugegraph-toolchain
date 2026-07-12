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

import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {EditLayer, ViewLayer} from './EditLayer';
import * as api from '../../api';

jest.mock('react-i18next', () => ({
    initReactI18next: {type: '3rdParty', init: jest.fn()},
    useTranslation: () => ({t: key => key}),
}));

jest.mock('../../api', () => ({
    manage: {
        getGraphSchema: jest.fn(),
        exportSchema: jest.fn(),
        getSchemaList: jest.fn(),
    },
}));

beforeEach(() => {
    jest.clearAllMocks();
    URL.createObjectURL = jest.fn(() => 'blob:schema');
    URL.revokeObjectURL = jest.fn();
    window.matchMedia = jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
    }));
});

test('shows schema and downloads the exported Groovy document', async () => {
    api.manage.getGraphSchema.mockResolvedValue({
        status: 200,
        data: {schema: 'graph.schema().propertyKey("name")'},
    });
    api.manage.exportSchema.mockResolvedValue('schema export');
    const click = jest.spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(() => {});

    render(
        <ViewLayer
            visible
            onCancel={jest.fn()}
            graphspace='DEFAULT'
            graph='hugegraph'
        />
    );

    expect(await screen.findByText('graph.schema().propertyKey("name")'))
        .toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'graph.schema_view.export'}));

    await waitFor(() => expect(api.manage.exportSchema)
        .toHaveBeenCalledWith('DEFAULT', 'hugegraph'));
    await waitFor(() => expect(screen.getByRole('button', {
        name: 'graph.schema_view.export',
    })).not.toHaveClass('ant-btn-loading'));
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    click.mockRestore();
});

test('keeps schema load failure visible and retries the current graph', async () => {
    api.manage.getGraphSchema
        .mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValueOnce({status: 200, data: {schema: 'recovered'}});

    render(
        <ViewLayer
            visible
            onCancel={jest.fn()}
            graphspace='DEFAULT'
            graph='hugegraph'
        />
    );

    expect(await screen.findByText('graph.schema_view.load_failed')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'graph.schema_view.retry'}));
    expect(await screen.findByText('recovered')).toBeInTheDocument();
    expect(api.manage.getGraphSchema).toHaveBeenCalledTimes(2);
});

test('explains template list failure and retries without a silent empty select', async () => {
    api.manage.getSchemaList
        .mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValueOnce({status: 200, data: {records: []}});

    render(
        <EditLayer
            visible
            onCancel={jest.fn()}
            refresh={jest.fn()}
            graphspace='DEFAULT'
        />
    );

    expect(await screen.findByText('graph.form.schema_load_failed')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'graph.form.schema_retry'}));
    await waitFor(() => expect(api.manage.getSchemaList).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('graph.form.schema_load_failed')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('combobox')).toBeEnabled());
});

test('does not leak an old graph export failure into the next graph', async () => {
    let rejectOld;
    api.manage.getGraphSchema.mockResolvedValue({
        status: 200, data: {schema: 'schema'},
    });
    api.manage.exportSchema.mockImplementationOnce(() => new Promise((resolve, reject) => {
        rejectOld = reject;
    }));
    const {rerender} = render(
        <ViewLayer visible onCancel={jest.fn()} graphspace='DEFAULT' graph='graph-a' />
    );
    await screen.findByText('schema');
    fireEvent.click(screen.getByRole('button', {name: 'graph.schema_view.export'}));

    rerender(
        <ViewLayer visible onCancel={jest.fn()} graphspace='DEFAULT' graph='graph-b' />
    );
    await waitFor(() => expect(screen.getByRole('button', {
        name: 'graph.schema_view.export',
    })).toBeEnabled());
    rejectOld(new Error('old offline'));
    await waitFor(() => expect(api.manage.getGraphSchema).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('graph.schema_view.export_failed')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'graph.schema_view.export'}))
        .not.toHaveClass('ant-btn-loading');
});
