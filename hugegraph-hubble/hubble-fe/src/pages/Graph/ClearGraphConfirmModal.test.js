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

import {act, render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import enPages from '../../i18n/resources/en-US/modules/pages.json';
import zhPages from '../../i18n/resources/zh-CN/modules/pages.json';
import ClearGraphConfirmModal from './ClearGraphConfirmModal';

const mockClearMessages = enPages.graph.clear_confirm;
const mockMessages = {
    'graph.clear_confirm.title': mockClearMessages.title,
    'graph.clear_confirm.graphspace': mockClearMessages.graphspace,
    'graph.clear_confirm.graph': mockClearMessages.graph,
    'graph.clear_confirm.scope_data': mockClearMessages.scope_data,
    'graph.clear_confirm.scope_schema_data': mockClearMessages.scope_schema_data,
    'graph.clear_confirm.irreversible': mockClearMessages.irreversible,
    'graph.clear_confirm.schema_preservation_warning':
        mockClearMessages.schema_preservation_warning,
    'graph.clear_confirm.input_label': mockClearMessages.input_label,
    'graph.clear_confirm.input_placeholder': mockClearMessages.input_placeholder,
    'graph.clear_confirm.confirm': mockClearMessages.confirm,
    'graph.clear_confirm.cancel': mockClearMessages.cancel,
    'graph.clear_confirm.request_failed': mockClearMessages.request_failed,
};

jest.mock('react-i18next', () => ({
    useTranslation: () => ({
        t: (key, params = {}) => Object.entries(params).reduce(
            (value, [name, replacement]) => value.replace(`{{${name}}}`, replacement),
            mockMessages[key] || key
        ),
    }),
}));

beforeAll(() => {
    window.matchMedia = jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    }));
});

const renderModal = (props = {}) => {
    const defaultProps = {
        open: true,
        graphspace: 'DEFAULT',
        graph: 'hugegraph',
        mode: 'data',
        onCancel: jest.fn(),
        onSuccess: jest.fn(),
        onConfirm: jest.fn(),
    };

    return {
        ...render(<ClearGraphConfirmModal {...defaultProps} {...props} />),
        props: {...defaultProps, ...props},
    };
};

const createDeferred = () => {
    let resolve;
    let reject;
    const promise = new Promise((promiseResolve, promiseReject) => {
        resolve = promiseResolve;
        reject = promiseReject;
    });

    return {promise, resolve, reject};
};

test.each([
    ['data', 'Deletion scope: graph data only', true],
    [
        'schema-data',
        'Deletion scope: graph data, property keys, vertex labels, edge labels, and indexes',
        false,
    ],
])('requires exact graph-name confirmation in %s mode', async (mode, scope, warnsSchema) => {
    renderModal({mode});

    expect(screen.getByText('GraphSpace: DEFAULT')).toBeInTheDocument();
    expect(screen.getByText('Graph: hugegraph')).toBeInTheDocument();
    expect(screen.getByText(scope)).toBeInTheDocument();
    expect(screen.getByText('This operation is irreversible.')).toBeInTheDocument();
    if (warnsSchema) {
        expect(screen.getByText(
            'No verified dedicated Server operation currently guarantees schema preservation.'
        )).toBeInTheDocument();
    }
    else {
        expect(screen.queryByText(
            'No verified dedicated Server operation currently guarantees schema preservation.'
        )).not.toBeInTheDocument();
    }

    const input = screen.getByPlaceholderText('Graph name');
    const confirm = screen.getByRole('button', {name: 'Clear graph'});
    expect(confirm).toBeDisabled();

    userEvent.type(input, 'wrong');
    expect(confirm).toBeDisabled();

    userEvent.clear(input);
    userEvent.type(input, 'hugegraph');
    expect(confirm).toBeEnabled();
});

test('provides explicit schema-and-data scope in both locales', () => {
    expect(enPages.graph.clear_confirm.scope_schema_data).toBe(
        'Deletion scope: graph data, property keys, vertex labels, edge labels, and indexes'
    );
    expect(zhPages.graph.clear_confirm.scope_schema_data).toBe(
        '删除范围：图数据、属性键、顶点类型、边类型和索引'
    );
    expect(zhPages.graph.clear_confirm.schema_preservation_warning).toBe(
        '当前尚无经过验证、可保证保留 schema 的专用 Server 操作。'
    );
});

test('calls onSuccess only after a successful clear response', async () => {
    const onConfirm = jest.fn().mockResolvedValue({status: 200});
    const onSuccess = jest.fn();
    renderModal({onConfirm, onSuccess});

    userEvent.type(screen.getByPlaceholderText('Graph name'), 'hugegraph');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expect(onConfirm).toHaveBeenCalledTimes(1);
});

test('treats an empty resolved response as a successful clear', async () => {
    const onConfirm = jest.fn().mockResolvedValue(undefined);
    const onSuccess = jest.fn();
    renderModal({onConfirm, onSuccess});

    userEvent.type(screen.getByPlaceholderText('Graph name'), 'hugegraph');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expect(screen.queryByText('Clear failed')).not.toBeInTheDocument();
});

