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

import {Alert, PageHeader, Row, Col, Button, Spin, message, Space, Table} from 'antd';
import {useCallback, useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import * as api from '../../api';
import style from './index.module.scss';
import vertexSvg from './assets/aaa.svg';
import edgeSvg from './assets/collaboration-full.svg';
import {useTranslation} from 'react-i18next';

const GraphDetail = () => {
    const [graphspaceInfo, setGraphspaceInfo] = useState({});
    const [graphIno, setGraphInfo] = useState({});
    const [loading, setLoading] = useState({graph: true, graphspace: true});
    const [statistic, setStatistic] = useState({});
    const [statisticError, setStatisticError] = useState(false);
    const [pageError, setPageError] = useState(false);
    const {graphspace, graph} = useParams();
    const navigate = useNavigate();
    const {t} = useTranslation();

    const handleBack = useCallback(() => {
        navigate(-1);
    }, [navigate]);

    const handleUpdate = useCallback(() => {
        api.manage.updateGraphStatistic(graphspace, graph).then(res => {
            if (res.status === 200) {
                message.success(t('graph.detail.update_success'));
                return;
            }
        }).catch(() => {});
    }, [graphspace, graph, t]);

    const formatList = data => {
        if (!data || Object.keys(data).length === 0) {
            return [];
        }

        return Object.keys(data).map(item => ({key: item, num: data[item]}));
    };

    useEffect(() => {
        if (!graphspace || !graph) {
            return;
        }

        const inlineErrorConfig = {suppressBusinessErrorToast: true};
        api.manage.getGraphSpace(graphspace, inlineErrorConfig).then(res => {
            if (res.status === 200) {
                setGraphspaceInfo(res.data);
                setLoading(l => ({...l, graphspace: false}));
                return;
            }
            setPageError(true);
            setLoading(l => ({...l, graphspace: false}));
        }).catch(() => {
            setPageError(true);
            setLoading(l => ({...l, graphspace: false}));
        });

        api.manage.getGraph(graphspace, graph, inlineErrorConfig).then(res => {
            if (res.status === 200) {
                setGraphInfo(res.data);
                setLoading(l => ({...l, graph: false}));
                return;
            }

            setPageError(true);
            setGraphInfo({});
            setLoading(l => ({...l, graph: false}));
        }).catch(() => {
            setPageError(true);
            setGraphInfo({});
            setLoading(l => ({...l, graph: false}));
        });

        api.manage.getGraphStatistic(graphspace, graph, inlineErrorConfig).then(res => {
            if (res.status === 200) {
                setStatistic(res.data);
                setStatisticError(false);
                return;
            }
            setStatisticError(true);
        }).catch(() => {
            setStatisticError(true);
        });
    }, [graphspace, graph]);

    const pageTitle = `${graphspaceInfo.nickname ?? graphspace} - `
                      + `${graphIno.nickname ?? graph} - ${t('graph.detail.title')}`;

    return (
        <Spin spinning={loading.graph || loading.graphspace}>
            {!loading.graph && !loading.graphspace && (
                <>
                    <PageHeader
                        ghost={false}
                        onBack={handleBack}
                        title={pageTitle}
                    />

                    <div className={'container'}>
                        <>
                            {pageError ? (
                                <Alert
                                    type='error'
                                    showIcon
                                    message={t('graph.detail.unavailable')}
                                />
                            ) : statisticError && (
                                <Alert
                                    type='warning'
                                    showIcon
                                    message={t('graph.detail.statistics_unavailable')}
                                />
                            )}
                            {!pageError && (
                                <Row justify='end' className={style.top}>
                                    <Col>
                                        <Space>
                                            <span>
                                                {t('graph.detail.last_update')}
                                                {statistic.update_time ?? '--/--'}
                                            </span>
                                            <Button type='primary' onClick={handleUpdate}>
                                                {t('graph.detail.update_data')}
                                            </Button>
                                        </Space>
                                    </Col>
                                </Row>
                            )}

                            {!pageError && (
                                <Row gutter={[10, 10]}>
                                    <Col span={12}>
                                        <div>
                                            <Row className={style.type}>
                                                <Col span={6} className={style.vertex}>
                                                    <img width={20} src={vertexSvg} />
                                                    <span>{t('graph.detail.vertex_total')}</span>
                                                </Col>
                                                <Col span={18}>{statistic.vertex_count ?? 0}</Col>
                                            </Row>
                                            <Table
                                                columns={[
                                                    {title: t('graph.detail.vertex_type'), dataIndex: 'key'},
                                                    {title: t('graph.detail.count'), dataIndex: 'num'},
                                                ]}
                                                dataSource={formatList(statistic.vertices)}
                                                className={style.card}
                                                pagination={false}
                                            />
                                        </div>
                                    </Col>

                                    <Col span={12}>
                                        <div>
                                            <Row className={style.type}>
                                                <Col span={6} className={style.edge}>
                                                    <img width={20} src={edgeSvg} />
                                                    {t('graph.detail.edge_total')}
                                                </Col>
                                                <Col span={18}>{statistic.edge_count ?? 0}</Col>
                                            </Row>
                                            <Table
                                                columns={[
                                                    {title: t('graph.detail.edge_type'), dataIndex: 'key'},
                                                    {title: t('graph.detail.count'), dataIndex: 'num'},
                                                ]}
                                                dataSource={formatList(statistic.edges)}
                                                pagination={false}
                                                className={style.card}
                                            />
                                        </div>
                                    </Col>
                                </Row>
                            )}
                        </>
                    </div>
                </>
            )}
        </Spin>
    );
};

export default GraphDetail;
