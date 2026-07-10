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
    Table,
    Space,
    Row,
    Col,
    PageHeader,
    Button,
    Input,
    Radio,
    DatePicker,
    Card,
    Modal,
    message,
    Pagination,
    Spin,
} from 'antd';
import {useState, useEffect, useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {EditLayer, ViewLayer, CloneLayer} from './EditLayer';
import {PlusOutlined} from '@ant-design/icons';
import {Link, useParams, useNavigate} from 'react-router-dom';
import style from './index.module.scss';
import * as api from '../../api';
import {isPdEnabled} from '../../utils/config';
import {
    DEFAULT_GRAPHSPACE,
    isGraphCreateEnabled,
    isGraphDefaultMutationEnabled,
} from '../../utils/productMode';
import moment from 'moment';
import GraphCard from './Card';
import ClearGraphConfirmModal from './ClearGraphConfirmModal';

const Graph = () => {
    const {t} = useTranslation();
    const [data, setData] = useState([]);
    const [dateData, setDateData] = useState('');
    const [graphname, setGraphname] = useState('');
    const [graphspaceInfo, setGraphspaceInfo] = useState({});
    const [editLayer, setEditLayer] = useState(false);
    const [viewLayer, setViewLayer] = useState(false);
    const [cloneLayer, setCloneLayer] = useState(false);
    const [selectGraph, setSelectGraph] = useState('');
    const [listType, setListType] = useState('image');
    const [refresh, setRefresh] = useState(false);
    const [pagination, setPagination] = useState({current: 1, pageSize: 11});
    const [loading, setLoading] = useState(false);
    const [clearSelection, setClearSelection] = useState(null);
    const {graphspace} = useParams();
    const navigate = useNavigate();
    const pdMode = isPdEnabled();
    const graphCreateEnabled = isGraphCreateEnabled(pdMode);
    const graphDefaultMutationEnabled = isGraphDefaultMutationEnabled(pdMode);

    const handlePagination = useCallback(current => {
        setPagination({...pagination, current});
    }, [pagination]);

    const handleTable = useCallback(pagination => {
        setPagination(pagination);
    }, []);

    const handleListType = useCallback(e => {
        setListType(e.target.value);
        setPagination({...pagination, current: 1, pageSize: e.target.value === 'image' ? 11 : 10});
    }, [pagination]);

    const handleSearch = useCallback(val => {
        setGraphname(val);
        setRefresh(!refresh);
    }, [refresh]);

    const showEditLayer = useCallback(() => {
        setEditLayer(true);
        setSelectGraph('');
    }, []);

    const editGraph = graph => {
        setSelectGraph(graph);
        setEditLayer(true);
    };

    const clearData = graph => {
        setClearSelection({graph, mode: 'data'});
    };

    const clearSchema = graph => {
        setClearSelection({graph, mode: 'schema-data'});
    };

    const handleClearSuccess = useCallback(() => {
        message.success(t('common.msg.success'));
        setClearSelection(null);
        setRefresh(value => !value);
    }, [t]);

    const handleClearCancel = useCallback(() => {
        setClearSelection(null);
    }, []);

    const handleClearConfirm = useCallback(() => {
        return clearSelection?.mode === 'data'
            ? api.manage.clearGraphData(graphspace, clearSelection.graph)
            : api.manage.clearGraphDataAndSchema(graphspace, clearSelection.graph);
    }, [clearSelection, graphspace]);

    const showSchema = graph => {
        setViewLayer(true);
        setSelectGraph(graph);
    };

    const deleteGraph = graph => {
        Modal.confirm({
            title: '确定删除图吗?',
            content: '删除后无法恢复',
            onOk: () => {
                const hide = message.loading('删除中', 0);
                api.manage.delGraph(graphspace, graph).then(res => {
                    hide();
                    if (res.status === 200) {
                        message.success('删除成功');
                        setRefresh(!refresh);
                        return;
                    }

                    message.error('删除失败');
                });
            },
        });
    };

    const setDefault = graph => {
        const hide = message.loading('设置中...', 0);
        api.manage.setDefaultGraph(graphspace, graph).then(res => {
            hide();
            if (res.status === 200) {
                message.success('设置成功');
                setRefresh(!refresh);
                return;
            }
            message.error(res.message);
        });
    };

    const handleSetDefault = graph => {
        api.manage.getDefaultGraph(graphspace).then(res => {
            if (res.status !== 200) {
                message.error(res.message);
                return;
            }

            if (res.data.default_graph) {
                Modal.confirm({
                    title: t('graph.set_default_confirm'),
                    onOk: () => setDefault(graph),
                });

                return;
            }

            setDefault(graph);
        });
    };

    const handleBack = useCallback(() => {
        if (isPdEnabled()) {
            navigate('/graphspace');
        }
        else {
            navigate('/navigation');
        }
    }, [navigate]);

    const handleHideEditLayer = useCallback(() => {
        setEditLayer(false);
    }, []);

    const handleRefresh = useCallback(() => {
        setRefresh(!refresh);
    }, [refresh]);

    const handleHideViewLayer = useCallback(() => {
        setViewLayer(false);
    }, []);

    const handleDatePickerChange = useCallback((_, val) => setDateData(val), []);

    const handleGotoMeta = useCallback(item => {
        navigate(`/graphspace/${item.graphspace || 'DEFAULT'}/graph/${item.name}/meta`);
    }, [navigate]);

    const handleGotoAnalysis = useCallback(item => {
        navigate(`/gremlin/${item.graphspace || 'DEFAULT'}/${item.name}`);
    }, [navigate]);

    const showClone = graph => {
        setSelectGraph(graph);
        setCloneLayer(true);
    };

    const handleHideCloneLayer = useCallback(() => {
        setSelectGraph('');
        setCloneLayer(false);
    }, []);

    const columns = [
        {
            title: '图名称',
            render: row => (
                <Link to={`/gremlin/${row.graphspace || 'DEFAULT'}/${row.name}`}>
                    {row.nickname}
                    {row.default && (
                        <span className={style.default}>
                            {t('common.label.default')}
                        </span>
                    )}
                </Link>
            ),
        },
        {
            title: t('graph.detail.graphspace'),
            dataIndex: 'graphspace_nickname',
        },
        {
            title: t('graph.col.create_time'),
            dataIndex: 'create_time',
            align: 'center',
            width: 140,
            render: val => moment(val).format('YYYY-MM-DD'),
        },
        {
            title: t('graph.detail.update_data'),
            dataIndex: 'update_time',
            align: 'center',
            width: 140,
            render: val => moment(val).format('YYYY-MM-DD'),
        },
        {
            title: t('graph.col.creator'),
            dataIndex: 'creator',
            align: 'center',
            width: 140,
        },
        {
            title: t('graph.col.operation'),
            width: 420,
            align: 'center',
            render: row => {
                return (
                    <Space>
                        <Link to={`/graphspace/${graphspace}/graph/${row.name}/meta`}>
                            {t('graph.menu.meta_config')}
                        </Link>
                        {(row.default)
                            ? <span className={style.disable}>{t('graph.menu.clear_data')}</span>
                            : <a onClick={() => clearSchema(row.name)}>{t('graph.menu.clear_data')}</a>}
                        {(row.graphspace === 'neizhianli')
                            ? <span className={style.disable}>{t('common.action.delete')}</span>
                            : <a onClick={() => deleteGraph(row.name)}>{t('common.action.delete')}</a>}
                        <a onClick={() => showSchema(row.name)}>{t('graph.menu.view_schema')}</a>
                        {(row.graphspace === 'neizhianli')
                            ? <span className={style.disable}>{t('common.action.edit')}</span>
                            : <a onClick={() => editGraph(row.name)}>{t('common.action.edit')}</a>}
                        {graphDefaultMutationEnabled && (
                            row.default
                                ? <span className={style.disable}>{t('graph.menu.set_default')}</span>
                                : <a onClick={() => handleSetDefault(row.name)}>{t('graph.menu.set_default')}</a>
                        )}
                        {graphCreateEnabled && (
                            <a onClick={() => showClone(row.name)}>{t('graph.menu.clone')}</a>
                        )}
                    </Space>
                );
            },
        },
    ];

    const getMenus = item => [
        {
            key: '0',
            label: <a onClick={() => handleGotoAnalysis(item)}>{t('graph.menu.enter_analysis')}</a>,
        },
        {
            key: '1',
            label: <a onClick={() => handleGotoMeta(item)}>{t('graph.menu.meta_config')}</a>,
        },
        {
            key: '2',
            label: item.isDefault
                ? <span className={style.disable}>{t('graph.menu.clear_schema_data')}</span>
                : <a onClick={() => clearSchema(item.name)}>{t('graph.menu.clear_schema_data')}</a>,
        },
        {
            key: '3',
            label: <a onClick={() => clearData(item.name)}>{t('graph.menu.clear_data')}</a>,
        },
        graphDefaultMutationEnabled && {
            key: '4',
            label: item.isDefault
                ? <span className={style.disable}>{t('graph.menu.set_default')}</span>
                : <a onClick={() => handleSetDefault(item.name)}>{t('graph.menu.set_default')}</a>,
        },
        {
            key: '5',
            label: <a onClick={() => showSchema(item.name)}>{t('graph.menu.view_schema')}</a>,
        },
        {
            key: '6',
            label: item.graphspace === 'neizhianli'
                ? <span className={style.disable}>{t('common.action.edit')}</span>
                : <a onClick={() => editGraph(item.name)}>{t('common.action.edit')}</a>,
        },
        {
            key: '7',
            label: item.graphspace === 'neizhianli'
                ? <span className={style.disable}>{t('common.action.delete')}</span>
                : <a onClick={() => deleteGraph(item.name)}>{t('common.action.delete')}</a>,
        },
        graphCreateEnabled && {
            key: '8',
            label: <a onClick={() => showClone(item.name)}>{t('graph.menu.clone')}</a>,
        },
        // {
        //     key: '8',
        //     label: <a onClick={() => showClone(item.name)}>克隆图</a>,
        // },
    ].filter(Boolean);

    useEffect(() => {
        if (!pdMode && graphspace === DEFAULT_GRAPHSPACE) {
            setGraphspaceInfo({name: DEFAULT_GRAPHSPACE, nickname: DEFAULT_GRAPHSPACE});
            return;
        }

        api.manage.getGraphSpace(graphspace).then(res => {
            if (res.status === 200) {
                setGraphspaceInfo(res.data);
                return;
            }

            message.error(res.message);
        });
    }, [graphspace, pdMode]);

    useEffect(() => {
        setLoading(true);

        api.manage.getGraphList(graphspace, {
            create_time: dateData,
            query: graphname,
            page_no: pagination.current,
            page_size: pagination.pageSize,
        }).then(res => {
            // hide();
            setLoading(false);
            if (res.status === 200) {
                setData(res.data.records);
                setPagination({...pagination, total: res.data.total});

                return;
            }

            message.error(res.message);
        });

    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [refresh, pagination.current, listType, dateData, graphspace, graphname]);

    return (
        <Spin spinning={loading}>
            <PageHeader
                ghost={false}
                onBack={handleBack}
                title={pdMode
                    ? (graphspaceInfo.nickname ?? graphspace) + ` - ${t('graph.title')}`
                    : t('graph.title')}
            >
                <Row justify='space-between'>
                    <Col>
                        <DatePicker onChange={handleDatePickerChange} />
                    </Col>
                    <Col>
                        <Space>
                            <Radio.Group
                                options={[
                                    {label: t('common.label.view_mode'), value: 'image'},
                                    {label: t('common.label.list_mode'), value: 'list'},
                                ]}
                                optionType='button'
                                buttonStyle='solid'
                                defaultValue={'image'}
                                onChange={handleListType}
                            />
                            <Input.Search
                                onSearch={handleSearch}
                                placeholder={t('graph.search_placeholder')}
                            />
                        </Space>
                    </Col>
                </Row>
            </PageHeader>

            <div className='container'>
                {listType === 'image'
                    ? (
                        <>
                            <Row gutter={[10, 10]} justify='start'>
                                {graphCreateEnabled && (
                                    <Col span={8} key='add'>
                                        <Card className={style.add_card} onClick={showEditLayer}>
                                            <Space><PlusOutlined />{t('graph.create')}</Space>
                                        </Card>
                                    </Col>
                                )}

                                {data.map(item => {
                                    const menus = getMenus(item);

                                    return (
                                        <Col span={8} key={item.name}>
                                            <GraphCard
                                                item={item}
                                                menus={menus}
                                            />
                                        </Col>
                                    );
                                })}
                            </Row>
                            <br />
                            <Row justify='end'>
                                <Col>
                                    <Pagination
                                        current={pagination.current}
                                        pageSize={pagination.pageSize}
                                        total={pagination.total}
                                        onChange={handlePagination}
                                    />
                                </Col>
                            </Row>
                        </>
                    )
                    : (
                        <>
                            {graphCreateEnabled && (
                                <>
                                    <Row>
                                        <Col>
                                            <Button onClick={showEditLayer} type='primary'>
                                                {t('graph.create')}
                                            </Button>
                                        </Col>
                                    </Row>
                                    <br />
                                </>
                            )}
                            <Table
                                columns={columns}
                                dataSource={data}
                                pagination={pagination}
                                onChange={handleTable}
                            />
                        </>
                    )
                }
                <EditLayer
                    visible={editLayer}
                    onCancel={handleHideEditLayer}
                    graphspace={graphspace}
                    refresh={handleRefresh}
                    graph={selectGraph}
                />
                <ViewLayer
                    visible={viewLayer}
                    onCancel={handleHideViewLayer}
                    graph={selectGraph}
                    graphspace={graphspace}
                />
                <CloneLayer
                    open={cloneLayer}
                    onCancel={handleHideCloneLayer}
                    refresh={handleRefresh}
                    graph={selectGraph}
                    graphspace={graphspace}
                />
                <ClearGraphConfirmModal
                    open={Boolean(clearSelection)}
                    graphspace={graphspace}
                    graph={clearSelection?.graph || ''}
                    mode={clearSelection?.mode || 'data'}
                    onCancel={handleClearCancel}
                    onSuccess={handleClearSuccess}
                    onConfirm={handleClearConfirm}
                />
            </div>
        </Spin>
    );
};

export default Graph;
