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

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ViewLayer from './ViewLayer';
import * as api from '../../../api';

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key}),
}));

jest.mock('../../../api', () => ({
    manage: {getTaskDetail: jest.fn()},
}));

jest.mock('react-json-view', () => ({src}) => <div>{src.name}</div>);

const deferred = () => {
    let resolve;
    let reject;
    const promise = new Promise((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return {promise, resolve, reject};
};

beforeEach(() => {
    jest.clearAllMocks();
    window.matchMedia = jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        addListener: jest.fn(),
        removeListener: jest.fn(),
    }));
});

test('clears stale task data and offers retry for the current task', async () => {
    const taskB = deferred();
    api.manage.getTaskDetail
        .mockResolvedValueOnce({status: 200, data: {name: 'Task A'}})
        .mockReturnValueOnce(taskB.promise)
        .mockResolvedValueOnce({status: 200, data: {name: 'Task B'}});

    const {rerender} = render(
        <ViewLayer visible task_id='A' onCancel={jest.fn()} />
    );
    expect(await screen.findByText('Task A')).toBeInTheDocument();

    rerender(<ViewLayer visible task_id='B' onCancel={jest.fn()} />);
    expect(screen.queryByText('Task A')).not.toBeInTheDocument();

    taskB.reject(new Error('down'));
    expect(await screen.findByText('task.view.unavailable')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'task.view.retry'}));
    await waitFor(() => expect(screen.getByText('Task B')).toBeInTheDocument());
});
