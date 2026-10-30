/*
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
import {CloneLayer} from './EditLayer';
import * as api from '../../api';

jest.mock('react-i18next', () => ({useTranslation: () => ({t: key => key})}));
jest.mock('../../utils/rules', () => ({
    required: () => ({required: true}), isName: {}, isPropertyName: {},
}));
jest.mock('../../api', () => ({manage: {getGraph: jest.fn(), cloneGraph: jest.fn()}}));

beforeEach(() => {
    jest.clearAllMocks();
    window.matchMedia = jest.fn().mockReturnValue({matches: false,
        addListener: jest.fn(), removeListener: jest.fn()});
    api.manage.getGraph.mockResolvedValue({status: 200, data: {nickname: 'Original'}});
});

test('submits only schema cloning and leaves unsupported data cloning unavailable', async () => {
    api.manage.cloneGraph.mockResolvedValue({status: 200, data: {name: 'copy'}});
    const refresh = jest.fn();
    render(<CloneLayer open graphspace='DEFAULT' graph='source' onCancel={jest.fn()} refresh={refresh} />);
    expect(screen.getByText('graph.clone.schema_only')).toBeInTheDocument();
    expect(screen.queryByText('graph.clone.schema_data')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('graph.clone.name'), 'copy');
    const nickname = screen.getByLabelText('graph.clone.nickname_label');
    await userEvent.clear(nickname);
    await userEvent.type(nickname, 'Copy');
    await userEvent.click(screen.getByRole('button', {name: /ok/i}));
    await waitFor(() => expect(api.manage.cloneGraph).toHaveBeenCalledWith('DEFAULT', 'source', {
        graphspace: 'DEFAULT', name: 'copy', nickname: 'Copy', load_data: 0,
    }, {suppressBusinessErrorToast: true}));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
});

test('keeps the dialog open after an unsupported or failed server request', async () => {
    api.manage.cloneGraph.mockRejectedValue(new Error('404'));
    const onCancel = jest.fn();
    render(<CloneLayer open graphspace='DEFAULT' graph='source' onCancel={onCancel} refresh={jest.fn()} />);
    await userEvent.type(screen.getByLabelText('graph.clone.name'), 'copy');
    const nickname = screen.getByLabelText('graph.clone.nickname_label');
    await userEvent.clear(nickname);
    await userEvent.type(nickname, 'Copy');
    await userEvent.click(screen.getByRole('button', {name: /ok/i}));
    await waitFor(() => expect(api.manage.cloneGraph).toHaveBeenCalled());
    expect(onCancel).not.toHaveBeenCalled();
    expect(await screen.findByText('graph.clone.failed')).toBeInTheDocument();
});
