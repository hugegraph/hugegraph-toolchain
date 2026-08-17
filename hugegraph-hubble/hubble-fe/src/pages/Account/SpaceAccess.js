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
    Alert,
    Button,
    Form,
    Input,
    message,
    Modal,
    Select,
    Space,
    Table,
    Tabs,
    Tag,
    Typography,
} from 'antd';
import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import * as api from '../../api';
import TableHeader from '../../components/TableHeader';
import {useAuthContext} from '../../auth/AuthContext';
import {PERMISSION_PRESETS} from './permissionPresets';

const PAGE_ERROR_CONFIG = {suppressBusinessErrorToast: true};
const PAGE_PARAMS = {query: '', page_no: 1, page_size: 200};
const responseRecords = response => response?.data?.records ?? [];
const showMutationError = (error, t) => {
    const response = error?.response ?? error;
    const detail = response?.data?.message ?? response?.message;
    message.error(detail
        ? `${t('common.msg.operation_failed')} (${detail})`
        : t('common.msg.operation_failed'));
};
const adminRole = {
    role_id: PERMISSION_PRESETS.GS_ADMIN,
    permission_preset: PERMISSION_PRESETS.GS_ADMIN,
};

const mergeMembersAndAdmins = (members, admins) => {
    const rows = new Map(members.map(member => [
        member.user_id,
        {...member, member_roles: member.roles ?? []},
    ]));
    admins.forEach(admin => {
        const userId = admin.id ?? admin.user_id;
        const existing = rows.get(userId) ?? {};
        rows.set(userId, {
            ...existing,
            user_id: userId,
            user_name: admin.name ?? admin.user_name ?? existing.user_name,
            member_roles: existing.roles ?? existing.member_roles ?? [],
            roles: [...(existing.roles ?? existing.member_roles ?? []), adminRole],
            is_space_admin: true,
        });
    });
    return Array.from(rows.values());
};

const rolePreset = role => {
    const explicit = role?.permission_preset ?? role?.permissionPreset;
    return Object.values(PERMISSION_PRESETS).includes(explicit)
        ? explicit
        : null;
};

const rolesPreset = roles => {
    const values = roles ?? [];
    const presets = values.map(rolePreset);
    if (presets.some(preset => preset === null)) {
        return null;
    }
    if (values.some(role => (role?.permission_preset ?? role?.permissionPreset)
                            === PERMISSION_PRESETS.GS_ADMIN)) {
        return PERMISSION_PRESETS.GS_ADMIN;
    }
    return values.length > 0 && presets.every(Boolean) && new Set(presets).size === 1 ? presets[0] : null;
};

const roleLabel = (role, t) => {
    const preset = rolePreset(role);
    if (preset) {
        return t(`account.permission_preset.${preset}`);
    }
    const name = role?.role_name ?? role?.name;
    const legacy = t('account.permission_preset.legacy_custom');
    return name ? `${name} · ${legacy}` : legacy;
};

const RowAction = ({row, onAction, children}) => {
    const handleClick = useCallback(() => onAction(row), [onAction, row]);

    return <Button type="link" onClick={handleClick}>{children}</Button>;
};

const useScopedResource = (graphSpace, contextVersion, loader, selector) => {
    const request = useRef(null);
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);
    const [revision, setRevision] = useState(0);

    const retry = useCallback(() => setRevision(value => value + 1), []);

    useEffect(() => {
        if (!graphSpace) {
            request.current = null;
            setData([]);
            setLoading(false);
            setError(false);
            return undefined;
        }
        const token = Symbol(graphSpace);
        request.current = token;
        setData([]);
        setLoading(true);
        setError(false);
        loader(graphSpace).then(response => {
            if (request.current !== token) {
                return;
            }
            if (response?.status !== 200) {
                setError(true);
                return;
            }
            setData(selector(response));
        }).catch(() => {
            if (request.current === token) {
                setError(true);
            }
        }).finally(() => {
            if (request.current === token) {
                setLoading(false);
            }
        });
        return () => {
            if (request.current === token) {
                request.current = null;
            }
        };
    }, [contextVersion, graphSpace, loader, revision, selector]);

    return {data, loading, error, retry};
};

const ErrorAlert = ({error, retry, t}) => (error ? (
    <Alert
        type="error"
        showIcon
        message={t('account.space_access.load_error')}
        action={(
            <Button size="small" onClick={retry}>
                {t('common.action.retry')}
            </Button>
        )}
    />
) : null);

