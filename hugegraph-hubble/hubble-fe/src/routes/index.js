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

import {Navigate, Routes, Route, useParams} from 'react-router-dom';
import Datasource from '../pages/Datasource';
import Task from '../pages/Task';
import TaskEdit from '../pages/TaskEdit/index';
import TaskDetail from '../pages/TaskDetail';
import Schema from '../pages/Schema';
import Login from '../pages/Login';
import Graph from '../pages/Graph';
import GraphSpace from '../pages/GraphSpace';
import Meta from '../pages/Meta';
import GraphDetail from '../pages/GraphDetail';
import My from '../pages/My';
// import Super from '../pages/Super';
import Account from '../pages/Account';
import RoleAuth from '../pages/Role/Auth';
import Navigation from '../pages/Navigation';
import Error404 from '../pages/Error404';
import Test from '../pages/Test';

// 图分析的路由
import GraphAnalysis from '../pages/GraphAnalysis';
import AsyncTaskResultPage from '../pages/AsyncTaskResult';
import {isPdEnabled} from '../utils/config';
import {
    DEFAULT_GRAPHSPACE,
    shouldUseNonPdDefaultGraphspace,
} from '../utils/productMode';

const PdOnlyRoute = ({children, fallback = '/navigation'}) => {
    return isPdEnabled() ? children : <Navigate to={fallback} replace />;
};

const GraphSpaceListRoute = () => {
    return isPdEnabled()
        ? <GraphSpace />
        : <Navigate to={`/graphspace/${DEFAULT_GRAPHSPACE}`} replace />;
};

const GraphRoute = () => {
    const {graphspace} = useParams();

    if (shouldUseNonPdDefaultGraphspace(isPdEnabled(), graphspace)) {
        return <Navigate to={`/graphspace/${DEFAULT_GRAPHSPACE}`} replace />;
    }

    return <Graph />;
};

const GraphspaceParamRoute = ({children, fallback}) => {
    const {graphspace, graphSpace} = useParams();
    const currentGraphspace = graphspace ?? graphSpace;

    if (shouldUseNonPdDefaultGraphspace(isPdEnabled(), currentGraphspace)) {
        return <Navigate to={fallback ?? `/graphspace/${DEFAULT_GRAPHSPACE}`} replace />;
    }

    return children;
};

const RouteList = ({element}) => {
    return (
        <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/" element={element}>
                <Route index element={<Navigation />} />
                <Route path="/login" element={<Login />} />
                <Route path="/source" element={<Datasource />} />
                <Route
                    path="/graphspace/:graphspace/schema"
                    element={(
                        <PdOnlyRoute fallback={`/graphspace/${DEFAULT_GRAPHSPACE}`}>
                            <Schema />
                        </PdOnlyRoute>
                    )}
                />
                <Route path="/graphspace/:graphspace" element={<GraphRoute />} />
                <Route path='/graphspace' element={<GraphSpaceListRoute />} />
                <Route
                    path='/graphspace/:graphspace/graph/:graph/meta'
                    element={(
                        <GraphspaceParamRoute>
                            <Meta />
                        </GraphspaceParamRoute>
                    )}
                />
                <Route
                    path='/graphspace/:graphspace/graph/:graph/detail'
                    element={(
                        <GraphspaceParamRoute>
                            <GraphDetail />
                        </GraphspaceParamRoute>
                    )}
                />

                <Route path="/task" element={<Task />} />
                <Route path="/task/edit" element={<TaskEdit />} />
                <Route path="/task/detail/:taskid" element={<TaskDetail />} />

                <Route path='/my' element={<My />} />
                <Route
                    path='/account'
                    element={<PdOnlyRoute fallback='/my'><Account /></PdOnlyRoute>}
                />
                {/* <Route path='/role' element={<Role />} /> */}
                <Route
                    path='/role/graphspace/:graphspace/:role'
                    element={<PdOnlyRoute><RoleAuth /></PdOnlyRoute>}
                />
                {/* <Route path='/resource' element={<Resource />} /> */}
                {/* <Route path="/:moduleName" element={<GraphAnalysis />} /> */}
                <Route path="/gremlin" element={<GraphAnalysis moduleName={'gremlin'} />} />
                <Route path="/algorithms" element={<GraphAnalysis moduleName={'algorithms'} />} />
                <Route path="/asyncTasks" element={<GraphAnalysis moduleName={'asyncTasks'} />} />
                {/* 从数据管理带图空间和图信息跳转到图分析平台的路由 */}
                {/* <Route path="/:moduleName/:graphSpace/:graph" element={<GraphAnalysis />} />
                <Route path="/:moduleName/:taskId" element={<GraphAnalysis />} /> */}
                <Route
                    path="/gremlin/:graphSpace/:graph"
                    element={(
                        <GraphspaceParamRoute fallback='/gremlin'>
                            <GraphAnalysis moduleName={'gremlin'} />
                        </GraphspaceParamRoute>
                    )}
                />
                <Route path="/gremlin/:taskId" element={<GraphAnalysis moduleName={'gremlin'} />} />
                <Route
                    path="/algorithms/:graphSpace/:graph"
                    element={(
                        <GraphspaceParamRoute fallback='/algorithms'>
                            <GraphAnalysis moduleName={'algorithms'} />
                        </GraphspaceParamRoute>
                    )}
                />
                <Route path="/algorithms/:taskId" element={<GraphAnalysis moduleName={'algorithms'} />} />
                <Route
                    path="/asyncTasks/:graphSpace/:graph"
                    element={(
                        <GraphspaceParamRoute fallback='/asyncTasks'>
                            <GraphAnalysis moduleName={'asyncTasks'} />
                        </GraphspaceParamRoute>
                    )}
                />
                <Route path="/asyncTasks/:taskId" element={<GraphAnalysis moduleName={'asyncTasks'} />} />
                <Route
                    path="/asyncTasks/result/:graphspace/:graph/:taskId"
                    element={(
                        <GraphspaceParamRoute fallback='/asyncTasks'>
                            <AsyncTaskResultPage />
                        </GraphspaceParamRoute>
                    )}
                />

                <Route path="/navigation" element={<Navigation />} />
                <Route path="*" element={<Error404 />} />
            </Route>


            <Route path="/test" element={<Test />} />
        </Routes>
    );
};

export default RouteList;
