/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {Alert, Button} from 'antd';
import {useTranslation} from 'react-i18next';

const MetaTableStatus = ({error, onRetry}) => {
    const {t} = useTranslation();
    if (!error) {
        return null;
    }
    return (
        <Alert
            type='error'
            showIcon
            message={t('schema.list_failed')}
            action={(
                <Button size='small' onClick={onRetry}>
                    {t('schema.retry')}
                </Button>
            )}
        />
    );
};

export default MetaTableStatus;
