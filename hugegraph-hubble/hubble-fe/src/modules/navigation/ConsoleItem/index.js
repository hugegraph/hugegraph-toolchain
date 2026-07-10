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
 * @file 运维管理子项块
 */
import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';

import * as api from '../../../api';
import Item from '../Item';
import {normalizeDashboardUrl, probeDashboard} from './dashboard';

const ConsoleItem = () => {

    const {t} = useTranslation();
    const [dashboard, setDashboard] = useState({status: 'loading', url: ''});

    useEffect(() => {
        let cancelled = false;
        const loadDashboard = async () => {
            try {
                const res = await api.auth.getDashboard();
                if (res?.status !== 200) {
                    if (!cancelled) {
                        setDashboard({status: 'unavailable', url: ''});
                    }
                    return;
                }
                const url = normalizeDashboardUrl(res.data?.address);
                const reachable = await probeDashboard(url);
                if (!cancelled) {
                    setDashboard({
                        status: reachable ? 'available' : 'unavailable',
                        url,
                    });
                }
            }
            catch {
                if (!cancelled) {
                    setDashboard({status: 'unavailable', url: ''});
                }
            }
        };
        loadDashboard();
        return () => {
            cancelled = true;
        };
    }, []);
    const available = dashboard.status === 'available';
    const reason = dashboard.status === 'loading'
        ? t('navigation_page.dashboard_checking')
        : t('navigation_page.dashboard_unavailable');
    const clusterUrl = available ? dashboard.url : '';
    const item = (titleKey, path = '') => ({
        title: t(titleKey),
        url: clusterUrl ? clusterUrl + path : '',
        disabled: !available,
        reason: available ? '' : reason,
    });

    return (
        <Item
            btnIndex={4}
            btnTitle={t('navigation_page.operation_manage')}
            listData={[
                item('navigation_page.cluster_manage'),
                item('navigation_page.monitor_manage', '/monitor/machine'),
                item('navigation_page.node_manage', '/operate/node'),
                item('navigation_page.alert_manage', '/alert/rule'),
            ]}
        />
    );
};

export default ConsoleItem;
