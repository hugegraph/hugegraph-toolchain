/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hugegraph.service.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import org.apache.hugegraph.driver.AuthManager;
import org.apache.hugegraph.driver.GraphSpaceManager;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.auth.BelongEntity;
import org.apache.hugegraph.entity.auth.RoleEntity;
import org.apache.hugegraph.entity.auth.UserView;
import org.apache.hugegraph.exception.ParameterizedException;
import org.apache.hugegraph.structure.auth.User;
import org.apache.hugegraph.testutil.Assert;

public class GraphSpaceUserServiceTest {

    private HugeClient client;
    private AuthManager auth;
    private GraphSpaceManager graphSpace;
    private BelongService belongService;
    private GraphSpaceUserService service;

    @Before
    public void setup() {
        this.client = Mockito.mock(HugeClient.class);
        this.auth = Mockito.mock(AuthManager.class);
        this.graphSpace = Mockito.mock(GraphSpaceManager.class);
        this.belongService = Mockito.mock(BelongService.class);
        this.service = new GraphSpaceUserService();
        ReflectionTestUtils.setField(this.service, "belongService",
                                     this.belongService);
        Mockito.when(this.client.auth()).thenReturn(this.auth);
        Mockito.when(this.client.graphSpace()).thenReturn(this.graphSpace);
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(true);
    }

    @Test
    public void testPermissionPresetFailureHasActionableErrorKey() {
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(false);

        ParameterizedException error = null;
        try {
            this.service.validatePermissionPresets(
                    this.client, Collections.emptyList(), "GS_READ_ONLY");
        } catch (ParameterizedException e) {
            error = e;
        }

        Assert.assertNotNull(error);
        Assert.assertEquals("auth.permission-preset.unsupported",
                            error.getMessage());
    }

    @Test
    public void testListUsersMarksOnlyServerDefaultRolesAsPresets() {
        User user = user("u-1", "alice");
        Mockito.when(this.belongService.list(
                             this.client, "team", null, null))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceMember("team"))
               .thenReturn(Collections.singletonList("alice"));
        Mockito.when(this.client.findUserByName("alice")).thenReturn(user);
        Mockito.when(this.graphSpace.checkDefaultRole(
                             "team", "alice", "observer"))
               .thenReturn(true);

        UserView view = this.service.listUsers(this.client, "team").get(0);

