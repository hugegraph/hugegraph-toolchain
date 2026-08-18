/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.unit;

import java.util.Arrays;
import java.util.Collections;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.driver.AuthManager;
import org.apache.hugegraph.driver.GraphSpaceManager;
import org.apache.hugegraph.driver.GraphsManager;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.auth.UserEntity;
import org.apache.hugegraph.exception.ServerException;
import org.apache.hugegraph.options.HubbleOptions;
import org.apache.hugegraph.service.auth.GraphSpaceUserService;
import org.apache.hugegraph.service.auth.UserService;
import org.apache.hugegraph.structure.auth.User;

public class UserServiceCompatibilityTest {

    private HugeConfig config;
    private HugeClient client;
    private AuthManager auth;
    private GraphSpaceManager graphSpace;
    private GraphsManager graphs;
    private GraphSpaceUserService graphSpaceUsers;
    private UserService service;

    @Before
    public void setup() {
        this.config = Mockito.mock(HugeConfig.class);
        this.client = Mockito.mock(HugeClient.class);
        this.auth = Mockito.mock(AuthManager.class);
        this.graphSpace = Mockito.mock(GraphSpaceManager.class);
        this.graphs = Mockito.mock(GraphsManager.class);
        this.graphSpaceUsers = Mockito.mock(GraphSpaceUserService.class);
        Mockito.when(this.client.auth()).thenReturn(this.auth);
        Mockito.when(this.client.graphSpace()).thenReturn(this.graphSpace);
        Mockito.when(this.client.graphs()).thenReturn(this.graphs);
        Mockito.when(this.auth.createUser(Mockito.any(User.class)))
               .thenReturn(new User());
        Mockito.when(this.auth.listSuperAdmin())
               .thenReturn(java.util.Collections.emptyList());
        this.service = new UserService();
        ReflectionTestUtils.setField(this.service, "config", this.config);
        ReflectionTestUtils.setField(this.service, "graphSpaceUserService",
                                     this.graphSpaceUsers);
    }

    @Test
    public void testStandaloneUserCreationOmitsPdOnlyNickname() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(false);

        this.service.add(this.client, userEntity("display-name"));

