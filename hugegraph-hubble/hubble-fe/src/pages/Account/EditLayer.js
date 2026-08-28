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

import {Alert, Modal, Input, Form, Select, message, Spin} from 'antd';
import {useCallback, useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import * as api from '../../api';
import * as rules from '../../utils/rules';
import style from './index.module.scss';
import FormHelpLabel from '../../components/FormHelpLabel';
import {accountErrorMessage} from './accountError';
import {useAuthContext} from '../../auth/AuthContext';
import {
    getAccountPreset,
    getAccountPresetLabelKey,
    getPresetSpaces,
    PERMISSION_PRESETS,
    toPermissionPayload,
} from './permissionPresets';
import {loadAllPages, PAGE_ERROR_CONFIG} from './pagedRecords';

const DEFAULT_ALLOWED_OPERATIONS = {create: true, edit: true, auth: true};
const PRESERVE_PERMISSIONS = 'PRESERVE_PERMISSIONS';
const permissionPresetChanged = (prev, next) => prev.permission_preset !== next.permission_preset;
const toProfilePayload = values => ({
    user_name: values.user_name,
    user_nickname: values.user_nickname,
    user_password: values.user_password,
    user_description: values.user_description,
});
const sameSpaces = (left = [], right = []) => (
    [...left].sort().join('\u0000') === [...right].sort().join('\u0000')
);

const loadAllGraphspaces = () => loadAllPages(api.manage.getGraphSpaceList);

const HelpLabel = ({t, labelKey}) => (
    <FormHelpLabel
        label={t(labelKey)}
        help={t(`${labelKey}_help`)}
    />
);

const EditLayer = ({
    visible,
    onCancel,
    data,
    op,
    refresh,
    allowedOperations = DEFAULT_ALLOWED_OPERATIONS,
}) => {
    const {t} = useTranslation();
    const {context} = useAuthContext();
    const [form] = Form.useForm();
    const [graphspaceList, setGraphspaceList] = useState([]);
    const [detail, setDetail] = useState({});
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [mutationError, setMutationError] = useState(null);
    const submitPending = useRef(false);
    const detailRequest = useRef(0);
    const permissionPresetsSupported = !context
        || context.capabilities?.includes('account_permission_presets');
    const permissionFieldsVisible = permissionPresetsSupported
        && ['create', 'edit'].includes(op);
    const preservesMixedPermissions = op === 'edit'
        && getAccountPreset(detail) === null;
    const presetOptions = [
        ...(preservesMixedPermissions ? [PRESERVE_PERMISSIONS] : []),
        ...Object.values(PERMISSION_PRESETS),
    ];

    const title = {
        'detail': t('account.form.title_detail'),
        'edit': t('account.form.title_edit'),
        'auth': t('account.form.title_auth'),
        'create': t('account.form.title_create'),
    };

    const createUser = useCallback(values => {
        const payload = permissionPresetsSupported
            ? toPermissionPayload(values)
            : toProfilePayload(values);
        return api.auth.addUser(payload, PAGE_ERROR_CONFIG).then(res => {
            if (res.status === 200) {
                message.success(t('common.msg.create_success'));
                onCancel();
                refresh();
                return;
            }
            throw res;
        });
    }, [onCancel, permissionPresetsSupported, refresh, t]);
    const updateUser = useCallback(values => {
        const initialPreset = getAccountPreset(detail) ?? PRESERVE_PERMISSIONS;
        const permissionsChanged = permissionPresetsSupported
                                   && (values.permission_preset !== initialPreset
                                       || !sameSpaces(
                                           values.graphspaces,
                                           getPresetSpaces(detail)
                                       ));
        if (values.user_password && permissionsChanged) {
            throw new Error(t('account.feedback.password_permission_separate'));
        }
        const payload = permissionsChanged
                        && values.permission_preset !== PRESERVE_PERMISSIONS
            ? toPermissionPayload(values)
            : toProfilePayload(values);
        return api.auth.updateUser(data.id, payload, PAGE_ERROR_CONFIG).then(res => {
            if (res.status === 200) {
                message.success(t('common.msg.update_success'));
                onCancel();
                refresh();

                return;
            }

            throw res;
        });
    }, [onCancel, refresh, data.id, detail, permissionPresetsSupported, t]);

    const updateUserAuth = useCallback(values => {
        const payload = toPermissionPayload({
            ...values,
            permission_preset: PERMISSION_PRESETS.GS_ADMIN,
        });
        return api.auth.updateAdminspace(data.id, payload.adminSpaces, PAGE_ERROR_CONFIG).then(res => {
            if (res.status === 200) {
                message.success(t('common.msg.set_success'));
                onCancel();
                refresh();

                return;
            }

            throw res;
        });
    }, [data.id, onCancel, refresh, t]);

    const onFinish = useCallback(async () => {
        if (submitPending.current || loading) {
            return;
        }

        submitPending.current = true;
        setSubmitting(true);
        setMutationError(null);
        try {
            const values = await form.validateFields();
            if (op === 'create') {
                await createUser(values);
            }

            if (op === 'edit') {
                await updateUser(values);
            }

            if (op === 'auth') {
                await updateUserAuth(values);
            }
        }
        catch (error) {
            if (!error || !error.errorFields) {
                const detail = accountErrorMessage(
                    error, t('account.feedback.save_retry')
                );
                setMutationError(detail);
                message.error(detail);
            }
        }
        finally {
            submitPending.current = false;
            setSubmitting(false);
        }
    }, [createUser, form, loading, op, t, updateUser, updateUserAuth]);

    useEffect(() => {
        if (!visible) {
            detailRequest.current += 1;
            setDetail({});
            setGraphspaceList([]);
            setMutationError(null);
            form.resetFields();
            setLoading(false);
            return;
        }

        const request = detailRequest.current + 1;
        detailRequest.current = request;
        setGraphspaceList([]);
        if (op !== 'detail' && (permissionPresetsSupported || op === 'auth')) {
            loadAllGraphspaces().then(res => {
                if (detailRequest.current !== request) {
                    return;
                }

                if (res.status === 200) {
                    setGraphspaceList(res.data.records.map(item => ({
                        label: item.name,
                        value: item.name,
                    })));
                    return;
                }

                message.error(t('common.msg.load_failed'));
            }).catch(() => {
                if (detailRequest.current === request) {
                    message.error(t('common.msg.load_failed'));
                }
            });
        }

        if (data.id) {
            setLoading(true);
            setDetail({});
            form.resetFields();
            api.auth.getUserInfo(data.id, PAGE_ERROR_CONFIG).then(res => {
                if (detailRequest.current !== request) {
                    return;
                }

                if (res.status === 200) {
                    if (op !== 'detail') {
                        form.setFieldsValue({
                            ...res.data,
                            permission_preset: getAccountPreset(res.data) ?? PRESERVE_PERMISSIONS,
                            graphspaces: op === 'auth'
                                ? (res.data?.adminSpaces ?? [])
                                : getPresetSpaces(res.data),
                        });
                    }
                    setDetail(res.data);
                    return;
                }

                form.resetFields();
                setDetail({});
                message.error(t('common.msg.load_failed'));
            }).catch(() => {
                if (detailRequest.current !== request) {
                    return;
                }

                form.resetFields();
                setDetail({});
                message.error(t('common.msg.load_failed'));
            }).finally(() => {
                if (detailRequest.current === request) {
                    setLoading(false);
                }
            });
        }
        else {
            setDetail({});
            form.resetFields();
            setLoading(false);
        }
    }, [visible, data.id, form, op, permissionPresetsSupported, t]);

    if (op !== 'detail' && !allowedOperations[op]) {
        return null;
    }

    return (
        op === 'detail'
            ? (
                <Modal
                    title={t('account.form.title_detail')}
                    onCancel={onCancel}
                    open={visible}
                    footer={null}
                    width={600}
                    maskClosable={false}
                >
                    <Spin spinning={loading}>
                        <Form
                            labelCol={{span: 6}}
                            form={form}
                            preserve={false}
                        >
                            <Form.Item label={t('account.form.id')} className={style.item}>
                                {detail.user_name}
                            </Form.Item>
                            <Form.Item label={t('account.form.name')} className={style.item}>
                                {detail.user_nickname}
                            </Form.Item>
                            <Form.Item
                                label={t('account.form.permission_preset')}
                                className={style.item}
                            >
                                {t(`account.permission_preset.${getAccountPresetLabelKey(
                                    detail, permissionPresetsSupported
                                )}`)}
                            </Form.Item>
                            <Form.Item label={t('account.form.remark')} className={style.item}>
                                {detail.user_description}
                            </Form.Item>
                            <Form.Item
                                label={t('account.form.graphspaces')}
                                className={style.item}
                            >
                                {getPresetSpaces(detail).join(', ')}
                            </Form.Item>
                            <Form.Item label={t('account.col.create_time')} className={style.item}>
                                {detail.user_create}
                            </Form.Item>
                        </Form>
                    </Spin>
                </Modal>
            )
            : (
                <Modal
                    title={title[op] ?? t('account.form.title_create')}
                    onCancel={onCancel}
                    open={visible}
                    onOk={onFinish}
                    confirmLoading={submitting}
                    okButtonProps={{disabled: loading}}
                    width={600}
                >
                    <Spin spinning={loading}>
                        {!permissionPresetsSupported && ['create', 'edit'].includes(op) && (
                            <Alert
                                type='info'
                                showIcon
                                message={t(
                                    op === 'create'
                                        ? 'account.feedback.presets_unavailable'
                                        : 'account.feedback.preset_edit_unavailable'
                                )}
                                description={t(
                                    op === 'create'
                                        ? 'account.feedback.presets_unavailable_help'
                                        : 'account.feedback.preset_edit_unavailable_help'
                                )}
                            />
                        )}
                        {mutationError && (
                            <Alert
                                type='error'
                                showIcon
                                message={t('account.feedback.save_failed')}
                                description={mutationError}
                            />
                        )}
                        <Form
                            labelCol={{span: 6}}
                            // initialValues={data}
                            form={form}
                            preserve={false}
                        >
                            {(op === 'create' || op === 'edit') && (
                                <>
                                    <Form.Item
                                        label={<HelpLabel t={t} labelKey='account.form.id' />}
                                        name="user_name"
                                        validateFirst
                                        rules={[{type: 'string', min: 5, max: 16}, rules.isName, rules.required()]}
                                    >
                                        <Input
                                            placeholder={t('account.form.id_placeholder')}
                                            disabled={op === 'edit'}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        label={<HelpLabel t={t} labelKey='account.form.name' />}
                                        name="user_nickname"
                                        rules={[rules.isAccountName]}
                                        validateFirst
                                    >
                                        <Input placeholder={t('account.form.name_placeholder')} />
                                    </Form.Item>
                                    <Form.Item
                                        label={<HelpLabel t={t} labelKey='account.form.default_password' />}
                                        name="user_password"
                                        rules={op === 'create'
                                            ? [rules.required(), {type: 'string', min: 5, max: 16}]
                                            : [{type: 'string', min: 5, max: 16}]}
                                    >
                                        <Input.Password
                                            placeholder={t('account.form.default_password_placeholder')}
                                            autoComplete="new-password"
                                        />
                                    </Form.Item>
                                    {permissionFieldsVisible && (
                                        <Form.Item
                                            label={<HelpLabel t={t} labelKey='account.form.permission_preset' />}
                                            name="permission_preset"
                                            rules={[rules.required()]}
                                        >
                                            <Select
                                                options={presetOptions.map(value => ({
                                                    value,
                                                    label: t(value === PRESERVE_PERMISSIONS
                                                        ? 'account.permission_preset.preserve_mixed'
                                                        : `account.permission_preset.${value}`),
                                                }))}
                                            />
                                        </Form.Item>
                                    )}
                                    <Form.Item
                                        label={<HelpLabel t={t} labelKey='account.form.remark' />}
                                        name="user_description"
                                    >
                                        <Input placeholder={t('account.form.remark_placeholder')} />
                                    </Form.Item>
                                    {permissionFieldsVisible && (
                                        <Form.Item
                                            noStyle
                                            shouldUpdate={permissionPresetChanged}
                                        >
                                            {({getFieldValue}) => (
                                                [
                                                    PERMISSION_PRESETS.SUPER_ADMIN,
                                                    PRESERVE_PERMISSIONS,
                                                ].includes(
                                                    getFieldValue('permission_preset')
                                                )
                                                    ? null
                                                    : (
                                                        <Form.Item
                                                            label={(
                                                                <HelpLabel
                                                                    t={t}
                                                                    labelKey='account.form.graphspaces'
                                                                />
                                                            )}
                                                            name="graphspaces"
                                                            rules={[rules.required()]}
                                                        >
                                                            <Select options={graphspaceList} mode="multiple" />
                                                        </Form.Item>
                                                    )
                                            )}
                                        </Form.Item>
                                    )}
                                </>
                            )}
                            {op === 'auth' && (
                                <>
                                    <Form.Item
                                        label={<HelpLabel t={t} labelKey='account.form.permission_preset' />}
                                        name="permission_preset"
                                        rules={[rules.required()]}
                                    >
                                        <Select
                                            options={[PERMISSION_PRESETS.GS_ADMIN].map(value => ({
                                                value,
                                                label: t(`account.permission_preset.${value}`),
                                            }))}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        label={<HelpLabel t={t} labelKey='account.form.graphspaces' />}
                                        name="graphspaces"
                                        rules={[rules.required()]}
                                    >
                                        <Select options={graphspaceList} mode="multiple" />
                                    </Form.Item>
                                </>
                            )}
                        </Form>
                    </Spin>
                </Modal>
            )
    );
};

export default EditLayer;
