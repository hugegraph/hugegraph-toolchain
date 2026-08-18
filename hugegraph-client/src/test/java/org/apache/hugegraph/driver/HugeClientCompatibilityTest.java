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

package org.apache.hugegraph.driver;

import java.util.Arrays;

import org.apache.hugegraph.structure.auth.TokenPayload;
import org.apache.hugegraph.structure.auth.User;
import org.apache.hugegraph.testutil.Whitebox;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class HugeClientCompatibilityTest {

    private HugeClient client;
    private AuthManager auth;

    @Before
    public void setup() {
        this.client = Mockito.mock(HugeClient.class,
                                   Mockito.CALLS_REAL_METHODS);
        this.auth = Mockito.mock(AuthManager.class);
        Whitebox.setInternalState(this.client, "auth", this.auth);
    }

    @Test
    public void shouldUseDirectLookupForModernServers() {
        User alice = user("alice");
        Whitebox.setInternalState(
                this.client, "compatibility",
                ServerCompatibility.Profile.MODERN);
        Mockito.when(this.auth.getUserByName("alice")).thenReturn(alice);

        Assert.assertTrue(this.client.supportsDefaultRole());
        Assert.assertSame(alice, this.client.findUserByName("alice"));
        Mockito.verify(this.auth, Mockito.never()).listUsers();
    }

    @Test
    public void shouldSearchLegacyUserListsByName() {
        User alice = user("alice");
        User bob = user("bob");
        Whitebox.setInternalState(
                this.client, "compatibility",
                ServerCompatibility.Profile.GRAPHSPACE);
        Mockito.when(this.auth.listUsers())
               .thenReturn(Arrays.asList(bob, alice));

        Assert.assertFalse(this.client.supportsDefaultRole());
        Assert.assertSame(alice, this.client.findUserByName("alice"));
        Assert.assertNull(this.client.findUserByName("missing"));
        Mockito.verify(this.auth, Mockito.never())
               .getUserByName(Mockito.anyString());
    }

    @Test
    public void shouldFindCurrentUserFromVerifiedTokenIdentity() {
        TokenPayload payload = Mockito.mock(TokenPayload.class);
        User alice = user("alice");
        Mockito.when(payload.userId()).thenReturn("user-id");
        Mockito.when(payload.username()).thenReturn("alice");
        Mockito.when(this.auth.verifyToken()).thenReturn(payload);
        Mockito.when(this.auth.getUser("user-id")).thenReturn(alice);

        Assert.assertSame(alice, this.client.findCurrentUser("alice"));
        Mockito.verify(this.auth, Mockito.never()).listUsers();
        Mockito.verify(this.auth, Mockito.never())
               .getUserByName(Mockito.anyString());
    }

    @Test
    public void shouldRejectMismatchedCurrentUserIdentity() {
        TokenPayload payload = Mockito.mock(TokenPayload.class);
        Mockito.when(payload.userId()).thenReturn("user-id");
        Mockito.when(payload.username()).thenReturn("bob");
        Mockito.when(this.auth.verifyToken()).thenReturn(payload);

        try {
            this.client.findCurrentUser("alice");
            Assert.fail("Expected a mismatched current-user identity");
        } catch (IllegalStateException ignored) {
            // Expected
        }
        Mockito.verify(this.auth, Mockito.never()).getUser(Mockito.any());
    }

    private static User user(String name) {
        User user = new User();
        user.name(name);
        return user;
    }
}
