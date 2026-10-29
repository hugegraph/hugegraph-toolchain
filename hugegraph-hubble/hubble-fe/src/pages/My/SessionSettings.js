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

import {Alert, Button, Card, InputNumber, Space, Typography, message} from 'antd';
import {useCallback, useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import * as api from '../../api';

const SessionSettings = () => {
    const {t} = useTranslation();
    const [settings, setSettings] = useState(null);
    const [minutes, setMinutes] = useState(null);
    const [failed, setFailed] = useState(false);
    const [saving, setSaving] = useState(false);
    const requestRef = useRef(null);
    const load = useCallback(async () => {
        const request = Symbol('session-settings');
        requestRef.current = request;
        setFailed(false);
        try {
            const res = await api.auth.getSessionSettings();
            if (requestRef.current !== request) {
                return;
            }
            if (res?.status !== 200) {
                throw new Error('session settings unavailable');
            }
            setSettings(res.data);
            setMinutes(res.data.idle_timeout_seconds / 60);
        }
        catch {
            if (requestRef.current === request) {
                setFailed(true);
            }
        }
    }, []);
    useEffect(() => {
        load();
        return () => { requestRef.current = null; };
    }, [load]);
    const save = async () => {
        const request = Symbol('session-save');
        requestRef.current = request;
        setSaving(true);
        try {
            const res = await api.auth.updateSessionSettings(minutes * 60);
            if (requestRef.current === request && res?.status === 200) {
                setSettings(res.data);
                setMinutes(res.data.idle_timeout_seconds / 60);
                message.success(t('my.session.saved'));
            }
        }
        catch {
            // The API layer displays save errors; keep the user's input for retry.
        }
        finally {
            if (requestRef.current === request) {
                setSaving(false);
            }
        }
    };
    const min = settings?.min_idle_seconds / 60;
    const max = settings?.max_idle_seconds / 60;
    const readOnly = settings && max < min;
    const valid = Number.isInteger(minutes) && minutes >= min && minutes <= max;
    return (
        <Card title={t('my.session.title')} style={{width: 'min(100%, 780px)'}}>
            <Typography.Paragraph>{t('my.session.description')}</Typography.Paragraph>
            {failed ? <Alert type='error' title={t('my.session.unavailable')}
                action={<Button onClick={load}>{t('my.load.retry')}</Button>} /> : (
                <Space wrap>
                    <label htmlFor='session-idle-minutes'>{t('my.session.minutes')}</label>
                    <InputNumber id='session-idle-minutes' value={minutes} onChange={setMinutes}
                        min={min} max={max} precision={0} disabled={!settings || saving || readOnly} />
                    <Button type='primary' onClick={save} loading={saving}
                        disabled={!valid || minutes * 60 === settings?.idle_timeout_seconds}>
                        {t('common.action.save')}
                    </Button>
                    {settings && <Typography.Text type='secondary'>
                        {readOnly ? t('my.session.deploymentOnly') : t('my.session.range', {min, max})}
                    </Typography.Text>}
                </Space>
            )}
        </Card>
    );
};
export default SessionSettings;