const SpaceAccess = () => {
    const {t} = useTranslation();
    const {context} = useAuthContext();
    const contextVersion = context?.context_version;
    const scopes = context?.scopes ?? {};
    const memberActions = context?.actions?.members ?? [];
    const canAddMember = memberActions.includes('add');
    const canRemoveMember = memberActions.includes('remove');
    const [selectedSpace, setSelectedSpace] = useState('');
    const [allSpaces, setAllSpaces] = useState([]);
    const [spacesLoading, setSpacesLoading] = useState(false);
    const [spacesError, setSpacesError] = useState(false);
    const [spacesRevision, setSpacesRevision] = useState(0);
    const spacesRequest = useRef(null);
    const [memberDialog, setMemberDialog] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [memberForm] = Form.useForm();

    const scopedSpaces = useMemo(
        () => scopes.admin_graphspaces ?? [],
        [scopes.admin_graphspaces]
    );

    useEffect(() => {
        if (!scopes.all_graphspaces) {
            spacesRequest.current = null;
            setAllSpaces([]);
            setSpacesLoading(false);
            setSpacesError(false);
            return undefined;
        }
        const token = Symbol('graphspaces');
        spacesRequest.current = token;
        setSpacesLoading(true);
        setSpacesError(false);
        api.manage.getGraphSpaceList(PAGE_PARAMS, PAGE_ERROR_CONFIG)
            .then(response => {
                if (spacesRequest.current !== token) {
                    return;
                }
                if (response?.status !== 200) {
                    setSpacesError(true);
                    return;
                }
                setAllSpaces(responseRecords(response).map(space => space.name));
            })
            .catch(() => {
                if (spacesRequest.current === token) {
                    setSpacesError(true);
                }
            })
            .finally(() => {
                if (spacesRequest.current === token) {
                    setSpacesLoading(false);
                }
            });
        return () => {
            if (spacesRequest.current === token) {
                spacesRequest.current = null;
            }
        };
    }, [contextVersion, scopes.all_graphspaces, spacesRevision]);

    const spaces = scopes.all_graphspaces ? allSpaces : scopedSpaces;
    const graphSpace = spaces.includes(selectedSpace) ? selectedSpace : spaces[0];
    const loadMembers = useCallback(space => api.auth.getSpaceMembers(
        space, PAGE_PARAMS, PAGE_ERROR_CONFIG
    ), []);
    const loadAdmins = useCallback(space => api.auth.getSpaceAdmins(
        space, PAGE_PARAMS, PAGE_ERROR_CONFIG
    ), []);
    const members = useScopedResource(
        graphSpace, contextVersion, loadMembers, responseRecords
    );
    const admins = useScopedResource(graphSpace, contextVersion, loadAdmins, responseRecords
    );
    const visibleMembers = {
        data: mergeMembersAndAdmins(members.data, admins.data),
        loading: members.loading || admins.loading,
        error: members.error || admins.error,
        retry: () => {
            members.retry();
            admins.retry();
        },
    };

    const refreshAll = useCallback(() => {
        members.retry();
        admins.retry();
    }, [admins, members]);

    const runMutation = useCallback(async (operation, close) => {
        if (submitting) {
            return;
        }
        setSubmitting(true);
        try {
            const response = await operation();
            if (response?.status !== 200) {
                showMutationError(response, t);
                return;
            }
            message.success(t('common.msg.success'));
            close();
            refreshAll();
        }
        catch (error) {
            showMutationError(error, t);
        }
        finally {
            setSubmitting(false);
        }
    }, [refreshAll, submitting, t]);

    const openMember = useCallback(row => {
        memberForm.setFieldsValue({
            user_id: row?.user_id,
            permission_preset: rolesPreset(row?.roles),
        });
        setMemberDialog(row ?? {});
    }, [memberForm]);
    const closeMember = useCallback(() => {
        setMemberDialog(null);
        memberForm.resetFields();
    }, [memberForm]);
    const submitMember = useCallback(values => {
        runMutation(
            () => api.auth.setSpacePreset(graphSpace, values.user_id, values.permission_preset,
                PAGE_ERROR_CONFIG
            ),
            closeMember
        );
    }, [closeMember, graphSpace, runMutation]);

    const confirmDelete = useCallback((title, operation) => {
        Modal.confirm({
            title,
            onOk: () => runMutation(operation, () => undefined),
        });
    }, [runMutation]);

    const editMember = useCallback(row => openMember(row), [openMember]);
    const deleteMember = useCallback(row => confirmDelete(
        t('account.space_access.member.remove_confirm'),
        () => api.auth.deleteSpaceMember(
            graphSpace, row.user_id, PAGE_ERROR_CONFIG
        )
    ), [confirmDelete, graphSpace, t]);
    const addMember = useCallback(() => openMember(), [openMember]);
    const canManageMember = row => scopes.all_graphspaces
        || !row.is_space_admin;
    const retrySpaces = useCallback(
        () => setSpacesRevision(value => value + 1), []
    );
    const submitMemberForm = useCallback(() => memberForm.submit(), [memberForm]);

    const memberColumns = [
        {title: t('account.space_access.member.id'), dataIndex: 'user_id'},
        {title: t('account.space_access.member.name'), dataIndex: 'user_name'},
        {
            title: t('account.space_access.member.roles'),
            dataIndex: 'roles',
            render: value => value?.map(role => (
                <Tag key={role.role_id}>{roleLabel(role, t)}</Tag>
            )),
        },
        ...((canAddMember || canRemoveMember) ? [{
            title: t('common.operation'),
            render: row => (
                <Space>
                    {canAddMember && canManageMember(row) && (
                        <RowAction row={row} onAction={editMember}>
                            {t('common.action.edit')}
                        </RowAction>
                    )}
                    {canRemoveMember && canManageMember(row) && (
                        <RowAction row={row} onAction={deleteMember}>
                            {t('common.action.delete')}
                        </RowAction>
                    )}
                </Space>
            ),
        }] : []),
    ];

    const table = (resource, columns, rowKey, addLabel, onAdd, canAdd) => (
        <>
            <ErrorAlert error={resource.error} retry={resource.retry} t={t} />
            <TableHeader>
                {canAdd && (
                    <Button type="primary" onClick={onAdd}>{addLabel}</Button>
                )}
            </TableHeader>
            <Table
                columns={columns}
                dataSource={resource.data}
                rowKey={rowKey}
                loading={resource.loading}
                pagination={{pageSize: 10, showSizeChanger: false}}
            />
        </>
    );

    if (spacesError) {
        return (
            <ErrorAlert
                error
                retry={retrySpaces}
                t={t}
            />
        );
    }

    if (!spacesLoading && spaces.length === 0) {
        return (
            <Alert
                type="info"
                showIcon
                message={t('account.space_access.no_spaces')}
            />
        );
    }

    return (
        <>
            <Space align="center" wrap>
                <Typography.Text strong>
                    {t('account.space_access.graphspace')}
                </Typography.Text>
                <Select
                    aria-label={t('account.space_access.graphspace')}
                    value={graphSpace}
                    onChange={setSelectedSpace}
                    loading={spacesLoading}
                    options={spaces.map(space => ({label: space, value: space}))}
                    style={{minWidth: 240}}
                />
            </Space>
            <Tabs
                items={[
                    {
                        key: 'members',
                        label: t('account.space_access.tabs.members'),
                        children: table(
                            visibleMembers, memberColumns, 'user_id',
                            t('account.space_access.member.add'),
                            addMember, canAddMember
                        ),
                    },
                ]}
            />

            <Modal
                open={memberDialog !== null}
                title={t('account.space_access.member.dialog')}
                onCancel={closeMember}
                onOk={submitMemberForm}
                confirmLoading={submitting}
                destroyOnClose
            >
                <Form form={memberForm} layout="vertical" onFinish={submitMember}>
                    <Form.Item
                        name="user_id"
                        label={t('account.space_access.member.id')}
                        rules={[{required: true}]}
                    >
                        <Input disabled={Boolean(memberDialog?.user_id)} />
                    </Form.Item>
                    <Form.Item
                        name="permission_preset"
                        label={t('account.space_access.member.roles')}
                        rules={[{required: true}]}
                    >
                        <Select
                            options={Object.values(PERMISSION_PRESETS)
                                .filter(value => value !== PERMISSION_PRESETS.SUPER_ADMIN)
                                .filter(value => scopes.all_graphspaces
                                                 || value !== PERMISSION_PRESETS.GS_ADMIN)
                                .map(value => ({
                                    value,
                                    label: t(`account.permission_preset.${value}`),
                                }))}
                        />
                    </Form.Item>
                </Form>
            </Modal>

        </>
    );
};

export {rolesPreset};
export default SpaceAccess;
