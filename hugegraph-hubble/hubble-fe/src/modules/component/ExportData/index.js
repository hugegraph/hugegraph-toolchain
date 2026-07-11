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
 * @file  导出画布和数据
 */

import React, {useState, useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {Button, Tooltip, Dropdown, Modal, Form, Input} from 'antd';
import {DownloadOutlined} from '@ant-design/icons';

const ExportData = props => {
    const {t} = useTranslation();
    const {
        buttonEnable,
        onExportJsonChange,
        onExportPngChange,
        tooltip,
    } = props;

    const [exportPngForm] = Form.useForm();
    const [exportJsonForm] = Form.useForm();

    const [isExportPngVisible, setExportPngVisible] = useState(false);
    const [isExportJsonVisible, setExportJsonVisible] = useState(false);

    const handleClickExportJson = useCallback(
        () => {
            setExportJsonVisible(true);
        },
        []
    );

    const handleClickExportPng = useCallback(
        () => {
            setExportPngVisible(true);
        },
        []
    );

    const handleExportJsonOk = useCallback(
        () => {
            exportJsonForm.submit();
        },
        [exportJsonForm]
    );

    const handleExportJsonCancel = useCallback(
        () => {
            setExportJsonVisible(false);
            exportJsonForm.resetFields();
        },
        [exportJsonForm]
    );

    const handleExportPngOk = useCallback(
        () => {
            exportPngForm.submit();
        },
        [exportPngForm]
    );

    const handleExportPngCancel = useCallback(
        () => {
            setExportPngVisible(false);
            exportPngForm.resetFields();
        },
        [exportPngForm]
    );


    const handleExportPngFinish = useCallback(
        values => {
            const {filename} = values;
            onExportPngChange(filename);
            setExportPngVisible(false);
            exportPngForm.resetFields();
        },
        [exportPngForm, onExportPngChange]
    );

    const handleExportJsonFinish = useCallback(
        values => {
            const {exportFileName} = values;
            onExportJsonChange(exportFileName);
            setExportJsonVisible(false);
            exportJsonForm.resetFields();
        },
        [exportJsonForm, onExportJsonChange]
    );

    const exportMenu = {
        items: [
            {
                key: '1',
                label: (<a onClick={handleClickExportJson}>{t('analysis.canvas.export_json')}</a>),
            },
            {
                key: '2',
                label: (<a onClick={handleClickExportPng}>{t('analysis.canvas.export_png')}</a>),
            },
        ],
    };

    return (
        <>
            <Dropdown menu={exportMenu} placement="bottomLeft" disabled={!buttonEnable}>
                <Tooltip placement="bottom" title={buttonEnable ? '' : tooltip}>
                    <Button
                        type='text'
                        icon={<DownloadOutlined />}
                        disabled={!buttonEnable}
                    >
                        {t('analysis.canvas.export')}
                    </Button>
                </Tooltip>
            </Dropdown>
            <Modal
                width={600}
                title={t('analysis.canvas.export_json_title')}
                open={isExportJsonVisible}
                onOk={handleExportJsonOk}
                onCancel={handleExportJsonCancel}
            >
                <Form name='fileConfig' form={exportJsonForm} onFinish={handleExportJsonFinish}>
                    <Form.Item
                        label={t('analysis.canvas.file_name')}
                        name="exportFileName"
                        rules={[{required: true, message: t('analysis.canvas.file_name_required')}]}
                    >
                        <Input placeholder={t('analysis.canvas.file_name_placeholder')} maxLength="12" showCount />
                    </Form.Item>
                </Form>
            </Modal>
            <Modal
                width={600}
                title={t('analysis.canvas.export_image_title')}
                open={isExportPngVisible}
                onOk={handleExportPngOk}
                onCancel={handleExportPngCancel}
            >
                <Form name='fileConfig' form={exportPngForm} onFinish={handleExportPngFinish}>
                    <Form.Item
                        label={t('analysis.canvas.file_name')}
                        name="filename"
                        rules={[{required: true, message: t('analysis.canvas.file_name_required')}]}
                    >
                        <Input placeholder={t('analysis.canvas.file_name_placeholder')} maxLength="12" showCount />
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
};

export default ExportData;
