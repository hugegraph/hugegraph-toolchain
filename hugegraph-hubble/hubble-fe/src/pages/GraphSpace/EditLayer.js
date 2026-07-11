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

import {Modal, Form, Input, Select, message, Switch, InputNumber, Space, Typography} from 'antd';
import {useState, useEffect, useMemo, useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import * as api from '../../api/index';
import * as rules from '../../utils/rules';

const {Option} = Select;
const PAGE_ERROR_CONFIG = {suppressBusinessErrorToast: true};

const MyFormItem = ({label, unit, children}) => {
    return (
        <Form.Item label={label} colon={false} required>
            <Space>
                {children}
                <span className='spanFontSize'>{unit}</span>
            </Space>
        </Form.Item>
    );
};

const EditLayer = ({visible, detail, onCancel, refresh}) => {
    const [form] = Form.useForm();
    const [userList, setUserList] = useState([]);
    const [loading, setLoading] = useState(false);
    const {t} = useTranslation();

    const defaultValues = {
        max_graph_number: 100,
        max_role_number: 100,
        cpu_limit: 100,
        compute_cpu_limit: 100,
        memory_limit: 1000,
        compute_memory_limit: 1000,
        storage_limit: 1000000,
        description: '',
    };

    const isDisabled = useMemo(() => {
        if (Object.keys(detail).length !== 0) {
            return true;
        }
        return false;
    }, [detail]);

    const onFinish = useCallback(() => {
        form.validateFields().then(values => {
            setLoading(true);

            const thenCallBack = res => {
                setLoading(false);
                if (res && res.status === 200) {
                    message.success(t('common.msg.success'));
                    refresh();
                    onCancel();
                    return;
                }
                message.error(t('common.msg.operation_failed'));
            };
            const catchCallback = () => {
                setLoading(false);
                message.error(t('common.msg.operation_failed'));
            };

            if (isDisabled) {
                api.manage.updateGraphSpace(detail.name, values, PAGE_ERROR_CONFIG)
                    .then(thenCallBack).catch(catchCallback);
            }
            else {
                api.manage.addGraphSpace(values, PAGE_ERROR_CONFIG)
                    .then(thenCallBack).catch(catchCallback);
            }
        });
    }, [detail.name, isDisabled, onCancel, refresh, form, t]);

    const serviceValidator = (_, value) => {
        if (value === 'DEFAULT') {
            return Promise.resolve();
        }
        let res = /^[a-z][a-z0-9_]{0,47}$/.test(value);
        if (!res) {
            return Promise.reject(t('graphspace.form.id_rule'));
        }

        return Promise.resolve();
    };

    const k8sValidator = (_, value) => {
        let res = /^[a-z][a-z0-9\-]{0,47}$/.test(value);
        if (value && !res) {
            return Promise.reject(t('graphspace.form.k8s_rule'));
        }

        return Promise.resolve();
    };

    const userSelect = useMemo(
        () => userList.map(item => (<Option key={item.id}>{item.user_name}</Option>)),
        [userList]
    );

    useEffect(() => {
        if (!visible) {
            return;
        }
        form.resetFields();

        api.auth.getUserList(undefined, PAGE_ERROR_CONFIG).then(res => {
            if (res && res.status === 200) {
                setUserList(res.data.users);
                return;
            }
            message.error(t('common.msg.load_failed'));
        }).catch(() => message.error(t('common.msg.load_failed')));

        if (detail.name) {
            api.manage.getGraphSpace(detail.name, PAGE_ERROR_CONFIG).then(res => {
                if (res.status === 200) {
                    form.setFieldsValue({
                        ...res.data,
                        algorithm_image_url: res.data.algorithm_image_url
                                             || res.data['internal_'
                                                         + 'algorithm_image_url'],
                    });
                    return;
                }
                message.error(t('common.msg.load_failed'));
            }).catch(() => message.error(t('common.msg.load_failed')));
        }
    }, [visible, form, detail.name, t]);

    return (
        <Modal
            title={t(isDisabled ? 'graphspace.form.title_edit' : 'graphspace.form.title_create')}
            open={visible}
            onCancel={onCancel}
            okText={t(Object.keys(detail).length === 0
                ? 'common.action.create'
                : 'common.action.save')}
            loading={loading}
            onOk={onFinish}
            width={600}
            destroyOnClose
            maskClosable={false}
            // forceRender
        >
            <Form
                labelCol={{span: 6}}
                form={form}
                name="control-hooks"
                // onFinish={onFinish}
                // preserve={false}
                initialValues={defaultValues}
            >

                <Form.Item
                    name="name"
                    label={t('graphspace.form.id')}
                    rules={
                        [
                            {required: true, message: t('common.form.required')},
                            {max: 48, message: t('common.form.max_48')},
                            {validator: serviceValidator},
                        ]
                    }
                >
                    <Input disabled={isDisabled} placeholder={t('graphspace.form.id_placeholder')} />
                </Form.Item>

                <Form.Item
                    name="nickname"
                    label={t('graphspace.form.name')}
                    rules={
                        [
                            {required: true, message: t('common.form.required')},
                            {max: 12, message: t('common.form.max_12')},
                            rules.isPropertyName,
                        ]
                    }
                >
                    <Input placeholder={t('graphspace.form.name_placeholder')} />
                </Form.Item>

                <Form.Item
                    label={t('graphspace.form.auth')}
                    name="auth"
                    valuePropName="checked"
                >
                    <Switch disabled={isDisabled} />
                </Form.Item>

                <Form.Item
                    name="max_graph_number"
                    label={t('graphspace.form.max_graph')}
                    rules={
                        [
                            {required: true, message: t('common.form.required')},
                        ]
                    }
                >
                    <InputNumber precision={0} min={1} />
                </Form.Item>

                <Typography.Title level={5}>{t('graphspace.form.graph_service')}</Typography.Title>
                <MyFormItem label={t('graphspace.form.cpu')} unit={t('graphspace.unit.cpu')}>
                    <Form.Item
                        name="cpu_limit"
                        noStyle
                        rules={
                            [
                                {required: true, message: t('common.form.required')},
                            ]
                        }
                    >
                        <InputNumber placeholder={t('graphspace.form.cpu_placeholder')} precision={0} min={1} />
                    </Form.Item>
                </MyFormItem>

                <MyFormItem label={t('graphspace.form.memory')} unit={t('graphspace.unit.memory')}>
                    <Form.Item
                        name="memory_limit"
                        noStyle
                        rules={
                            [
                                {required: true, message: t('common.form.required')},
                            ]
                        }
                    >
                        <InputNumber placeholder={t('graphspace.form.memory_placeholder')} precision={0} min={1} />
                    </Form.Item>
                </MyFormItem>

                <Form.Item
                    name="oltp_namespace"
                    label={t('graphspace.form.k8s_namespace')}
                    rules={
                        [
                            {validator: k8sValidator},
                        ]
                    }
                >
                    <Input disabled={isDisabled} placeholder={t('graphspace.form.k8s_placeholder')} />
                </Form.Item>

                <Typography.Title level={5}>{t('graphspace.form.compute_task')}</Typography.Title>
                <MyFormItem label={t('graphspace.form.cpu')} unit={t('graphspace.unit.cpu')}>
                    <Form.Item
                        name="compute_cpu_limit"
                        noStyle
                        rules={
                            [
                                {required: true, message: t('common.form.required')},
                            ]
                        }
                    >
                        <InputNumber placeholder={t('graphspace.form.cpu_placeholder')} precision={0} min={1} />
                    </Form.Item>
                </MyFormItem>

                <MyFormItem label={t('graphspace.form.memory')} unit={t('graphspace.unit.memory')}>
                    <Form.Item
                        name="compute_memory_limit"
                        noStyle
                        rules={
                            [
                                {required: true, message: t('common.form.required')},
                            ]
                        }
                    >
                        <InputNumber placeholder={t('graphspace.form.memory_placeholder')} precision={0} min={1} />
                    </Form.Item>
                </MyFormItem>

                <Form.Item
                    name="olap_namespace"
                    label={t('graphspace.form.k8s_namespace')}
                    rules={
                        [
                            {validator: k8sValidator},
                        ]
                    }
                >
                    <Input disabled={isDisabled} placeholder={t('graphspace.form.k8s_placeholder')} />
                </Form.Item>

                <Form.Item
                    name="operator_image_path"
                    label={t('graphspace.form.operator_image')}
                    rules={[
                        {
                            pattern: /^[a-zA-Z0-9\-\.]+\/[a-zA-Z0-9\-_]+\/[a-zA-Z0-9\-_]+(\:[a-z0-9\.]+)*$/,
                            message: t('graphspace.form.image_format_error'),
                        },
                    ]}
                    extra='ie: example.com/org_1/xx_img:1.0.0'
                >
                    <Input />
                </Form.Item>

                <Form.Item
                    name="algorithm_image_url"
                    label={t('graphspace.form.algorithm_image')}
                    extra='ie: example.com/org_1/xx_img:1.0.0'
                    rules={[
                        {
                            pattern: /^[a-zA-Z0-9\-\.]+\/[a-zA-Z0-9\-_]+\/[a-zA-Z0-9\-_]+(\:[a-z0-9\.]+)*$/,
                            message: t('graphspace.form.image_format_error'),
                        },
                    ]}
                >
                    <Input />
                </Form.Item>

                <Typography.Title level={5}>{t('graphspace.form.storage')}</Typography.Title>
                <MyFormItem label={t('graphspace.form.storage_limit')} unit={t('graphspace.unit.memory')}>
                    <Form.Item
                        name="storage_limit"
                        noStyle
                        rules={
                            [
                                {required: true, message: t('common.form.required')},
                            ]
                        }
                    >
                        <InputNumber
                            style={{width: 200}}
                            placeholder={t('graphspace.form.memory_placeholder')}
                            precision={0}
                            min={1}
                        />
                    </Form.Item>
                </MyFormItem>

                <hr />

                <Form.Item
                    name="graphspace_admin"
                    label={t('graphspace.form.admin')}
                >
                    <Select
                        mode="multiple"
                        allowClear
                        style={{width: '100%'}}
                        placeholder={t('graphspace.form.admin_placeholder')}
                    >
                        {userList.length ? userSelect : null}
                    </Select>
                </Form.Item>

                <Form.Item
                    name="description"
                    label={t('graphspace.form.description')}
                    rules={
                        [
                            {max: 128, message: t('common.form.max_128')},
                        ]
                    }
                >
                    <Input.TextArea placeholder={t('graphspace.form.description_placeholder')} />
                </Form.Item>
            </Form>
        </Modal>
    );
};

export {EditLayer};
