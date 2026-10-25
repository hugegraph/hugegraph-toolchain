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

import {act, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {Modal, message} from 'antd';
import {createContext, startTransition, Suspense, useContext, useState} from 'react';
import PropertyTable from './Property';
import VertexTable from './Vertex';
import EdgeTable from './Edge';
import * as api from '../../api';

jest.mock('react-i18next', () => ({useTranslation: () => ({t: key => key})}));
let mockScope;
let mockTableData;
const MockScopeContext = createContext(null);
jest.mock('react-router-dom', () => ({useParams: () => useContext(MockScopeContext) || mockScope}));
jest.mock('./Property/EditLayer', () => ({EditPropertyLayer: () => null}));
jest.mock('./Vertex/EditLayer', () => ({EditVertexLayer: () => null}));
jest.mock('./Edge/EditLayer', () => ({EditEdgeLayer: () => null}));
jest.mock('./common/useMetaTable', () => () => ({
    data: mockTableData,
    pagination: false,
}));
jest.mock('../../api', () => ({manage: {
    checkMetaProperty: jest.fn(), checkMetaVertex: jest.fn(),
    delMetaProperty: jest.fn(), delMetaVertex: jest.fn(), delMetaEdge: jest.fn(),
    getMetaPropertyList: jest.fn(), getMetaVertexList: jest.fn(), getMetaEdgeList: jest.fn(),
}}));

const tableRow = name => ({name, data_type: 'TEXT', cardinality: 'SINGLE',
    properties: [], primary_keys: [], property_indexes: [], sort_keys: []});

let existing;
beforeEach(() => {
    mockScope = {graphspace: 'DEFAULT', graph: 'audit'};
    mockTableData = [tableRow('audit_label')];
    existing = {Property: [{name: 'audit_label'}], Vertex: [{name: 'audit_label'}], Edge: [{name: 'audit_label'}]};
    jest.clearAllMocks();
    window.matchMedia = jest.fn().mockImplementation(query => ({
        matches: false, media: query, addListener: jest.fn(), removeListener: jest.fn(),
    }));
    api.manage.checkMetaProperty.mockResolvedValue({status: 200,
        data: {audit_label: false, pending_label: false}});
    api.manage.checkMetaVertex.mockResolvedValue({status: 200,
        data: {audit_label: false, pending_label: false}});
    for (const type of ['Property', 'Vertex', 'Edge']) {
        api.manage[`getMeta${type}List`].mockImplementation(async () => ({status: 200,
            data: {records: existing[type], total: existing[type].length}}));
    }
    jest.spyOn(Modal, 'confirm').mockImplementation(() => ({update: jest.fn()}));
    jest.spyOn(message, 'error').mockImplementation(() => {});
    jest.spyOn(message, 'success').mockImplementation(() => {});
});
afterEach(() => jest.restoreAllMocks());

const tables = [
    ['property', PropertyTable, 'delMetaProperty'],
    ['vertex', VertexTable, 'delMetaVertex'],
    ['edge', EdgeTable, 'delMetaEdge'],
];

test.each(tables)('%s selection cannot carry a same-name key into another graph scope',
    async (_name, Table) => {
        const view = render(<Table />);
        const checkbox = within(screen.getByRole('row', {name: /audit_label/})).getByRole('checkbox');
        fireEvent.click(checkbox);
        expect(checkbox).toBeChecked();

        mockScope = {graphspace: 'OTHER', graph: 'audit'};
        view.rerender(<Table />);
        await waitFor(() => expect(checkbox).not.toBeChecked());
    });

test.each(tables)('%s delayed deletion cannot clear the new graph scope selection',
    async (_name, Table, operation) => {
        const type = operation.replace('delMeta', '');
        let resolveList;
        api.manage[`getMeta${type}List`].mockImplementationOnce(() =>
            new Promise(resolve => {
                resolveList = resolve;
            }));
        const view = render(<Table />);
        let row = screen.getByRole('row', {name: /audit_label/});
        let checkbox = within(row).getByRole('checkbox');
        fireEvent.click(checkbox);
        fireEvent.click(within(row).getByRole('button', {name: /^common\.(?:action\.)?delete$/}));
        const close = jest.fn();
        act(() => Modal.confirm.mock.calls[0][0].onOk(close));
        await waitFor(() => expect(resolveList).toBeDefined());

        mockScope = {graphspace: 'OTHER', graph: 'audit'};
        view.rerender(<Table />);
        row = screen.getByRole('row', {name: /audit_label/});
        checkbox = within(row).getByRole('checkbox');
        await waitFor(() => expect(checkbox).not.toBeChecked());
        fireEvent.click(checkbox);
        expect(checkbox).toBeChecked();

        await act(async () => resolveList({status: 200, data: {records: [], total: 0}}));
        await waitFor(() => expect(close).toHaveBeenCalledTimes(1));
        expect(checkbox).toBeChecked();
    });

test.each(tables)('%s suspended scope render cannot invalidate the still-committed deletion scope',
    async (_name, Table, operation) => {
        const type = operation.replace('delMeta', '');
        let resolveList;
        api.manage[`getMeta${type}List`].mockImplementationOnce(() =>
            new Promise(resolve => {
                resolveList = resolve;
            }));
        const pendingScope = new Promise(() => {});
        const attemptedScope = jest.fn();
        const SuspendNewScope = ({scope}) => {
            if (scope.graphspace === 'OTHER') {
                attemptedScope();
                throw pendingScope;
            }
            return null;
        };
        const SwitchableTable = () => {
            const [scope, setScope] = useState(mockScope);
            return <>
                <button onClick={() => startTransition(() =>
                    setScope({graphspace: 'OTHER', graph: 'audit'}))}>Switch graph scope</button>
                <Suspense fallback={<span>Loading graph scope</span>}>
                    <MockScopeContext.Provider value={scope}>
                        <Table />
                        <SuspendNewScope scope={scope} />
                    </MockScopeContext.Provider>
                </Suspense>
            </>;
        };
        render(<SwitchableTable />);
        const row = screen.getByRole('row', {name: /audit_label/});
        const checkbox = within(row).getByRole('checkbox');
        fireEvent.click(checkbox);
        fireEvent.click(within(row).getByRole('button', {name: /^common\.(?:action\.)?delete$/}));
        const close = jest.fn();
        act(() => Modal.confirm.mock.calls[0][0].onOk(close));
        await waitFor(() => expect(resolveList).toBeDefined());

        fireEvent.click(screen.getByRole('button', {name: 'Switch graph scope'}));
        await waitFor(() => expect(attemptedScope).toHaveBeenCalled());
        // A transition keeps the old table committed while the next scope suspends.
        expect(screen.queryByText('Loading graph scope')).not.toBeInTheDocument();
        expect(row).toBeVisible();
        expect(checkbox).toBeChecked();

        await act(async () => resolveList({status: 200, data: {records: [], total: 0}}));
        await waitFor(() => expect(close).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(checkbox).not.toBeChecked());
    });

test.each(tables)('%s batch completion clears the checkbox of a same-name replacement',
    async (_name, Table, operation) => {
        api.manage[operation].mockImplementation(async () => {
            existing[operation.replace('delMeta', '')] = [];
            return {status: 200};
        });
        render(<Table />);
        const checkbox = within(screen.getByRole('row', {name: /audit_label/})).getByRole('checkbox');
        fireEvent.click(checkbox);
        expect(checkbox).toBeChecked();
        fireEvent.click(screen.getByRole('button', {name: /^(?:schema\.)?common\.batch_delete$/}));
        const close = jest.fn();
        act(() => Modal.confirm.mock.calls[0][0].onOk(close));

        await waitFor(() => expect(close).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(checkbox).not.toBeChecked());
    });

test.each(tables)('%s single-row completion removes a selected same-name replacement',
    async (_name, Table, operation) => {
        api.manage[operation].mockImplementation(async () => {
            existing[operation.replace('delMeta', '')] = [];
            return {status: 200};
        });
        render(<Table />);
        const row = screen.getByRole('row', {name: /audit_label/});
        const checkbox = within(row).getByRole('checkbox');
        fireEvent.click(checkbox);
        expect(checkbox).toBeChecked();
        fireEvent.click(within(row).getByRole('button', {name: /^common\.(?:action\.)?delete$/}));
        const close = jest.fn();
        act(() => Modal.confirm.mock.calls[0][0].onOk(close));

        await waitFor(() => expect(close).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(checkbox).not.toBeChecked());
    });

test.each(tables)('%s partial batch clears only the names confirmed absent',
    async (_name, Table, operation) => {
        const type = operation.replace('delMeta', '');
        mockTableData = [tableRow('audit_label'), tableRow('pending_label')];
        existing[type] = [{name: 'audit_label'}, {name: 'pending_label'}];
        api.manage[operation].mockImplementation(async () => {
            existing[type] = [{name: 'pending_label'}];
            return {status: 500};
        });
        render(<Table />);
        const deletedRow = screen.getByRole('row', {name: /audit_label/});
        const pendingRow = screen.getByRole('row', {name: /pending_label/});
        const deletedCheckbox = within(deletedRow).getByRole('checkbox');
        const pendingCheckbox = within(pendingRow).getByRole('checkbox');
        fireEvent.click(deletedCheckbox);
        fireEvent.click(pendingCheckbox);
        expect(deletedCheckbox).toBeChecked();
        expect(pendingCheckbox).toBeChecked();
        fireEvent.click(screen.getByRole('button', {name: /^(?:schema\.)?common\.batch_delete$/}));
        const close = jest.fn();
        act(() => Modal.confirm.mock.calls[0][0].onOk(close));

        await waitFor(() => expect(api.manage[operation]).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(deletedCheckbox).not.toBeChecked());
        expect(pendingCheckbox).toBeChecked();
        expect(close).not.toHaveBeenCalled();
    });

test.each(tables)('%s deletion keeps confirmation retryable on business and transport failure',
    async (_name, Table, operation) => {
        api.manage[operation]
            .mockResolvedValueOnce({status: 500})
            .mockRejectedValueOnce(new Error('network unavailable'))
            .mockImplementationOnce(async () => {
                existing[operation.replace('delMeta', '')] = [];
                return {status: 200};
            });
        await act(async () => { render(<Table />); });
        fireEvent.click(screen.getByRole('button', {name: /^common\.(?:action\.)?delete$/}));
        await waitFor(() => expect(Modal.confirm).toHaveBeenCalledTimes(1));
        const {onOk} = Modal.confirm.mock.calls[0][0];

        const close = jest.fn();
        act(() => { onOk(close); onOk(close); });
        await waitFor(() => expect(message.error).toHaveBeenCalledTimes(1));
        expect(api.manage[operation]).toHaveBeenCalledTimes(1);
        expect(close).not.toHaveBeenCalled();
        expect(message.success).not.toHaveBeenCalled();
        await waitFor(() => expect(Modal.confirm.mock.results[0].value.update)
            .toHaveBeenLastCalledWith(expect.objectContaining({
                okButtonProps: {loading: false, disabled: false},
            })));
        const retry = () => {
            if (_name === 'property') act(() => onOk(close));
            else {
                const content = Modal.confirm.mock.results[0].value.update.mock.calls.at(-1)[0].content;
                const retryView = render(content);
                fireEvent.click(screen.getByRole('button', {name: 'schema.delete_retry'}));
                retryView.unmount();
            }
        };
        retry();
        await waitFor(() => expect(message.error).toHaveBeenCalledTimes(2));
        expect(close).not.toHaveBeenCalled();
        expect(message.success).not.toHaveBeenCalled();
        await waitFor(() => expect(Modal.confirm.mock.results[0].value.update)
            .toHaveBeenLastCalledWith(expect.objectContaining({okButtonProps: {loading: false, disabled: false}})));
        retry();
        await waitFor(() => expect(close).toHaveBeenCalledTimes(1));
        expect(api.manage[operation]).toHaveBeenLastCalledWith('DEFAULT', 'audit',
            {names: ['audit_label'], skip_using: false}, {suppressBusinessErrorToast: true});
        expect(message.success).toHaveBeenCalledTimes(1);
    });
