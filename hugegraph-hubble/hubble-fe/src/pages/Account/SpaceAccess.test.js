/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {
    act,
    fireEvent,
    render,
    screen,
    waitFor,
    within,
} from '@testing-library/react';
import SpaceAccess, {loadAllPages, rolesPreset} from './SpaceAccess';
import * as api from '../../api';

let mockAuthContext;

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key}),
}));

jest.mock('../../auth/AuthContext', () => ({
    useAuthContext: () => mockAuthContext,
}));

jest.mock('../../api', () => ({
    auth: {
        getSpaceMembers: jest.fn(),
        getSpaceAdmins: jest.fn(),
        setSpaceAdmin: jest.fn(),
        removeSpaceAdmin: jest.fn(),
        setSpacePreset: jest.fn(),
        getSpaceRoles: jest.fn(),
        getSpaceTargets: jest.fn(),
        getSpaceAccesses: jest.fn(),
        addSpaceMember: jest.fn(),
        updateSpaceMember: jest.fn(),
        deleteSpaceMember: jest.fn(),
        addSpaceRole: jest.fn(),
        updateSpaceRole: jest.fn(),
        deleteSpaceRole: jest.fn(),
        addSpaceTarget: jest.fn(),
        updateSpaceTarget: jest.fn(),
        deleteSpaceTarget: jest.fn(),
        saveSpaceAccess: jest.fn(),
        deleteSpaceAccess: jest.fn(),
        getAllUserList: jest.fn(),
    },
    manage: {
        getGraphSpaceList: jest.fn(),
    },
}));

const deferred = () => {
    let resolve;
    const promise = new Promise(resolvePromise => {
        resolve = resolvePromise;
    });
    return {promise, resolve};
};

const page = records => ({status: 200, data: {records, total: records.length}});

const setResponses = ({members = [], roles = [], targets = [], accesses = []} = {}) => {
    api.auth.getSpaceMembers.mockResolvedValue(page(members));
    api.auth.getSpaceAdmins.mockResolvedValue(page([]));
    api.auth.getSpaceRoles.mockResolvedValue(page(roles));
    api.auth.getSpaceTargets.mockResolvedValue(page(targets));
    api.auth.getSpaceAccesses.mockResolvedValue({status: 200, data: accesses});
};

test('requires explicit preset for mixed or legacy member roles', () => {
    expect(rolesPreset([
        {role_name: 'observer'},
        {role_name: 'custom-role'},
    ])).toBeNull();
    expect(rolesPreset([
        {role_name: 'observer'},
        {role_name: 'analyst'},
    ])).toBeNull();
    expect(rolesPreset([
        {role_name: 'observer', permission_preset: 'GS_READ_ONLY'},
    ])).toBe('GS_READ_ONLY');
});

test('keeps graphspace administrator selected with member roles', () => {
    expect(rolesPreset([
        {role_name: 'analyst', permission_preset: 'GS_READ_WRITE'},
        {permission_preset: 'GS_ADMIN'},
    ])).toBe('GS_ADMIN');
    expect(rolesPreset([
        {role_name: 'analyst'},
        {role_name: 'custom-role', permissions: ['delete']},
    ])).toBeNull();
    expect(rolesPreset([
        {role_name: 'analyst', permissions: ['write', 'delete']},
    ])).toBeNull();
    expect(rolesPreset([
        {role_name: 'custom-role'},
        {permission_preset: 'GS_ADMIN'},
    ])).toBeNull();
});

test('loads every page without silently truncating member-management data', async () => {
    const firstPage = Array.from({length: 500}, (_, index) => ({id: index}));
    const request = jest.fn()
        .mockResolvedValueOnce({
            status: 200,
            data: {records: firstPage, total: '501'},
        })
        .mockResolvedValueOnce({
            status: 200,
            data: {records: [{id: 500}], total: 501},
        });

    const response = await loadAllPages(request, {params: {query: ''}});

    expect(response.data.records).toHaveLength(501);
    expect(request).toHaveBeenNthCalledWith(1, {
        query: '',
        page_no: 1,
        page_size: 500,
    }, expect.any(Object));
    expect(request).toHaveBeenNthCalledWith(2, {
        query: '',
        page_no: 2,
        page_size: 500,
    }, expect.any(Object));
});

