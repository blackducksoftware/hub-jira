/**
 * Black Duck JIRA Plugin
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.jira.mocks;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.user.ApplicationUser;

public class ApplicationUserMock implements ApplicationUser {
    private String username;
    private String name;
    private String key;

    @Override
    public long getDirectoryId() {
        return 0;
    }

    @Override
    public User getDirectoryUser() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getEmailAddress() {
        return null;
    }

    @Override
    public String getKey() {
        return key != null ? key : name;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public Long getId() {
        return 123L;
    }

}