        Assert.assertEquals(1, view.getRoles().size());
        Assert.assertEquals("observer", view.getRoles().get(0).getName());
        Assert.assertEquals("GS_READ_ONLY",
                            view.getRoles().get(0).getPermissionPreset());
    }

    @Test
    public void testLegacyAccessDoesNotCallNewDefaultRoleApi() {
        Mockito.when(this.client.supportsDefaultRole()).thenReturn(false);

        Assert.assertFalse(this.service.hasGraphSpaceAccess(
                           this.client, "team", "alice"));
        Mockito.verifyZeroInteractions(this.graphSpace);
        Mockito.verifyZeroInteractions(this.auth);
    }

    @Test
    public void testModernRolePayloadRequiresPresetApi() {
        UserView user = new UserView(
                "u-1", "alice",
                Collections.singletonList(new RoleEntity("custom", "custom")));

        ParameterizedException error = null;
        try {
            this.service.createOrUpdate(this.client, "team", user);
        } catch (ParameterizedException e) {
            error = e;
        }

        Assert.assertNotNull(error);
        Assert.assertEquals("auth.permission-preset.required",
                            error.getMessage());
        Mockito.verifyZeroInteractions(this.belongService);
        Mockito.verifyZeroInteractions(this.auth);
    }

    @Test
    public void testGetUserMergesCustomAndExplicitDefaultRoles() {
        User user = user("u-1", "alice");
        BelongEntity custom = BelongEntity.builder()
                                          .userId("u-1")
                                          .userName("alice")
                                          .roleId("custom-id")
                                          .roleName("analyst")
                                          .build();
        Mockito.when(this.belongService.list(
                             this.client, "team", null, "u-1"))
               .thenReturn(Collections.singletonList(custom));
        Mockito.when(this.auth.getUser("u-1")).thenReturn(user);
        Mockito.when(this.graphSpace.checkDefaultRole(
                             "team", "alice", "observer"))
               .thenReturn(true);

        UserView view = this.service.getUser(this.client, "team", "u-1");

        Assert.assertEquals(2, view.getRoles().size());
        RoleEntity customRole = view.getRoles().get(0);
        RoleEntity defaultRole = view.getRoles().get(1);
        Assert.assertNull(customRole.getPermissionPreset());
        Assert.assertEquals("GS_READ_ONLY",
                            defaultRole.getPermissionPreset());
    }

    @Test
    public void testApplyReadOnlyPresetAndRemoveUnrequestedSpace() {
        User user = user("u-1", "alice");
        Mockito.when(this.client.findUserByName("alice")).thenReturn(user);
        Mockito.when(this.auth.getUser("u-1")).thenReturn(user);
        Mockito.when(this.graphSpace.listGraphSpace())
               .thenReturn(java.util.Arrays.asList("team", "old"));
        Mockito.when(this.belongService.list(
                             Mockito.eq(this.client), Mockito.anyString(),
                             Mockito.isNull(), Mockito.eq("u-1")))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceMember("team"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceMember("old"))
               .thenReturn(Collections.singletonList("alice"));
        Mockito.when(this.auth.listSpaceAdmin(Mockito.anyString()))
               .thenReturn(Collections.emptyList());

        this.service.applyPermissionPresets(
                this.client, "alice",
                Collections.singletonList(permission("team", "GS_READ_ONLY")),
                "GS_READ_ONLY");

        Mockito.verify(this.auth).addSpaceMember("alice", "team");
        Mockito.verify(this.graphSpace)
               .setDefaultRole("team", "alice", "observer");
        Mockito.verify(this.auth).delSpaceMember("alice", "old");
    }

    @Test
    public void testApplyAdminPresetAddsManagementAndWriteAccess() {
        User user = user("u-1", "alice");
        Mockito.when(this.auth.getUser("u-1")).thenReturn(user);
        Mockito.when(this.belongService.list(
                             this.client, "team", null, "u-1"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceMember("team"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceAdmin("team"))
               .thenReturn(Collections.emptyList());

        this.service.applySpacePreset(this.client, "team", "u-1",
                                      "GS_ADMIN");

        Mockito.verify(this.auth).addSpaceMember("alice", "team");
        Mockito.verify(this.auth).addSpaceAdmin("alice", "team");
        Mockito.verify(this.graphSpace)
               .setDefaultRole("team", "alice", "analyst");
    }

    @Test
    public void testApplyReadWritePresetRemovesAdminAccess() {
        User user = user("u-1", "alice");
        Mockito.when(this.auth.getUser("u-1")).thenReturn(user);
        Mockito.when(this.belongService.list(
                             this.client, "team", null, "u-1"))
               .thenReturn(Collections.emptyList());
        Mockito.when(this.auth.listSpaceMember("team"))
               .thenReturn(Collections.singletonList("alice"));
        Mockito.when(this.auth.listSpaceAdmin("team"))
               .thenReturn(Collections.singletonList("alice"));

        this.service.applySpacePreset(this.client, "team", "u-1",
                                      "GS_READ_WRITE");

        Mockito.verify(this.auth).delSpaceAdmin("alice", "team");
        Mockito.verify(this.graphSpace)
               .setDefaultRole("team", "alice", "analyst");
        Mockito.verify(this.auth, Mockito.never())
               .addSpaceMember(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testRemovePresetCleansEveryGrantType() {
        User user = user("u-1", "alice");
        BelongEntity belong = BelongEntity.builder()
                                          .id("belong-1")
                                          .userId("u-1")
                                          .build();
        Mockito.when(this.auth.getUser("u-1")).thenReturn(user);
        Mockito.when(this.belongService.list(
                             this.client, "team", null, "u-1"))
               .thenReturn(Collections.singletonList(belong));
        Mockito.when(this.graphSpace.checkDefaultRole(
                             "team", "alice", "analyst"))
               .thenReturn(true);
        Mockito.when(this.graphSpace.checkDefaultRole(
                             "team", "alice", "observer"))
               .thenReturn(true);
        Mockito.when(this.auth.listSpaceAdmin("team"))
               .thenReturn(Collections.singletonList("alice"));
        Mockito.when(this.auth.listSpaceMember("team"))
               .thenReturn(Collections.singletonList("alice"));

        this.service.removeSpacePreset(this.client, "team", "u-1");

        Mockito.verify(this.belongService)
               .deleteById(this.client, "team", "belong-1");
        Mockito.verify(this.graphSpace)
               .deleteDefaultRole("team", "alice", "analyst");
        Mockito.verify(this.graphSpace)
               .deleteDefaultRole("team", "alice", "observer");
        Mockito.verify(this.auth).delSpaceAdmin("alice", "team");
        Mockito.verify(this.auth).delSpaceMember("alice", "team");
    }

    private static User user(String id, String name) {
        User user = new User();
        user.setId(id);
        user.name(name);
        return user;
    }

    private static Map<String, String> permission(String graphSpace,
                                                   String preset) {
        Map<String, String> permission = new HashMap<>();
        permission.put("graphspace", graphSpace);
        permission.put("permission_preset", preset);
        return permission;
    }
}
