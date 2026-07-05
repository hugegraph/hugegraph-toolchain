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
 * @file 算法表单目录Home
 */

import React, {useCallback, useState} from 'react';
import {Empty} from 'antd';
import OlapFormHome from '../OlapHome';
import OltpFormHome from '../OltpHome';
import AlgorithmSearch from '../../AlgorithmSearch';
import {
    isAlgorithmNameMatched,
    useTranslatedConstants,
} from '../../../../utils/constants';
import c from './index.module.scss';
import _ from 'lodash';
import {useTranslation} from 'react-i18next';

const AlgorithmFormHome = props => {
    const {
        handleOltpFormSubmit,
        handleOlapFormSubmit,
        currentAlgorithm,
        updateCurrentAlgorithm,
    } =  props;
    const {t} = useTranslation();
    const {ALGORITHM_NAME} = useTranslatedConstants();
    const [search, setSearch] = useState('');

    const handleSearch = useCallback(value => {
        setSearch(value);
    }, []);

    const isListEmpty = _.isEmpty(
        Object.values(ALGORITHM_NAME).filter(item => isAlgorithmNameMatched(item, search, t))
    );

    return (
        <div className={c.algorithmSidebar}>
            <AlgorithmSearch onSearch={handleSearch} />
            {isListEmpty && (
                <Empty
                    className={c.listEmpty}
                    description={t('query_result.empty')}
                />
            )}
            <OltpFormHome
                onOltpFormSubmit={handleOltpFormSubmit}
                search={search}
                currentAlgorithm={currentAlgorithm}
                updateCurrentAlgorithm={updateCurrentAlgorithm}
            />
            <OlapFormHome
                onOlapFormSubmit={handleOlapFormSubmit}
                search={search}
                currentAlgorithm={currentAlgorithm}
                updateCurrentAlgorithm={updateCurrentAlgorithm}
            />
        </div>
    );
};

export default AlgorithmFormHome;
