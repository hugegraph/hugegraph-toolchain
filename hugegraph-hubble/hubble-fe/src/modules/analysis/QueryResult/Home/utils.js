/*
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

const isJsonBigNumber = value => {
    return value !== null
        && typeof value === 'object'
        && typeof value.constructor?.isBigNumber === 'function'
        && value.constructor.isBigNumber(value)
        && typeof value.toString === 'function';
};

const projectJsonValue = (value, ancestors = new WeakSet()) => {
    if (typeof value === 'bigint' || isJsonBigNumber(value)) {
        return value.toString();
    }
    if (value === null || typeof value !== 'object') {
        return value;
    }
    if (ancestors.has(value)) {
        return '[Circular]';
    }
    ancestors.add(value);
    const projected = Array.isArray(value) ? [] : {};
    Object.keys(value).forEach(key => {
        Object.defineProperty(projected, key, {
            configurable: true,
            enumerable: true,
            value: projectJsonValue(value[key], ancestors),
            writable: true,
        });
    });
    ancestors.delete(value);
    return projected;
};

const getJsonViewContent = jsonView => {
    const projected = projectJsonValue(jsonView?.data ?? []);
    if (projected !== null && typeof projected === 'object') {
        return projected;
    }
    return {value: projected};
};

export {getJsonViewContent, isJsonBigNumber, projectJsonValue};
