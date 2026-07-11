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

import {act, fireEvent, render, screen} from '@testing-library/react';
import AnalysisHome from './index';
import GraphAnalysisContext from '../../Context';
import * as api from '../../../api';

jest.mock('../../../api', () => ({
    analysis: {
        getExecutionLogs: jest.fn(),
        fetchFavoriteQueries: jest.fn(),
        getGraphData: jest.fn(),
        getExecutionQuery: jest.fn(),
        getCypherExecutionQuery: jest.fn(),
        getExecutionTask: jest.fn(),
        getCypherTask: jest.fn(),
    },
    manage: {
        getMetaEdgeList: jest.fn(),
        getMetaVertexList: jest.fn(),
        getMetaPropertyList: jest.fn(),
    },
}));
jest.mock('../QueryBar/Home', () => props => (
    <div>
        <button onClick={() => props.onTabsChange('Text2GQL')}>Natural language</button>
        <button onClick={() => props.onExecute(props.activeTab)}>Run current</button>
    </div>
));
jest.mock('../QueryResult/Home', () => ({queryStatus, queryMessage}) => (
    <div>query result {queryStatus} {queryMessage}</div>
));
jest.mock('../LogsDetail/Home', () => () => <div>query history</div>);
jest.mock('react-i18next', () => ({useTranslation: () => ({t: key => key})}));

const okList = {status: 200, data: {records: [], total: 0}};

beforeEach(() => {
    api.analysis.getExecutionLogs.mockResolvedValue(okList);
    api.analysis.fetchFavoriteQueries.mockResolvedValue(okList);
    api.analysis.getGraphData.mockResolvedValue({
        status: 200,
        data: {vertexcount: 0, edgecount: 0},
    });
    api.manage.getMetaEdgeList.mockResolvedValue(okList);
    api.manage.getMetaVertexList.mockResolvedValue(okList);
    api.manage.getMetaPropertyList.mockResolvedValue(okList);
});

afterEach(() => jest.clearAllMocks());

it('makes no backend request when switching to the Text2GQL placeholder', async () => {
    render(
        <GraphAnalysisContext.Provider value={{graphSpace: 'DEFAULT', graph: 'hugegraph'}}>
            <AnalysisHome />
        </GraphAnalysisContext.Provider>
    );
    await act(async () => Promise.resolve());
    Object.values(api.analysis).forEach(mock => mock.mockClear());
    Object.values(api.manage).forEach(mock => mock.mockClear());

    fireEvent.click(screen.getByRole('button', {name: 'Natural language'}));
    await act(async () => Promise.resolve());
    fireEvent.click(screen.getByRole('button', {name: 'Run current'}));
    await act(async () => Promise.resolve());

    Object.values(api.analysis).forEach(mock => expect(mock).not.toHaveBeenCalled());
    Object.values(api.manage).forEach(mock => expect(mock).not.toHaveBeenCalled());
    expect(screen.queryByText('query result')).not.toBeInTheDocument();
    expect(screen.queryByText('query history')).not.toBeInTheDocument();
});

it('turns a rejected synchronous query into a recoverable failed result', async () => {
    api.analysis.getExecutionQuery.mockRejectedValue(new Error('offline'));
    render(
        <GraphAnalysisContext.Provider value={{graphSpace: 'DEFAULT', graph: 'hugegraph'}}>
            <AnalysisHome />
        </GraphAnalysisContext.Provider>
    );
    await act(async () => Promise.resolve());

    fireEvent.click(screen.getByRole('button', {name: 'Run current'}));

    expect(await screen.findByText(/query result failed/)).toHaveTextContent(
        'analysis.query_result.run_failed_action'
    );
});
