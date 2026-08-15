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

import Route from './routes';
import 'antd/dist/antd.css';
import './App.scss';
import './App.css';
import './styles/workbench.scss';
import Layout from './layout.ant';
import {AuthContextProvider} from './auth/AuthContext';
import * as api from './api';
import {setConfig} from './utils/config';
import {useEffect, useState} from 'react';

function App() {
    const [configReady, setConfigReady] = useState(false);
    const [configError, setConfigError] = useState(false);

    useEffect(() => {
        let active = true;
        api.config.getConfig()
            .then(response => {
                if (response?.status !== 200 || !response.data) {
                    throw new Error('invalid_hubble_config');
                }
                if (active) {
                    setConfig(response.data);
                    setConfigReady(true);
                }
            })
            .catch(() => {
                if (active) {
                    setConfigError(true);
                }
            });
        return () => {
            active = false;
        };
    }, []);

    if (configError) {
        return (
            <div role='alert'>
                Unable to load Hubble configuration.
                <button type='button' onClick={() => window.location.reload()}>
                    Retry
                </button>
            </div>
        );
    }
    if (!configReady) {
        return null;
    }
    return (
        <div>
            <AuthContextProvider>
                <Route element={<Layout />} />
            </AuthContextProvider>
        </div>
    );
};

export default App;
