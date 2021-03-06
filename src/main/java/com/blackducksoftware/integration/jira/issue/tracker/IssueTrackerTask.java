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
package com.blackducksoftware.integration.jira.issue.tracker;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.entity.property.EntityProperty;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.blackducksoftware.integration.jira.common.exception.JiraIssueException;
import com.blackducksoftware.integration.jira.common.model.PluginBlackDuckServerConfigModel;
import com.blackducksoftware.integration.jira.data.accessor.GlobalConfigurationAccessor;
import com.blackducksoftware.integration.jira.data.accessor.JiraSettingsAccessor;
import com.blackducksoftware.integration.jira.data.accessor.PluginConfigurationAccessor;
import com.blackducksoftware.integration.jira.issue.handler.JiraIssuePropertyWrapper;
import com.blackducksoftware.integration.jira.issue.model.PluginIssueCreationConfigModel;
import com.blackducksoftware.integration.jira.web.JiraConfigDeserializer;
import com.blackducksoftware.integration.jira.web.model.BlackDuckJiraConfigSerializable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig;
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfigBuilder;
import com.synopsys.integration.blackduck.rest.BlackDuckHttpClient;
import com.synopsys.integration.blackduck.service.BlackDuckServicesFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class IssueTrackerTask implements Callable<Boolean> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Issue jiraIssue;
    private final JiraIssuePropertyWrapper issueProperyWrapper;
    private final Long eventTypeID;
    private final JiraSettingsAccessor jiraSettingsAccessor;
    private final String propertyKey;
    private final EntityProperty property;
    private final JiraConfigDeserializer configDeserializer;

    public IssueTrackerTask(final Issue jiraIssue, final JiraIssuePropertyWrapper issueProperyWrapper, final Long eventTypeID, final PluginSettings settings, final String propertyKey, final EntityProperty property) {
        this.jiraIssue = jiraIssue;
        this.issueProperyWrapper = issueProperyWrapper;
        this.eventTypeID = eventTypeID;
        this.jiraSettingsAccessor = new JiraSettingsAccessor(settings);
        this.propertyKey = propertyKey;
        this.property = property;
        this.configDeserializer = new JiraConfigDeserializer();
    }

    @Override
    public Boolean call() throws Exception {
        try {
            logger.debug(String.format("ISSUE TRACKER TASK STARTED: Event Type ID: %s, Issue: %s", eventTypeID, jiraIssue));
            final GlobalConfigurationAccessor globalConfigurationAccessor = jiraSettingsAccessor.createGlobalConfigurationAccessor();
            final PluginIssueCreationConfigModel issueCreationConfig = globalConfigurationAccessor.getIssueCreationConfig();

            final BlackDuckJiraConfigSerializable config = createJiraConfig(issueCreationConfig);
            if (!config.hasProjectMappings()) {
                logger.debug("Black Duck JIRA configuration is incomplete");
                return Boolean.FALSE;
            }

            final BlackDuckServerConfig blackDuckServerConfig =
                createBlackDuckServerConfig(jiraSettingsAccessor.createPluginConfigurationAccessor(), globalConfigurationAccessor.getBlackDuckServerConfig(), issueCreationConfig);
            if (blackDuckServerConfig == null) {
                logger.error("Black Duck Server Configuration is invalid.  Cannot update Black Duck issue tracking data.");
            } else {
                final BlackDuckServicesFactory servicesFactory = createBlackDuckServicesFactory(blackDuckServerConfig);

                final IssueTrackerHandler blackDuckIssueHandler = new IssueTrackerHandler(jiraSettingsAccessor.createPluginErrorAccessor(), servicesFactory.createBlackDuckService());
                handleIssue(eventTypeID, jiraIssue, blackDuckIssueHandler, property, propertyKey);
            }
        } catch (final Throwable throwable) {
            logger.error(String.format("Error occurred processing issue %s, caused by %s", jiraIssue, throwable));
            logger.debug(throwable.getMessage(), throwable);
            return Boolean.FALSE;
        } finally {
            logger.debug(String.format("ISSUE TRACKER TASK FINISHED: Event Type ID: %s, Issue: %s", eventTypeID, jiraIssue));
        }

        return Boolean.TRUE;
    }

    public BlackDuckServerConfig createBlackDuckServerConfig(final PluginConfigurationAccessor configurationAccessor, final PluginBlackDuckServerConfigModel blackDuckServerConfigModel,
        final PluginIssueCreationConfigModel issueCreationConfig) {
        final BlackDuckServerConfigBuilder blackDuckConfigBuilder = blackDuckServerConfigModel.createBlackDuckServerConfigBuilder();
        if (issueCreationConfig.getProjectMapping().getMappingsJson() == null) {
            logger.debug("BlackDuckNotificationCheckTask: Project Mappings not configured, therefore there is nothing to do.");
            return null;
        }

        if (issueCreationConfig.getTicketCriteria().getPolicyRulesJson() == null) {
            logger.debug("BlackDuckNotificationCheckTask: Policy Rules not configured, therefore there is nothing to do.");
            return null;
        }

        try {
            logger.debug("Building Black Duck configuration");
            final BlackDuckServerConfig blackDuckServerConfig = blackDuckConfigBuilder.build();
            logger.debug("Finished building Black Duck configuration");
            logger.debug("Last run date: " + configurationAccessor.getLastRunDate());
            logger.debug("Black Duck url: " + blackDuckServerConfig.getBlackDuckUrl().toString());
            logger.debug("Interval: " + issueCreationConfig.getGeneral().getInterval());
            return blackDuckServerConfig;
        } catch (final IllegalStateException e) {
            logger.error("Unable to connect to Black Duck. This could mean Black Duck is currently unreachable, or that the Black Duck plugin is not (yet) configured correctly: " + e.getMessage());
            return null;
        }
    }

    // FIXME we should use our Black Duck class to create the Services factory
    public BlackDuckServicesFactory createBlackDuckServicesFactory(final BlackDuckServerConfig config) {
        IntLogger intLogger = new Slf4jIntLogger(logger);
        final BlackDuckHttpClient restConnection = config.createBlackDuckHttpClient(intLogger);
        return new BlackDuckServicesFactory(new IntEnvironmentVariables(), BlackDuckServicesFactory.createDefaultGson(), BlackDuckServicesFactory.createDefaultObjectMapper(), null, restConnection, intLogger);
    }

    private BlackDuckJiraConfigSerializable createJiraConfig(final PluginIssueCreationConfigModel pluginIssueCreationConfig) {
        return configDeserializer.deserializeConfig(pluginIssueCreationConfig);
    }

    private void handleIssue(final Long eventTypeID, final Issue issue, final IssueTrackerHandler blackDuckIssueHandler, final EntityProperty property, final String propertyKey) throws IntegrationException {
        // final EntityProperty property = props.get(0);
        final IssueTrackerProperties properties = createIssueTrackerPropertiesFromJson(property.getValue());
        if (eventTypeID.equals(EventType.ISSUE_DELETED_ID)) {
            // || eventTypeID.equals(EventType.ISSUE_MOVED_ID))) { // move may be treated as delete in the future
            blackDuckIssueHandler.deleteBlackDuckIssue(properties.getBlackDuckIssueUrl(), issue);
            try {
                issueProperyWrapper.deleteIssueProperty(property.getEntityId(), jiraIssue.getCreator(), propertyKey);
            } catch (final JiraIssueException e) {
                logger.error("Problem deleting issue tracker property", e);
            }
        } else {
            blackDuckIssueHandler.updateBlackDuckIssue(properties.getBlackDuckIssueUrl(), issue);
        }
    }

    private IssueTrackerProperties createIssueTrackerPropertiesFromJson(final String json) {
        final Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, IssueTrackerProperties.class);
    }

}