test('continues after short pages while declared records remain', async () => {
    const page = offset => Array.from({length: 200}, (_, index) => ({
        id: offset + index,
    }));
    const request = jest.fn()
        .mockResolvedValueOnce({
            status: 200,
            data: {records: page(0), total: 501},
        })
        .mockResolvedValueOnce({
            status: 200,
            data: {records: page(200), total: 501},
        })
        .mockResolvedValueOnce({
            status: 200,
            data: {records: page(400).slice(0, 101), total: 501},
        });

    const response = await loadAllPages(request);

    expect(response.data.records).toHaveLength(501);
    expect(request).toHaveBeenCalledTimes(3);
});

test('treats blank total as absent instead of zero', async () => {
    const firstPage = Array.from({length: 500}, (_, index) => ({id: index}));
    const request = jest.fn()
        .mockResolvedValueOnce({
            status: 200,
            data: {records: firstPage, total: '   '},
        })
        .mockResolvedValueOnce({
            status: 200,
            data: {records: [{id: 500}], total: '   '},
        });

    const response = await loadAllPages(request);

    expect(response.data.records).toHaveLength(501);
    expect(request).toHaveBeenCalledTimes(2);
});

test('fails when a paged response ends before its declared total', async () => {
    const request = jest.fn()
        .mockResolvedValueOnce({
            status: 200,
            data: {records: [{id: 0}], total: 2},
        })
        .mockResolvedValueOnce({
            status: 200,
            data: {records: [], total: 2},
        });

    await expect(loadAllPages(request))
        .rejects.toThrow('Record list ended before its declared total');
});

test('shows legacy role names without treating them as presets', async () => {
    setResponses({
        members: [{
            user_id: 'alice',
            user_name: 'alice',
            roles: [{role_id: 'custom', role_name: 'analyst'}],
        }],
    });

    render(<SpaceAccess />);

    expect(await screen.findByText(
        'analyst · account.permission_preset.legacy_custom'
    )).toBeInTheDocument();
});

beforeEach(() => {
    jest.clearAllMocks();
    mockAuthContext = {
        context: {
            context_version: 'v1',
            actions: {
                members: ['read', 'add', 'remove'],
                roles: ['read', 'create', 'update', 'delete'],
                authorizations: ['read', 'grant', 'revoke'],
            },
            scopes: {
                all_graphspaces: false,
                admin_graphspaces: ['SPACE_A'],
            },
        },
    };
    setResponses({
        members: [{user_id: 'alice', user_name: 'alice', roles: []}],
        roles: [{id: 'reader-id', role_name: 'reader'}],
    });
    window.matchMedia = jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        addListener: jest.fn(),
        removeListener: jest.fn(),
    }));
});

test('uses only path-scoped APIs for a space administrator', async () => {
    render(<SpaceAccess />);

    expect((await screen.findAllByText('alice')).length).toBeGreaterThan(0);
    expect(api.auth.getSpaceMembers).toHaveBeenCalledWith(
        'SPACE_A', expect.any(Object), expect.any(Object)
    );
    expect(api.auth.getSpaceRoles).not.toHaveBeenCalled();
    expect(api.auth.getSpaceAdmins).toHaveBeenCalledWith(
        'SPACE_A', expect.any(Object), expect.any(Object)
    );
    expect(api.auth.getSpaceTargets).not.toHaveBeenCalled();
    expect(api.auth.getSpaceAccesses).not.toHaveBeenCalled();
    expect(api.auth.getAllUserList).not.toHaveBeenCalled();
    expect(api.manage.getGraphSpaceList).not.toHaveBeenCalled();
});

test('drops late scoped responses after the authorization context changes', async () => {
    const requestA = deferred();
    api.auth.getSpaceMembers.mockReturnValueOnce(requestA.promise);
    const view = render(<SpaceAccess />);
    await waitFor(() => expect(api.auth.getSpaceMembers).toHaveBeenCalledWith(
        'SPACE_A', expect.any(Object), expect.any(Object)
    ));

    mockAuthContext = {
        context: {
            ...mockAuthContext.context,
            context_version: 'v2',
            scopes: {
                all_graphspaces: false,
                admin_graphspaces: ['SPACE_B'],
            },
        },
    };
    api.auth.getSpaceMembers.mockResolvedValueOnce(page([
        {user_id: 'bob', user_name: 'bob', roles: []},
    ]));
    view.rerender(<SpaceAccess />);

    expect((await screen.findAllByText('bob')).length).toBeGreaterThan(0);
    await act(async () => requestA.resolve(page([
        {user_id: 'alice', user_name: 'alice', roles: []},
    ])));
    expect(screen.queryByText('alice')).not.toBeInTheDocument();
    expect(screen.getAllByText('bob').length).toBeGreaterThan(0);
});

