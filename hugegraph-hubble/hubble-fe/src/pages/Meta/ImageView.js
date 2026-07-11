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

import {useEffect, useCallback, useState} from 'react';
import {useParams} from 'react-router-dom';
import * as api from '../../api';
import GraphView from '../../components/GraphinView';
import {EditPropertyLayer} from './Property/EditLayer';
import {EditVertexLayer} from './Vertex/EditLayer';
import {EditEdgeLayer} from './Edge/EditLayer';
import PropertyTable from './Property';
import {Button, Row, Space, Col, Drawer} from 'antd';
import {formatToGraphInData} from '../../utils/formatGraphInData';
import {useTranslation} from 'react-i18next';

const ImageView = () => {
    const {t} = useTranslation();
    // const graphRef = useRef(null);
    const {graphspace, graph} = useParams();
    const [data, setData] = useState({vertices: [], edges: []});
    const [propertyVisible, setPropertyVisible] = useState(false);
    const [vertexVisible, setVertexVisible] = useState(false);
    const [edgeVisible, setEdgeVisible] = useState(false);
    const [propertyListVisible, setPropertyListVisible] = useState(false);
    const [vertexName, setVertexName] = useState('');
    const [edgeName, setEdgeName] = useState('');
    const [propertyList, setPropertyList] = useState([]);
    const [vertexList, setVertexList] = useState([]);
    const [refresh, setRefresh] = useState(false);

    const handleRefresh = useCallback(() => {
        setRefresh(!refresh);
    }, [refresh]);

    const hideVertexLayer = useCallback(() => {
        setVertexVisible(false);
    }, []);

    const hideEdgeLayer = useCallback(() => {
        setEdgeVisible(false);
    }, []);

    const hidePropertyLayer = useCallback(() => {
        setPropertyVisible(false);
    }, []);

    const hidePropertyListLayer = useCallback(() => {
        setPropertyListVisible(false);
    }, []);

    const createProperty = useCallback(() => {
        setPropertyVisible(true);
    }, []);

    const createVertex = useCallback(() => {
        setVertexName('');
        setVertexVisible(true);
    }, []);

    const createEdge = useCallback(() => {
        setEdgeName('');
        setEdgeVisible(true);
    }, []);

    const showPropertyList = useCallback(() => {
        setPropertyListVisible(true);
    }, []);

    const handleClick = useCallback((id, type, data) => {
        if (type === 'node') {
            setVertexVisible(true);
            setEdgeVisible(false);
            setVertexName(data.label);
        }

        if (type === 'edge') {
            setVertexVisible(false);
            setEdgeVisible(true);
            setEdgeName(data.label);
        }
    }, []);

    useEffect(() => {
        api.manage.getGraphView(graphspace, graph).then(res => {
            if (res.status === 200) {
                const {data} = res;

                setData(formatToGraphInData(data));
            }
        });

        api.manage.getMetaVertexList(graphspace, graph, {page_size: -1}).then(res => {
            if (res.status === 200) {
                setVertexList(res.data.records.map(item => ({label: item.name, value: item.name})));
            }
        });

        api.manage.getMetaPropertyList(graphspace, graph, {page_size: -1}).then(res => {
            if (res.status === 200) {
                setPropertyList(res.data.records.map(item => ({
                    lable: item.name,
                    value: item.name,
                    data_type: item.data_type,
                })));
            }
        });
    }, [refresh, graph, graphspace]);

    return (
        <div style={{textAlign: 'center'}}>
            {/* <div ref={graphRef} style={{display: 'inline-block', width: 1000, height: 600}} /> */}
            <Row>
                <Col>
                    <Space>
                        <Button onClick={createProperty}>{t('schema.property.create')}</Button>
                        <Button onClick={createVertex}>{t('schema.vertex.create')}</Button>
                        <Button onClick={createEdge}>{t('schema.edge.form.title_create')}</Button>
                        <Button onClick={showPropertyList}>{t('schema.image_view.view_properties')}</Button>
                    </Space>
                </Col>
            </Row>
            <GraphView
                data={data}
                config={{
                    minZoom: 0.5,
                    maxZoom: 2,
                    fitCenter: true,
                }}
                layout={{
                    type: 'gForce',
                    gravity: 10,
                    linkDistance: 150,
                }}
                onClick={handleClick}
                height={600}
            />

            <EditVertexLayer
                visible={vertexVisible}
                onCancle={hideVertexLayer}
                graph={graph}
                graphspace={graphspace}
                refresh={handleRefresh}
                name={vertexName}
                propertyList={propertyList}
            />

            <EditEdgeLayer
                visible={edgeVisible}
                graphspace={graphspace}
                graph={graph}
                onCancle={hideEdgeLayer}
                refresh={handleRefresh}
                name={edgeName}
                propertyList={propertyList}
                vertexList={vertexList}
            />

            <EditPropertyLayer
                visible={propertyVisible}
                onCancle={hidePropertyLayer}
                graphspace={graphspace}
                graph={graph}
                refresh={handleRefresh}
            />

            <Drawer
                open={propertyListVisible}
                onClose={hidePropertyListLayer}
                width={600}
                mask={false}
                title={t('schema.image_view.view_properties')}
            >
                <PropertyTable noHeader forceRefresh={refresh} />
            </Drawer>
        </div>
    );
};

export default ImageView;
