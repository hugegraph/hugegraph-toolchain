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

/**
 * @file 任务管理 table页面
 */

import React, {useState, useCallback, useContext} from 'react';
import {Table, Tag, Spin, message, Button, Typography, Modal} from 'antd';
import GraphAnalysisContext from '../../Context';
import {CloseOutlined} from '@ant-design/icons';
import * as api from '../../../api/index';
import formatTimeDuration from '../../../utils/formatTimeDuration';
import {
    Async_Task_Type,
    Async_Taskt_Status,
    Filter_Task_Status,
    Status_Color,
} from '../../../utils/constants';
import {intersection, size} from 'lodash-es';
import {format} from 'date-fns';
import {useTranslation} from 'react-i18next';
import c from './index.module.scss';
const {Text} = Typography;

const {FAILED, SUCCESS, DELETING, CANCELLING} = Async_Taskt_Status;

const AsyncTaskDetail = props => {
    const {t} = useTranslation();
    const {
        page,
        pageSize,
        onPageChange,
        getAsynTaskList,
        asyncManageTaskData,
    } = props;

    const {graphSpace: currentGraphSpace, graph: currentGraph, isVermeer} = useContext(GraphAnalysisContext);
    const {records: asyncManageTaskDataRecords, total: asyncManageTaskDataTotal} = asyncManageTaskData || {};
    const [selectedRowKeys, setSelectedRowKeys] = useState([]);
    const taskTypeNames = {
        '': t('analysis.async_task.type.all'),
        gremlin: t('analysis.async_task.type.gremlin'),
        'computer-dis': t('analysis.async_task.type.algorithm'),
        remove_schema: t('analysis.async_task.type.remove_schema'),
        create_index: t('analysis.async_task.type.create_index'),
        rebuild_index: t('analysis.async_task.type.rebuild_index'),
        cypher: t('analysis.async_task.type.cypher'),
        'vermeer-task:load': t('analysis.async_task.type.vermeer_load'),
        'vermeer-task:compute': t('analysis.async_task.type.vermeer_compute'),
    };
    const taskStatusNames = {
        '': t('analysis.async_task.status.all'),
        UNKNOWN: t('analysis.async_task.status.unknown'),
        new: t('analysis.async_task.status.new'),
        scheduling: t('analysis.async_task.status.scheduling'),
        scheduled: t('analysis.async_task.status.scheduled'),
        queued: t('analysis.async_task.status.queued'),
        running: t('analysis.async_task.status.running'),
        restoring: t('analysis.async_task.status.restoring'),
        success: t('analysis.async_task.status.success'),
        failed: t('analysis.async_task.status.failed'),
        cancelled: t('analysis.async_task.status.cancelled'),
        cancelling: t('analysis.async_task.status.cancelling'),
        hanging: t('analysis.async_task.status.hanging'),
        pending: t('analysis.async_task.status.pending'),
        deleting: t('analysis.async_task.status.deleting'),
    };
    const taskManipulations = {
        check_reason: t('analysis.async_task.action.check_reason'),
        check_result: t('analysis.async_task.action.check_result'),
        delete: t('analysis.async_task.action.delete'),
        abort: t('analysis.async_task.action.abort'),
        aborting: t('analysis.async_task.action.aborting'),
    };

    const onSelectChange = (rowKey, selectedRows) => {
        setSelectedRowKeys(rowKey);
    };

    const rowSelection = {
        selectedRowKeys,
        onChange: onSelectChange,
        getCheckboxProps: record => {
            const checkboxProps = ['scheduling', 'scheduled', 'queued', 'running', 'restoring', 'deleting'];
            return {
                disabled: checkboxProps.includes(record.task_status),
                task_status: record.task_status,
            };

        },
    };

    const currentSelectedRowKeys = intersection(
        selectedRowKeys,
        asyncManageTaskDataRecords?.map(({id}) => id)
    );

    const onRefresh = useCallback(() => {
        getAsynTaskList();
    }, [getAsynTaskList]);

    const renderTaskTypeFilters = () => {
        const res = [];
        const keys = Object.keys(Async_Task_Type);
        for (let i = 0; i < keys.length; i++) {
            const item = keys[i];
            const text = taskTypeNames[item] || Async_Task_Type[item];
            if (!item.includes('vermeer')) {
                res.push({text, value: item});
            }
            else if (isVermeer) {
                res.push({text, value: item});
            }
        }
        return res;
    };

    const renderTaskStatusFilters = () => {
        const res = [];
        const keys = Object.keys(Filter_Task_Status);
        for (let i = 0; i < keys.length; i++) {
            const item = keys[i];
            res.push({text: taskStatusNames[item] || Filter_Task_Status[item], value: item});
        }
        return res;
    };

    const viewResult = useCallback(
        (text, rowData, index) => {
            window.open(`/asyncTasks/result/${currentGraphSpace}/${currentGraph}/${rowData.id}`);
        }, [currentGraph, currentGraphSpace]);

    const deleteTaskByIds = useCallback(
        taskIdArr => {
            const parmas  = {ids: taskIdArr};
            api.analysis.deleteAsyncTask(currentGraphSpace, currentGraph, parmas)
                .then(res => {
                    const {status, message: errMsg} = res;
                    if (status === 200) {
                        onRefresh();
                    }
                    else {
                        !errMsg && message.error(t('analysis.async_task.delete_failed'));
                    }
                });
        }, [currentGraph, currentGraphSpace, onRefresh, t]);

    const abortAsyncTaskById = useCallback(
        async taskId => {
            const response  = await api.analysis.abortAsyncTask(currentGraphSpace, currentGraph, taskId);
            const {status, message: abortAsyncTaskMessage} = response || {};
            if (status === 200) {
                onRefresh();
            }
            else {
                !abortAsyncTaskMessage && message.error(t('analysis.async_task.abort_failed'));
            }
        }, [currentGraphSpace, currentGraph, onRefresh, t]);

    const onAbortTaskHandler = useCallback(taskId => {
        abortAsyncTaskById(taskId);
    }, [abortAsyncTaskById]);

    const onDeleteConfirm = id => {
        Modal.confirm({
            title: t('analysis.async_task.delete_confirm_title'),
            content: t('analysis.async_task.delete_confirm_content'),
            okText: t('common.action.confirm'),
            cancelText: t('common.action.cancel'),
            onOk: () => deleteTaskByIds([id]),
        });
    };

    const onMassDeleteConfirm = () => {
        Modal.confirm({
            title: t('analysis.async_task.batch_delete_title'),
            content: t('analysis.async_task.batch_delete_content'),
            okText: t('common.action.confirm'),
            cancelText: t('common.action.cancel'),
            onOk: () => deleteTaskByIds(currentSelectedRowKeys),
        });
    };

    const columns = [
        {
            title: t('analysis.async_task.column.task_id'),
            dataIndex: 'id',
            fixed: 'left',
        },
        {
            title: t('analysis.async_task.column.task_name'),
            dataIndex: 'task_name',
            render: (task_name, rowData, index) => {
                return (<Text ellipsis={{tooltip: task_name}}>{task_name}</Text>);
            },
        },
        {
            title: t('analysis.async_task.column.task_type'),
            dataIndex: 'task_type',
            filters: renderTaskTypeFilters(),
            filterMultiple: false,
            render: (task_type, rowData, index) => {
                return (<>{taskTypeNames[task_type] || Async_Task_Type[task_type] || task_type}</>);
            },
        },
        {
            title: t('analysis.async_task.column.create_time'),
            dataIndex: 'task_create',
            render: (task_create, rowData, index) => {
                const convertedDate = format(new Date(task_create), 'yyyy-MM-dd H:m:ss');
                return (<>{convertedDate}</>);
            },
        },
        {
            title: t('analysis.async_task.column.duration'),
            dataIndex: 'task_progress',
            render: (task_progress, rowData, index) => {
                const {task_update, task_create} = rowData;
                const duration = formatTimeDuration(task_create, task_update);
                return <div style={{whiteSpace: 'nowrap'}}>{duration}</div>;
            },
        },
        {
            title: t('analysis.async_task.column.status'),
            dataIndex: 'task_status',
            filterMultiple: false,
            filters: renderTaskStatusFilters(),
            render: (task_status, rowData, index) => {
                return <Tag color={Status_Color[task_status]}>{taskStatusNames[task_status]}</Tag>;
            },
        },
        {
            title: t('analysis.async_task.column.action'),
            dataIndex: 'manipulation',
            render: (result, rowData, index) => {
                const {'task_status': status, 'task_type': type, id: taskId}  = rowData;
                const allowCheckResTypeArr = ['gremlin', 'computer-dis', 'cypher'];
                const isAllowCheckRes = status === SUCCESS && allowCheckResTypeArr.includes(type);
                const allowAbortStatusArr = ['scheduling', 'scheduled', 'queued', 'running', 'restoring'];
                const isAllowAbort = allowAbortStatusArr.includes(status);
                const {
                    'check_reason': reason,
                    'check_result': resultText,
                    'delete': delText,
                    abort,
                    aborting,
                } = taskManipulations;
                return (
                    <div style={{whiteSpace: 'nowrap'}}>
                        {status === FAILED && (
                            <a
                                style={{margin: '10px'}}
                                href={`/asyncTasks/result/${currentGraphSpace}/${currentGraph}/${taskId}`}
                            >
                                {reason}
                            </a>

                        )}
                        {isAllowCheckRes && (
                            <a style={{margin: '10px'}} onClick={() => viewResult(result, rowData, index)}>
                                {resultText}
                            </a>
                        )}
                        {!isAllowAbort && (
                            status === DELETING
                                ? <Spin type="strong" />
                                : <a style={{margin: '10px'}} onClick={() => onDeleteConfirm(taskId)}>{delText}</a>
                        )}
                        {isAllowAbort && (
                            <a style={{margin: '10px'}} onClick={() => onAbortTaskHandler(taskId)}>
                                {abort}
                            </a>
                        )}
                        {status === CANCELLING && (
                            <div><a style={{margin: '10px'}}>{aborting}</a></div>
                        )}
                    </div>
                );
            },
        },
    ];

    return (
        <div className={c.gremlinAsyncTaskDetail}>
            {size(currentSelectedRowKeys) !== 0 && (
                <div className={c.massDelete}>
                    <div className={c.left}>
                        <span style={{marginRight: '12px'}}>
                            {t('analysis.async_task.selected_count', {count: size(currentSelectedRowKeys)})}
                        </span>
                        <Button onClick={onMassDeleteConfirm}>
                            {t('analysis.async_task.batch_delete')}
                        </Button>
                    </div>
                    <CloseOutlined onClick={() => setSelectedRowKeys([])} />
                </div>
            )}
            <Table
                rowKey='id'
                scroll={{x: 1000}}
                rowSelection={rowSelection}
                columns={columns}
                dataSource={asyncManageTaskDataRecords}
                onChange={onPageChange}
                pagination={{
                    position: ['bottomRight'],
                    total: asyncManageTaskDataTotal,
                    showSizeChanger: asyncManageTaskDataTotal > 10,
                    current: page,
                    pageSize: pageSize,
                }}
            />
        </div>
    );
};

export default AsyncTaskDetail;