test('does not infer mutations when the server grants read-only actions', async () => {
    mockAuthContext = {
        context: {
            ...mockAuthContext.context,
            actions: {
                members: ['read'],
                roles: ['read'],
                authorizations: ['read'],
            },
        },
    };

    render(<SpaceAccess />);

    expect((await screen.findAllByText('alice')).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', {
        name: 'account.space_access.member.add',
    })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {
        name: 'common.action.delete',
    })).not.toBeInTheDocument();
});

test('submits only the selected preset when adding a member', async () => {
    api.auth.setSpacePreset.mockResolvedValue({status: 200});
    render(<SpaceAccess />);

    await screen.findAllByText('alice');
    fireEvent.click(screen.getByRole('button', {
        name: 'account.space_access.member.add',
    }));
    const textboxes = screen.getAllByRole('textbox');
    fireEvent.change(textboxes[textboxes.length - 1], {
        target: {value: 'bob'},
    });
    const comboboxes = screen.getAllByRole('combobox');
    fireEvent.mouseDown(comboboxes[comboboxes.length - 1]);
    fireEvent.click(screen.getByText('account.permission_preset.GS_READ_WRITE'));
    fireEvent.click(screen.getByRole('button', {name: 'OK'}));

    await waitFor(() => expect(api.auth.setSpacePreset).toHaveBeenCalledWith(
        'SPACE_A', 'bob', 'bob', 'GS_READ_WRITE', expect.any(Object)));
});

test('uses the preset API for GS admin', async () => {
    mockAuthContext.context.scopes = {
        all_graphspaces: true,
        admin_graphspaces: [],
    };
    api.manage.getGraphSpaceList.mockResolvedValue(page([
        {name: 'SPACE_A'},
    ]));
    api.auth.setSpacePreset.mockResolvedValue({status: 200});
    render(<SpaceAccess />);

    await screen.findAllByText('alice');
    fireEvent.click(screen.getByRole('button', {
        name: 'account.space_access.member.add',
    }));
    const textboxes = screen.getAllByRole('textbox');
    fireEvent.change(textboxes[textboxes.length - 1], {
        target: {value: 'bob'},
    });
    const comboboxes = screen.getAllByRole('combobox');
    fireEvent.mouseDown(comboboxes[comboboxes.length - 1]);
    fireEvent.click(screen.getByText('account.permission_preset.GS_ADMIN'));
    fireEvent.click(screen.getByRole('button', {name: 'OK'}));

    await waitFor(() => expect(api.auth.setSpacePreset).toHaveBeenCalledWith(
        'SPACE_A', 'bob', 'bob', 'GS_ADMIN', expect.any(Object)));
    expect(api.auth.addSpaceMember).not.toHaveBeenCalled();
});

test('does not let a space administrator manage other space administrators',
    async () => {
        api.auth.getSpaceAdmins.mockResolvedValueOnce(page([{
            id: 'admin-id',
            name: 'space-admin',
        }]));

        render(<SpaceAccess />);

        const row = (await screen.findByText('space-admin')).closest('tr');
        expect(within(row).queryByRole('button', {
            name: 'common.action.edit',
        })).not.toBeInTheDocument();
        expect(within(row).queryByRole('button', {
            name: 'common.action.delete',
        })).not.toBeInTheDocument();
    });

test('shows GraphSpace administrators through the preset model', async () => {
    api.auth.getSpaceAdmins.mockResolvedValueOnce(page([{
        id: 'admin-id',
        name: 'space-admin',
    }]));

    render(<SpaceAccess />);

    expect(await screen.findByText('space-admin')).toBeInTheDocument();
    expect(screen.getByText('account.permission_preset.GS_ADMIN'))
        .toBeInTheDocument();
});
