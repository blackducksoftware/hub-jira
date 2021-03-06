/**
 * Black Duck JIRA Plugin
 *
 * Copyright (C) 2020 Synopsys, Inc.
 * https://www.synopsys.com/
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
package com.blackducksoftware.integration.jira.issue.event;

import com.atlassian.jira.entity.property.EntityProperty;
import com.atlassian.jira.issue.Issue;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.blackducksoftware.integration.jira.issue.handler.JiraIssuePropertyWrapper;
import com.blackducksoftware.integration.jira.issue.tracker.IssueTrackerTask;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig;
import com.synopsys.integration.blackduck.service.BlackDuckServicesFactory;

public class IssueTrackerTaskWithMocks extends IssueTrackerTask {
    private final BlackDuckServicesFactory blackDuckServicesFactory;

    public IssueTrackerTaskWithMocks(final Issue jiraIssue, final JiraIssuePropertyWrapper issuePropertyWrapper, final Long eventTypeID, final PluginSettings settings, final String propertyKey,
        final EntityProperty property, final BlackDuckServicesFactory blackDuckServicesFactory) {
        super(jiraIssue, issuePropertyWrapper, eventTypeID, settings, propertyKey, property);
        this.blackDuckServicesFactory = blackDuckServicesFactory;
    }

    @Override
    public BlackDuckServicesFactory createBlackDuckServicesFactory(final BlackDuckServerConfig config) {
        return blackDuckServicesFactory;
    }

}
