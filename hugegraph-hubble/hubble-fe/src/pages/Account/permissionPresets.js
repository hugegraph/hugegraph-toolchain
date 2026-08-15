/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

const PERMISSION_PRESETS = Object.freeze({
    SUPER_ADMIN: 'SUPER_ADMIN',
    GS_READ_ONLY: 'GS_READ_ONLY',
    GS_READ_WRITE: 'GS_READ_WRITE',
    GS_ADMIN: 'GS_ADMIN',
});

const presetKeys = Object.values(PERMISSION_PRESETS);

const getAccountPreset = account => {
    const explicit = account?.permission_preset
        ?? account?.permissionPreset
        ?? account?.access_level;
    if (presetKeys.includes(explicit)) {
        return explicit;
    }
    if (account?.is_superadmin) {
        return PERMISSION_PRESETS.SUPER_ADMIN;
    }
    const scopedPresets = (account?.graphspace_permissions ?? [])
        .map(permission => permission?.permission_preset)
        .filter(preset => presetKeys.includes(preset));
    if (scopedPresets.includes(PERMISSION_PRESETS.GS_ADMIN)) {
        return PERMISSION_PRESETS.GS_ADMIN;
    }
    if (scopedPresets.includes(PERMISSION_PRESETS.GS_READ_WRITE)) {
        return PERMISSION_PRESETS.GS_READ_WRITE;
    }
    if (Array.isArray(account?.adminSpaces) && account.adminSpaces.length > 0) {
        return PERMISSION_PRESETS.GS_ADMIN;
    }
    return PERMISSION_PRESETS.GS_READ_ONLY;
};

const getPresetSpaces = account => {
    const spaces = account?.graphspace_permissions ?? account?.adminSpaces;
    return Array.isArray(spaces)
        ? spaces.map(space => (typeof space === 'string'
            ? space : space.name ?? space.graphspace)).filter(Boolean)
        : [];
};

// This is the only compatibility mapping used by account forms.
const toPermissionPayload = values => {
    const preset = values.permission_preset ?? PERMISSION_PRESETS.GS_READ_ONLY;
    const spaces = values.graphspaces ?? [];
    const payload = {...values};
    delete payload.permission_preset;
    delete payload.graphspaces;
    return {
        ...payload,
        permission_preset: preset,
        graphspace_permissions: spaces.map(graphspace => ({
            graphspace,
            permission_preset: preset,
        })),
        is_superadmin: preset === PERMISSION_PRESETS.SUPER_ADMIN,
        adminSpaces: preset === PERMISSION_PRESETS.GS_ADMIN ? spaces : [],
    };
};

export {PERMISSION_PRESETS, getAccountPreset, getPresetSpaces, toPermissionPayload};
