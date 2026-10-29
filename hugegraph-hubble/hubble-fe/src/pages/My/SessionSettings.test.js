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
import SessionSettings from './SessionSettings';
import * as api from '../../api';

jest.mock('react-i18next', () => ({useTranslation: () => ({t: key => key})}));
jest.mock('../../api', () => ({auth: {
    getSessionSettings: jest.fn(), updateSessionSettings: jest.fn(),
}}));
const settings = seconds => ({status: 200, data: {
    idle_timeout_seconds: seconds, min_idle_seconds: 900, max_idle_seconds: 604800,
}});
beforeEach(() => {
    jest.clearAllMocks();
    window.matchMedia = jest.fn().mockReturnValue({matches: false,
        addListener: jest.fn(), removeListener: jest.fn()});
    api.auth.getSessionSettings.mockResolvedValue(settings(172800));
});
test('loads the actual default and applies the selected minutes to this session', async () => {
    api.auth.updateSessionSettings.mockResolvedValue(settings(3600));
    render(<SessionSettings />);
    const input = screen.getByRole('spinbutton', {name: 'my.session.minutes'});
    await waitFor(() => expect(input).toHaveValue('2880'));
    await userEvent.clear(input);
    await userEvent.type(input, '60');
    await userEvent.click(screen.getByRole('button', {name: 'common.action.save'}));
    expect(api.auth.updateSessionSettings).toHaveBeenCalledWith(3600);
    await waitFor(() => expect(screen.getByRole('button', {name: 'common.action.save'})).toBeDisabled());
});
test('keeps an unavailable settings error visible and supports retry', async () => {
    api.auth.getSessionSettings.mockRejectedValueOnce(new Error('offline'));
    render(<SessionSettings />);
    expect(await screen.findByText('my.session.unavailable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', {name: 'my.load.retry'}));
    await waitFor(() => expect(screen.getByRole('spinbutton')).toHaveValue('2880'));
    expect(screen.queryByText('my.session.unavailable')).not.toBeInTheDocument();
});
test('keeps the pending value on failed save so the user can retry', async () => {
    api.auth.updateSessionSettings.mockRejectedValueOnce(new Error('offline'));
    render(<SessionSettings />);
    const input = screen.getByRole('spinbutton');
    await waitFor(() => expect(input).toHaveValue('2880'));
    await userEvent.clear(input);
    await userEvent.type(input, '60');
    const save = screen.getByRole('button', {name: 'common.action.save'});
    await userEvent.click(save);
    await waitFor(() => expect(save).toBeEnabled());
    expect(input).toHaveValue('60');
});


test('ignores a previous identity settings response after the session panel remounts', async () => {
    let finishOld;
    api.auth.getSessionSettings.mockReturnValueOnce(new Promise(resolve => { finishOld = resolve; }));
    const {rerender} = render(<SessionSettings key='old-user' />);
    api.auth.getSessionSettings.mockResolvedValueOnce(settings(3600));
    rerender(<SessionSettings key='new-user' />);
    await waitFor(() => expect(screen.getByRole('spinbutton')).toHaveValue('60'));
    await act(async () => finishOld(settings(172800)));
    expect(screen.getByRole('spinbutton')).toHaveValue('60');
});
