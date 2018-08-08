/**
 * Black Duck JIRA Plugin
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
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
package com.blackducksoftware.integration.jira.mocks.issue;

import org.apache.commons.lang3.math.NumberUtils;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.blackducksoftware.integration.hub.configuration.HubServerConfigBuilder;
import com.blackducksoftware.integration.jira.config.PluginConfigurationDetails;

public class PluginConfigurationDetailsMock extends PluginConfigurationDetails {

    public PluginConfigurationDetailsMock(final PluginSettings settings) {
        super(settings);
    }

    @Override
    public HubServerConfigBuilder createServerConfigBuilder() {
        final HubServerConfigBuilderMock configBuilder = new HubServerConfigBuilderMock();

        configBuilder.setUrl(getBlackDuckUrl());
        configBuilder.setUsername(getBlackDuckUsername());
        configBuilder.setPassword(getBlackDuckPasswordEncrypted());
        configBuilder.setPasswordLength(NumberUtils.toInt(getBlackDuckPasswordLength()));
        configBuilder.setTimeout(getBlackDuckTimeoutString());

        configBuilder.setProxyHost(getBlackDuckProxyHost());
        configBuilder.setProxyPort(getBlackDuckProxyPort());
        configBuilder.setIgnoredProxyHosts(getBlackDuckProxyNoHost());
        configBuilder.setProxyUsername(getBlackDuckProxyUser());
        configBuilder.setProxyPassword(getBlackDuckProxyPassEncrypted());
        configBuilder.setProxyPasswordLength(NumberUtils.toInt(getBlackDuckProxyPassLength()));

        return configBuilder;
    }
}
