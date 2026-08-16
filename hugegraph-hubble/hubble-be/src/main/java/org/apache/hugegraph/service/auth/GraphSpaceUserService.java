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

package org.apache.hugegraph.service.auth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.log4j.Log4j2;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.auth.BelongEntity;
import org.apache.hugegraph.entity.auth.RoleEntity;
import org.apache.hugegraph.entity.auth.UserView;
import org.apache.hugegraph.structure.auth.User;
import org.apache.hugegraph.util.E;
import org.apache.hugegraph.util.PageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class GraphSpaceUserService extends AuthService {

    @Autowired
    private BelongService belongService;

    public List<UserView> listUsers(HugeClient client, String graphSpace) {
        Map<String, UserView> users = new java.util.LinkedHashMap<>();
        List<BelongEntity> belongs = this.belongService.list(
                client, graphSpace, null, null);

        Multimap<String, BelongEntity> grouped = ArrayListMultimap.create();
        belongs.forEach(belong -> {
            grouped.put(belong.getUserId(), belong);
        });

        grouped.keySet().forEach(userId -> {
            UserView user = new UserView(null, null, new ArrayList<>());
            grouped.get(userId).forEach(belong -> {
                user.setId(belong.getUserId());
                user.setName(belong.getUserName());
                user.addRole(new RoleEntity(belong.getRoleId(),
                                            belong.getRoleName()));
            });
            users.put(userId, user);
        });
        client.auth().listSpaceMember(graphSpace).forEach(username -> {
            User account = client.findUserByName(username);
            if (account == null) {
                return;
            }
            String userId = account.id().toString();
            UserView user = users.computeIfAbsent(userId,
                    id -> new UserView(id, username, new ArrayList<>()));
            if (client.supportsDefaultRole()) {
                this.addDefaultRole(client, graphSpace, user, username, "observer");
                this.addDefaultRole(client, graphSpace, user, username, "analyst");
            }
        });
        return new ArrayList<>(users.values());
    }

    public UserView getUser(HugeClient client, String graphSpace,
                            String userId) {
        List<BelongEntity> belongs = this.belongService.list(
                client, graphSpace, null, userId);
        UserView user = new UserView(null, null,
                                     new ArrayList<>(belongs.size()));
        belongs.forEach(belong -> {
            user.setId(belong.getUserId());
            user.setName(belong.getUserName());
            user.addRole(new RoleEntity(belong.getRoleId(),
                                        belong.getRoleName()));
        });
        if (user.getId() == null) {
            User account = client.auth().getUser(userId);
            if (account != null) {
                user.setId(account.id().toString());
                user.setName(account.name());
                if (client.supportsDefaultRole()) {
                    this.addDefaultRole(client, graphSpace, user,
                                        account.name(), "observer");
                    this.addDefaultRole(client, graphSpace, user,
                                        account.name(), "analyst");
                }
            }
        }
        return user;
    }

    public IPage<UserView> queryPage(HugeClient client, String graphSpace,
                                     String query, int pageNo, int pageSize) {
        List<UserView> results =
                this.listUsers(client, graphSpace).stream()
                    .filter(user -> user.getName().contains(query))
                    .sorted(Comparator.comparing(UserView::getName))
                    .collect(Collectors.toList());
        return PageUtil.page(results, pageNo, pageSize);
    }

    public UserView createOrUpdate(HugeClient client, String graphSpace,
                                   UserView userView) {
        E.checkNotNull(userView.getId(), "User Id Not Null");
        E.checkArgument(userView.getRoles() != null &&
                        !userView.getRoles().isEmpty(),
                        "The role info is empty");

        Set<String> newRoles =
                userView.getRoles().stream()
                        .map(RoleEntity::getId)
                        .collect(Collectors.toSet());
        newRoles.forEach(roleId -> RoleService.requireScopedGroup(
                client.auth(), graphSpace, roleId));
        User account = client.auth().getUser(userView.getId());
        E.checkNotNull(account, "User");
        String username = account.name();
        E.checkArgument(username != null && !username.isEmpty(),
                        "The user name is empty");
        List<BelongEntity> current = this.belongService.list(
                client, graphSpace, null, userView.getId());
        if (!client.auth().listSpaceMember(graphSpace).contains(username)) {
            client.auth().addSpaceMember(username, graphSpace);
        }
        current.forEach(belong -> {
            if (!newRoles.contains(belong.getRoleId())) {
                this.belongService.deleteById(client, graphSpace,
                                              belong.getId());
            }
        });
        Set<String> currentRoles = current.stream()
                                          .map(BelongEntity::getRoleId)
                                          .collect(Collectors.toSet());

        userView.getRoles().forEach(role -> {
            if (!currentRoles.contains(role.getId())) {
                this.belongService.add(client, graphSpace, role.getId(),
                                       userView.getId());
            }
        });
        return this.getUser(client, graphSpace, userView.getId());
    }

    public void applyPermissionPresets(HugeClient client, String username,
                                       List<Map<String, String>> permissions,
                                       String preset) {
        if (preset == null || "SUPER_ADMIN".equals(preset)) {
            return;
        }
        E.checkArgument(client.supportsDefaultRole(), "Permission presets require HugeGraph Server 1.8+");
        User account = client.findUserByName(username);
        if (account == null) {
            return;
        }
        Map<String, String> desired = new java.util.LinkedHashMap<>();
        List<Map<String, String>> requested = permissions == null ? new ArrayList<>() : permissions;
        for (Map<String, String> permission : requested) {
            String graphSpace = permission.get("graphspace");
            String permissionPreset = permission.get("permission_preset");
            if (graphSpace != null) {
                desired.put(graphSpace, permissionPreset);
            }
        }
        for (String graphSpace : client.graphSpace().listGraphSpace()) {
            String desiredPreset = desired.get(graphSpace);
            if (desiredPreset == null) {
                this.unauthUser(client, graphSpace,
                                account.id().toString());
            } else {
                this.applySpacePreset(client, graphSpace,
                                      account.id().toString(),
                                      desiredPreset);
            }
        }
    }

    public void validatePermissionPresets(
            HugeClient client, List<Map<String, String>> permissions,
            String preset) {
        if (preset == null || "SUPER_ADMIN".equals(preset)) {
            return;
        }
        E.checkArgument(client.supportsDefaultRole(), "Permission presets require HugeGraph Server 1.8+");
        Set<String> graphSpaces =
                new java.util.HashSet<>(client.graphSpace().listGraphSpace());
        for (Map<String, String> permission :
                permissions == null ? new ArrayList<Map<String, String>>() : permissions) {
            String graphSpace = permission.get("graphspace");
            String permissionPreset = permission.get("permission_preset");
            E.checkArgument(graphSpace != null && graphSpaces.contains(graphSpace),
                            "The graphspace does not exist: %s", graphSpace);
            E.checkArgument("GS_READ_ONLY".equals(permissionPreset) ||
                            "GS_READ_WRITE".equals(permissionPreset) || "GS_ADMIN".equals(permissionPreset),
                            "Unsupported permission preset: %s",
                            permissionPreset);
        }
    }

    public void applySpacePreset(HugeClient client, String graphSpace,
                                 String userId, String preset) {
        E.checkArgument("GS_READ_ONLY".equals(preset) || "GS_READ_WRITE".equals(preset) || "GS_ADMIN".equals(preset),
                        "Unsupported permission preset: %s", preset);
        E.checkArgument(client.supportsDefaultRole(),
                        "Permission presets require HugeGraph Server 1.8+");
        User account = client.auth().getUser(userId);
        E.checkNotNull(account, "User");
        this.clearCustomRoles(client, graphSpace, userId);
        this.clearDefaultRoles(client, graphSpace, account.name());
        if (!client.auth().listSpaceMember(graphSpace)
                   .contains(account.name())) {
            client.auth().addSpaceMember(account.name(), graphSpace);
        }
        if ("GS_ADMIN".equals(preset)) {
            if (!client.auth().listSpaceAdmin(graphSpace)
                       .contains(account.name())) {
                client.auth().addSpaceAdmin(account.name(), graphSpace);
            }
            this.setDefaultRole(client, graphSpace, account.name(), "analyst");
            return;
        }
        if (client.auth().listSpaceAdmin(graphSpace).contains(account.name())) {
            client.auth().delSpaceAdmin(account.name(), graphSpace);
        }
        String role = "GS_READ_ONLY".equals(preset) ? "observer" : "analyst";
        this.setDefaultRole(client, graphSpace, account.name(), role);
    }

    public void removeSpacePreset(HugeClient client, String graphSpace,
                                  String userId) {
        this.unauthUser(client, graphSpace, userId);
    }

    public boolean hasCustomRoles(HugeClient client, String graphSpace,
                                  String userId) {
        return !this.belongService.list(client, graphSpace, null, userId).isEmpty();
    }

    public boolean hasGraphSpaceAccess(HugeClient client, String graphSpace,
                                       String username) {
        if (!client.supportsDefaultRole()) {
            return false;
        }
        if (client.graphSpace().checkDefaultRole(
                graphSpace, username, "analyst")) {
            return true;
        }
        return this.graphs(client, graphSpace).stream().anyMatch(
                graph -> client.graphSpace().checkDefaultRole(graphSpace, username, "observer", graph));
    }

    private void clearDefaultRoles(HugeClient client, String graphSpace,
                                   String username) {
        if (client.graphSpace().checkDefaultRole(
                graphSpace, username, "analyst")) {
            client.graphSpace().deleteDefaultRole(graphSpace, username, "analyst");
        }
        for (String graph : this.graphs(client, graphSpace)) {
            if (client.graphSpace().checkDefaultRole(
                    graphSpace, username, "observer", graph)) {
                client.graphSpace().deleteDefaultRole(graphSpace, username, "observer", graph);
            }
        }
    }

    private void clearCustomRoles(HugeClient client, String graphSpace,
                                  String userId) {
        this.belongService.list(client, graphSpace, null, userId)
            .forEach(belong -> this.belongService.deleteById(client, graphSpace, belong.getId()));
    }

    private void setDefaultRole(HugeClient client, String graphSpace,
                                String username, String role) {
        if ("observer".equals(role)) {
            for (String graph : this.graphs(client, graphSpace)) {
                client.graphSpace().setDefaultRole(graphSpace, username, role, graph);
            }
            return;
        }
        client.graphSpace().setDefaultRole(graphSpace, username, role);
    }

    private void addDefaultRole(HugeClient client, String graphSpace,
                                UserView user, String username, String role) {
        boolean assigned = "observer".equals(role) ? this.graphs(client, graphSpace).stream().anyMatch(
                               graph -> client.graphSpace().checkDefaultRole(graphSpace, username, role, graph)) :
                           client.graphSpace().checkDefaultRole(graphSpace, username, role);
        if (assigned) {
            user.addRole(new RoleEntity(role, role));
        }
    }

    private List<String> graphs(HugeClient client, String graphSpace) {
        client.assignGraph(graphSpace, "");
        return client.graphs().listGraph();
    }

    public void unauthUser(HugeClient client, String graphSpace,
                           String userId) {
        User account = client.auth().getUser(userId);
        E.checkNotNull(account, "User");
        List<BelongEntity> belongs = this.belongService.list(
                client, graphSpace, null, userId);
        belongs.forEach(belong -> {
            this.belongService.deleteById(client, graphSpace, belong.getId());
        });
        if (client.supportsDefaultRole()) {
            this.clearDefaultRoles(client, graphSpace, account.name());
        }
        if (client.auth().listSpaceAdmin(graphSpace).contains(account.name())) {
            client.auth().delSpaceAdmin(account.name(), graphSpace);
        }
        if (client.auth().listSpaceMember(graphSpace).contains(account.name())) {
            client.auth().delSpaceMember(account.name(), graphSpace);
        }
    }

    public IPage<User> querySpaceAdmins(HugeClient client, String graphSpace,
                                        String query, int pageNo,
                                        int pageSize) {
        List<User> spaceAdmins =
                this.getSpaceAdmins(client, graphSpace).stream()
                    .filter(user -> user.name().contains(query))
                    .sorted(Comparator.comparing(User::name))
                    .collect(Collectors.toList());
        return PageUtil.page(spaceAdmins, pageNo, pageSize);
    }

    private List<User> getSpaceAdmins(HugeClient client, String graphSpace) {
        List<String> spaceAdmins = client.auth().listSpaceAdmin(graphSpace);
        ArrayList<User> users = new ArrayList<>();
        for (String spaceAdmin : spaceAdmins) {
            User user = client.findUserByName(spaceAdmin);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }
}
