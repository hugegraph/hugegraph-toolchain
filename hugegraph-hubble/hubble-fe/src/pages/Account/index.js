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

import {
    PageHeader,
    Button,
    Space,
    Table,
    message,
    Tooltip,
    Modal,
} from 'antd';
import {useCallback, useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import TableHeader from '../../components/TableHeader';
import EditLayer from './EditLayer';
import * as api from '../../api';
import {getUser} from '../../utils/user';

const PAGE_ERROR_CONFIG = {suppressBusinessErrorToast: true};

const Account = () => {
    const {t} = useTranslation();
    const [editLayerVisible, setEditLayerVisible] = useState(false);
    const [op, setOp] = useState('detail');
    const [detail, setDetail] = useState({});
    const [data, setData] = useState([]);
    const [refresh, setRefresh] = useState(false);
    const [pagination, setPagination] = useState({toatal: 0, current: 1, pageSize: 10});

    const showDetail = row => {
        setDetail(row);
        setOp('detail');
        setEditLayerVisible(true);
    };

    const showEdit = row => {
        setDetail(row);
        setOp('edit');
        setEditLayerVisible(true);
    };

    const showAuth = row => {
        setDetail(row);
        setOp('auth');
        setEditLayerVisible(true);
    };

    const showAdd = useCallback(() => {
        setDetail({});
        setOp('create');
        setEditLayerVisible(true);
    }, []);

    const handleRefresh = useCallback(() => {
        setRefresh(value => !value);
    }, []);

    const handleHideLayer = useCallback(() => {
        setEditLayerVisible(false);
    }, []);

    const handleDelete = row => {
        Modal.confirm({
            title: t('account.delete_confirm', {name: row.user_name}),
            onOk: () => {
                return api.auth.delUser(row.id, PAGE_ERROR_CONFIG).then(res => {
                    if (res.status === 200) {
                        message.success(t('common.msg.delete_success'));
                        setRefresh(value => !value);
                        return;
                    }
                    message.error(t('common.msg.operation_failed'));
                }).catch(() => message.error(t('common.msg.operation_failed')));
            },
        });
    };

    const handleTable = useCallback(page => {
        setPagination({...pagination, ...page});
    }, [pagination]);

    const columns = [
        {
            title: t('account.col.id'),
            dataIndex: 'user_name',
        },
        {
            title: t('account.col.name'),
            dataIndex: 'user_nickname',
        },
        {
            title: t('account.col.remark'),
            dataIndex: 'user_description',
            ellipsis: {showTitle: false},
            render: val => <Tooltip title={val} placement='bottomLeft'>{val}</Tooltip>,
        },
        {
            title: t('account.col.resource'),
            dataIndex: 'spacenum',
            width: 120,
        },
        {
            title: t('account.col.create_time'),
            dataIndex: 'user_create',
            align: 'center',
            width: 200,
        },
        {
            title: t('common.operation'),
            width: 300,
            align: 'center',
            render: row => (
                <Space>
                    <a onClick={() => showDetail(row)}>{t('common.action.detail')}</a>
                    {<a onClick={() => showEdit(row)}>{t('common.action.edit')}</a>}
                    <a onClick={() => showAuth(row)}>{t('common.action.assign_permission')}</a>
                    {row.user_name !== 'admin'
                        && row.user_name !== getUser().id
                        && <a onClick={() => handleDelete(row)}>{t('common.action.delete')}</a>}
                </Space>
            ),
        },
    ];

    const rowKey = useCallback(item => item.user_name, []);
    const {current, pageSize} = pagination;

    useEffect(() => {
        api.auth.getAllUserList({
            query: '',
            page_no: current,
            page_size: pageSize,
        }, PAGE_ERROR_CONFIG).then(res => {
            if (res.status === 200) {
                setData(res.data.records);
                setPagination(value => ({...value, total: res.data.total}));
                return;
            }

            message.error(t('common.msg.load_failed'));
        }).catch(() => message.error(t('common.msg.load_failed')));
    }, [refresh, current, pageSize, t]);

    return (
        <>
            <PageHeader
                ghost={false}
                onBack={false}
                title={t('account.title')}
            />

            <div className='container'>
                <TableHeader>
                    <Space>
                        <Button onClick={showAdd} type='primary'>{t('account.create')}</Button>
                    </Space>
                </TableHeader>

                <Table
                    columns={columns}
                    dataSource={data}
                    rowKey={rowKey}
                    pagination={pagination}
                    onChange={handleTable}
                />
            </div>

            <EditLayer
                visible={editLayerVisible}
                op={op}
                data={detail}
                onCancel={handleHideLayer}
                refresh={handleRefresh}
            />
        </>
    );
};

export default Account;
