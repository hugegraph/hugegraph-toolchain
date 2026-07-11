/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * Licensed under the Apache License, Version 2.0.
 */

import {Modal} from 'antd';

let warningOpen = false;

export const showThrottleWarning = content => {
    if (warningOpen) {
        return;
    }
    warningOpen = true;
    const close = () => {
        warningOpen = false;
    };
    Modal.warning({content, onOk: close, afterClose: close});
};