        ArgumentCaptor<User> request = ArgumentCaptor.forClass(User.class);
        Mockito.verify(this.auth).createUser(request.capture());
        Assert.assertNull(request.getValue().nickname());
    }

    @Test
    public void testPdUserCreationKeepsNickname() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);

        this.service.add(this.client, userEntity("display-name"));

        ArgumentCaptor<User> request = ArgumentCaptor.forClass(User.class);
        Mockito.verify(this.auth).createUser(request.capture());
        Assert.assertEquals("display-name", request.getValue().nickname());
    }

    @Test
    public void testStandaloneAccountLevelsMatchServerRoles() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(false);
        Mockito.when(this.auth.listUsers())
               .thenReturn(Arrays.asList(user("admin"), user("hubbleuser")));

        @SuppressWarnings("unchecked")
        IPage<UserEntity> result = (IPage<UserEntity>)
                this.service.queryPage(this.client, "", 1, 10);

        Assert.assertTrue(result.getRecords().get(0).isSuperadmin());
        Assert.assertFalse(result.getRecords().get(1).isSuperadmin());
    }

    @Test
    public void testStandaloneUserUpdateOmitsPdOnlyNickname() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(false);
        UserEntity user = UserEntity.builder()
                                    .id("user-id")
                                    .name("user")
                                    .nickname("display-name")
                                    .build();

        this.service.update(this.client, user);

        ArgumentCaptor<User> request = ArgumentCaptor.forClass(User.class);
        Mockito.verify(this.auth).updateUser(request.capture());
        Assert.assertNull(request.getValue().nickname());
    }

    @Test
    public void testStandalonePersonalUpdateOmitsPdOnlyNickname() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(false);
        Mockito.when(this.auth.getUserByName("user"))
               .thenReturn(user("user"));
        Mockito.when(this.client.findUserByName("user")).thenReturn(user("user"));

        this.service.updatePersonal(this.client, "user", "display-name",
                                    "description");

        ArgumentCaptor<User> request = ArgumentCaptor.forClass(User.class);
        Mockito.verify(this.auth).updateUser(request.capture());
        Assert.assertNull(request.getValue().nickname());
        Assert.assertEquals("description", request.getValue().description());
    }

    @Test
    public void testCurrentUserPresetUsesSelfPermissionApi() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(true);
        Mockito.when(this.client.findUserByName("user"))
               .thenReturn(user("user"));
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(java.util.Collections.singletonList("SPACE"));
        Mockito.when(this.auth.checkDefaultRole("SPACE", "analyst"))
               .thenReturn(true);

        UserEntity result = this.service.getpersonal(this.client, "user");

        Assert.assertEquals("GS_READ_WRITE",
                            result.getGraphspacePermissions().get(0)
                                  .get("permission_preset"));
        Mockito.verify(this.graphSpace, Mockito.never())
               .checkDefaultRole(Mockito.anyString(), Mockito.anyString(),
                                 Mockito.anyString());
    }

    @Test
    public void testUserDetailFindsAdminSpacesByUsername() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        User account = user("alice");
        account.setId("user-id");
        Mockito.when(this.auth.getUser("user-id")).thenReturn(account);
        Mockito.when(this.auth.listUsers())
               .thenReturn(Collections.singletonList(account));
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(Collections.singletonList("SPACE"));
        Mockito.when(this.auth.listSpaceAdmin("SPACE"))
               .thenReturn(Collections.singletonList("alice"));

        UserEntity result = this.service.get(this.client, "user-id");

        Assert.assertEquals(Collections.singletonList("SPACE"),
                            result.getAdminSpaces());
        Assert.assertEquals(Integer.valueOf(1), result.getSpacenum());
    }

    @Test
    public void testCurrentUserIgnoresOnlyForbiddenGraphSpaces() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(true);
        Mockito.when(this.client.findUserByName("user"))
               .thenReturn(user("user"));
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(Arrays.asList("OWNED", "DENIED"));
        Mockito.when(this.auth.checkDefaultRole("OWNED", "analyst"))
               .thenReturn(true);
        ServerException forbidden = new ServerException("forbidden");
        forbidden.status(403);
        Mockito.when(this.graphs.listGraph()).thenThrow(forbidden);

        UserEntity result = this.service.getpersonal(this.client, "user");

        Assert.assertEquals(Collections.singletonList("OWNED"),
                            result.getResSpaces());
        Assert.assertEquals(1, result.getGraphspacePermissions().size());
    }

    @Test
    public void testLegacyAnalystKeepsGraphSpaceAccess() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(false);
        Mockito.when(this.client.findUserByName("user"))
               .thenReturn(user("user"));
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(Collections.singletonList("SPACE"));
        Mockito.when(this.auth.checkDefaultRole("SPACE", "analyst"))
               .thenReturn(true);

        UserEntity result = this.service.getpersonal(this.client, "user");

        Assert.assertEquals(Collections.singletonList("SPACE"),
                            result.getResSpaces());
        Assert.assertEquals("LEGACY_CUSTOM", result.getPermissionPreset());
        Mockito.verify(this.auth, Mockito.never())
               .checkDefaultRole("SPACE", "observer");
    }

    @Test
    public void testLegacyProfileUpdatePreservesPermissionAssignments() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(false);
        UserEntity user = UserEntity.builder()
                                    .id("user-id")
                                    .name("user")
                                    .nickname("display-name")
                                    .build();

        this.service.update(this.client, user);

        Mockito.verify(this.graphSpaceUsers, Mockito.never())
               .validatePermissionPresets(Mockito.any(), Mockito.any(),
                                          Mockito.any());
        Mockito.verify(this.graphSpaceUsers, Mockito.never())
               .applyPermissionPresets(Mockito.any(), Mockito.anyString(),
                                       Mockito.any(), Mockito.any());
        Mockito.verify(this.auth).updateUser(Mockito.any(User.class));
    }

    @Test
    public void testModernProfileUpdatePreservesPermissionAssignments() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(true);
        GraphSpaceUserService actualGraphSpaceUsers =
                new GraphSpaceUserService();
        ReflectionTestUtils.setField(this.service, "graphSpaceUserService",
                                     actualGraphSpaceUsers);
        UserEntity user = UserEntity.builder()
                                    .id("user-id")
                                    .name("user")
                                    .nickname("display-name")
                                    .build();

        this.service.update(this.client, user);

        Mockito.verify(this.auth).updateUser(Mockito.any(User.class));
        Mockito.verify(this.graphSpace, Mockito.never()).setDefaultRole(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(this.graphSpace, Mockito.never()).deleteDefaultRole(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(this.auth, Mockito.never()).addSpaceAdmin(
                Mockito.anyString(), Mockito.anyString());
        Mockito.verify(this.auth, Mockito.never()).delSpaceAdmin(
                Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testModernUserUpdateReconcilesAdminSpaces() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(true);
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(Arrays.asList("OLD", "NEW"));
        Mockito.when(this.auth.listSpaceAdmin("OLD"))
               .thenReturn(Collections.singletonList("user"));
        Mockito.when(this.auth.listSpaceAdmin("NEW"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.client.findUserByName("user"))
               .thenReturn(user("user"));
        UserEntity account = UserEntity.builder()
                                       .id("user")
                                       .name("user")
                                       .adminSpaces(
                                               Collections.singletonList("NEW"))
                                       .build();
        account.setSuperadmin(true);

        this.service.update(this.client, account);

        Mockito.verify(this.graphSpaceUsers).applySpacePreset(
                this.client, "NEW", "user", "GS_ADMIN");
        Mockito.verify(this.graphSpaceUsers).removeSpacePreset(
                this.client, "OLD", "user");
        Mockito.verify(this.auth).addSuperAdmin("user");
    }

    @Test
    public void testLegacyUserUpdateReconcilesAdminSpaces() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(false);
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(Arrays.asList("OLD", "NEW"));
        Mockito.when(this.auth.listSpaceAdmin("OLD"))
               .thenReturn(Collections.singletonList("user"));
        Mockito.when(this.auth.listSpaceAdmin("NEW"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.client.findUserByName("user"))
               .thenReturn(user("user"));
        Mockito.when(this.auth.listSuperAdmin())
               .thenReturn(Collections.singletonList("user"));
        UserEntity account = UserEntity.builder()
                                       .id("user")
                                       .name("user")
                                       .adminSpaces(
                                               Collections.singletonList("NEW"))
                                       .build();

        this.service.update(this.client, account);

        Mockito.verify(this.auth).addSpaceAdmin("user", "NEW");
        Mockito.verify(this.auth).delSpaceAdmin("user", "OLD");
        Mockito.verify(this.auth).delSuperAdmin("user");
        Mockito.verify(this.graphSpaceUsers, Mockito.never())
               .applySpacePreset(Mockito.any(), Mockito.anyString(),
                                 Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testPdUserListReportsPresetAndCustomRoleState() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(true);
        Mockito.when(this.auth.listUsers())
               .thenReturn(Collections.singletonList(user("alice")));
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(Arrays.asList("ADMIN", "WRITE", "READ", "CUSTOM"));
        Mockito.when(this.auth.listSpaceAdmin("ADMIN"))
               .thenReturn(Collections.singletonList("alice"));
        Mockito.when(this.auth.listSpaceAdmin("WRITE"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceAdmin("READ"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceAdmin("CUSTOM"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.graphSpace.checkDefaultRole(
                "WRITE", "alice", "analyst")).thenReturn(true);
        Mockito.when(this.graphSpace.checkDefaultRole(
                "READ", "alice", "observer")).thenReturn(true);
        Mockito.when(this.graphSpaceUsers.hasCustomRoles(
                this.client, "CUSTOM", "alice")).thenReturn(true);

        UserEntity account = this.service.listUsers(this.client).get(0);

        Assert.assertEquals(Integer.valueOf(1), account.getSpacenum());
        Assert.assertEquals(Collections.singletonList("ADMIN"),
                            account.getAdminSpaces());
        Assert.assertEquals(3, account.getGraphspacePermissions().size());
        Assert.assertEquals("GS_ADMIN",
                            account.getGraphspacePermissions().get(0)
                                   .get("permission_preset"));
        Assert.assertEquals("GS_READ_WRITE",
                            account.getGraphspacePermissions().get(1)
                                   .get("permission_preset"));
        Assert.assertEquals("GS_READ_ONLY",
                            account.getGraphspacePermissions().get(2)
                                   .get("permission_preset"));
        Assert.assertEquals("LEGACY_CUSTOM", account.getPermissionPreset());
    }

    @Test
    public void testLegacyUserCreationSkipsPermissionPresetApis() {
        Mockito.when(this.config.get(HubbleOptions.PD_ENABLED)).thenReturn(true);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(false);
        UserEntity user = userEntity("display-name");
        user.setPermissionPreset("GS_READ_ONLY");
        user.setGraphspacePermissions(java.util.Collections.singletonList(
                java.util.Collections.singletonMap(
                        "permission_preset", "GS_READ_ONLY")));

        this.service.add(this.client, user);

        Mockito.verify(this.auth).createUser(Mockito.any(User.class));
        Mockito.verify(this.graphSpaceUsers, Mockito.never())
               .validatePermissionPresets(Mockito.any(), Mockito.any(),
                                          Mockito.any());
        Mockito.verify(this.graphSpaceUsers, Mockito.never())
               .applyPermissionPresets(Mockito.any(), Mockito.anyString(),
                                       Mockito.any(), Mockito.any());
    }

    private static UserEntity userEntity(String nickname) {
        return UserEntity.builder()
                         .name("user")
                         .nickname(nickname)
                         .password("password")
                         .build();
    }

    private static User user(String name) {
        User user = new User();
        user.setId(name);
        user.name(name);
        return user;
    }
}