test('keeps the dialog and confirmation input after an unsuccessful response', async () => {
    const onConfirm = jest.fn().mockResolvedValue({status: 500, message: 'boom'});
    const onSuccess = jest.fn();
    renderModal({onConfirm, onSuccess});

    const input = screen.getByPlaceholderText('Graph name');
    userEvent.type(input, 'hugegraph');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));

    expect(await screen.findByText('boom')).toBeInTheDocument();
    expect(input).toHaveValue('hugegraph');
    await waitFor(() => expect(
        screen.getByRole('button', {name: 'Clear graph'})
    ).toBeEnabled());
    expect(onSuccess).not.toHaveBeenCalled();
});

test('keeps the dialog usable after a rejected request', async () => {
    const onConfirm = jest.fn().mockRejectedValue(new Error('network'));
    const onSuccess = jest.fn();
    renderModal({onConfirm, onSuccess});

    const input = screen.getByPlaceholderText('Graph name');
    userEvent.type(input, 'hugegraph');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));

    expect(await screen.findByText('network')).toBeInTheDocument();
    expect(input).toHaveValue('hugegraph');
    await waitFor(() => expect(
        screen.getByRole('button', {name: 'Clear graph'})
    ).toBeEnabled());
    expect(onSuccess).not.toHaveBeenCalled();
});

test('prefers a rejected backend response message over the generic error', async () => {
    const requestError = new Error('Request failed with status code 500');
    requestError.response = {data: {message: 'backend refused clear'}};
    const onConfirm = jest.fn().mockRejectedValue(requestError);
    renderModal({onConfirm});

    userEvent.type(screen.getByPlaceholderText('Graph name'), 'hugegraph');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));

    expect(await screen.findByText('backend refused clear')).toBeInTheDocument();
    expect(screen.queryByText('Request failed with status code 500'))
        .not.toBeInTheDocument();
});

test('resets confirmation and errors when opened for a new graph', async () => {
    const onConfirm = jest.fn().mockResolvedValue({status: 500, message: 'boom'});
    const {rerender, props} = renderModal({onConfirm});

    userEvent.type(screen.getByPlaceholderText('Graph name'), 'hugegraph');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));
    expect(await screen.findByText('boom')).toBeInTheDocument();

    rerender(<ClearGraphConfirmModal {...props} graph='new-graph' mode='schema-data' />);

    expect(screen.getByPlaceholderText('Graph name')).toHaveValue('');
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Clear graph'})).toBeDisabled();
});

test('guards duplicate submit and disables cancellation while a request is pending', async () => {
    const deferred = createDeferred();
    const onConfirm = jest.fn().mockReturnValue(deferred.promise);
    const onSuccess = jest.fn();
    renderModal({onConfirm, onSuccess});

    userEvent.type(screen.getByPlaceholderText('Graph name'), 'hugegraph');
    const confirm = screen.getByRole('button', {name: 'Clear graph'});
    act(() => {
        confirm.click();
        confirm.click();
    });

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeDisabled();
    expect(screen.queryByRole('button', {name: 'Close'})).not.toBeInTheDocument();

    await act(async () => deferred.resolve({status: 200}));
    expect(onSuccess).toHaveBeenCalledTimes(1);
});

test('ignores stale failure and finally after reopening for a newer graph request', async () => {
    const requestA = createDeferred();
    const requestB = createDeferred();
    const onConfirmA = jest.fn().mockReturnValue(requestA.promise);
    const onConfirmB = jest.fn().mockReturnValue(requestB.promise);
    const onSuccess = jest.fn();
    const {rerender, props} = renderModal({
        graph: 'graph-a',
        onConfirm: onConfirmA,
        onSuccess,
    });

    userEvent.type(screen.getByPlaceholderText('Graph name'), 'graph-a');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));
    expect(onConfirmA).toHaveBeenCalledTimes(1);

    rerender(<ClearGraphConfirmModal {...props} open={false} graph='graph-a' />);
    rerender(
        <ClearGraphConfirmModal
            {...props}
            open
            graph='graph-b'
            mode='schema-data'
            onConfirm={onConfirmB}
        />
    );
    userEvent.type(screen.getByPlaceholderText('Graph name'), 'graph-b');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));
    expect(onConfirmB).toHaveBeenCalledTimes(1);

    await act(async () => requestA.resolve({status: 500, message: 'stale boom'}));

    expect(screen.queryByText('stale boom')).not.toBeInTheDocument();
    expect(screen.getByText('Graph: graph-b')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeDisabled();
    expect(onSuccess).not.toHaveBeenCalled();

    await act(async () => requestB.resolve({status: 200}));
    expect(onSuccess).toHaveBeenCalledTimes(1);
});

test('ignores stale success after reopening for a newer graph', async () => {
    const requestA = createDeferred();
    const onConfirmA = jest.fn().mockReturnValue(requestA.promise);
    const onSuccess = jest.fn();
    const {rerender, props} = renderModal({
        graph: 'graph-a',
        onConfirm: onConfirmA,
        onSuccess,
    });

    userEvent.type(screen.getByPlaceholderText('Graph name'), 'graph-a');
    userEvent.click(screen.getByRole('button', {name: 'Clear graph'}));

    rerender(<ClearGraphConfirmModal {...props} open={false} graph='graph-a' />);
    rerender(<ClearGraphConfirmModal {...props} open graph='graph-b' />);

    await act(async () => requestA.resolve({status: 200}));

    expect(onSuccess).not.toHaveBeenCalled();
    expect(screen.getByText('Graph: graph-b')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Graph name')).toHaveValue('');
});
