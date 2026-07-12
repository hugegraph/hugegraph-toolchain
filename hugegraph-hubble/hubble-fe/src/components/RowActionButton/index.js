/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {Button} from 'antd';
import {useCallback} from 'react';

const RowActionButton = ({onAction, value, children}) => {
    const handleClick = useCallback(() => onAction(value), [onAction, value]);

    return <Button type='link' onClick={handleClick}>{children}</Button>;
};

export default RowActionButton;
