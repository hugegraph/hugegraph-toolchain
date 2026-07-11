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

package org.apache.hugegraph.controller.auth;

import com.google.common.collect.ImmutableMap;
import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.common.Response;
import org.apache.hugegraph.controller.BaseController;
import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.entity.auth.PasswordEntity;
import org.apache.hugegraph.entity.auth.UserEntity;
import org.apache.hugegraph.service.auth.UserService;
import org.apache.hugegraph.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping(Constant.API_VERSION + "auth/users")
public class UserController extends BaseController {

    @Autowired
    UserService userService;

    @GetMapping("list")
    public Object list() {
        List<UserEntity> users = this.userService.listUsers(
                this.authClient(null, null));
        return ImmutableMap.of("users", users);
    }

    @GetMapping
    public Object queryPage(@RequestParam(name = "query", required = false,
            defaultValue = "") String query,
                            @RequestParam(name = "page_no", required = false,
                                    defaultValue = "1") int pageNo,
                            @RequestParam(name = "page_size", required = false,
                                    defaultValue = "10") int pageSize) {
        return userService.queryPage(this.authClient(null, null),
                query, pageNo, pageSize);
    }

    @PostMapping
    public void create(@RequestBody UserEntity userEntity) {
        HugeClient client = this.authClient(null, null);
        userService.add(client, userEntity);
    }

    @PostMapping("batch")
    public void createbatch(@RequestParam("file") MultipartFile csvfile) {
        HugeClient client = this.authClient(null, null);
        userService.addbatch(client, csvfile);
    }

    @GetMapping("{id}")
    public Object get(@PathVariable("id") String id) {
        return userService.get(this.authClient(null, null),
                                    id);
    }

    @PutMapping("{id}")
    public void update(@PathVariable("id") String id,
                       @RequestBody UserEntity userEntity) {
        userEntity.setId(id);
        userService.update(this.authClient(null, null), userEntity);
    }

    @DeleteMapping("{id}")
    public void delete(@PathVariable("id") String id) {
        userService.delete(this.authClient(null, null), id);
    }

    @PostMapping("updatepwd")
    public Response updatepwd(@RequestBody PasswordEntity pwd) {
        HugeClient client = this.authClient(null, null);
        return userService.updatepwd(client, pwd.getUsername(), pwd.getOldpwd(), pwd.getNewpwd());
    }

    @GetMapping("listadminspace/{username}")
    public List<String> listadminspace(@PathVariable("username") String username) {
        HugeClient client = this.authClient(null, null);
        return userService.listAdminSpace(client, username);
    }

    @PostMapping("updateadminspace/{username}")
    public void updateadminspace(@PathVariable("username") String username,
                                 @RequestBody List<String> adminspaces) {
        HugeClient client = this.authClient(null, null);
        userService.updateAdminSpace(client, username, adminspaces);
    }

    @PutMapping("personal")
    public void updatePersonal(@RequestBody PersonalProfile profile) {
        String username = this.getUser();
        if (username == null) {
            throw new UnauthorizedException();
        }
        this.checkParamsNotEmpty("nickname", profile.getNickname(), true);
        String description = profile.getDescription() == null ? "" :
                             profile.getDescription();
        userService.updatePersonal(this.authClient(null, null), username,
                                   profile.getNickname(), description);
    }

    @GetMapping("getpersonal")
    public Object getpersonal() {
        return userService.getpersonal(this.authClient(null, null),
                getUser());
    }

    public static class PersonalProfile {

        private String nickname;
        private String description;

        public String getNickname() {
            return this.nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getDescription() {
            return this.description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

}
