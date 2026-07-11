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

import React from 'react';
import {Button, Result} from 'antd';

class RouteErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {failed: false};
        this.reload = this.reload.bind(this);
    }

    static getDerivedStateFromError() {
        return {failed: true};
    }

    componentDidCatch(error, info) {
        console.error('Route render failed', error, info);
    }

    reload() {
        window.location.reload();
    }

    render() {
        if (this.state.failed) {
            return (
                <Result
                    status='error'
                    title='Page failed to load'
                    extra={<Button onClick={this.reload}>Reload</Button>}
                />
            );
        }
        return this.props.children;
    }
}

export default RouteErrorBoundary;
