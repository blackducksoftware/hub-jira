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
package com.blackducksoftware.integration.jira.task;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.atlassian.jira.cluster.ClusterManager;
import com.atlassian.jira.util.BuildUtilsInfoImpl;
import com.blackducksoftware.integration.jira.JiraDeploymentType;
import com.blackducksoftware.integration.jira.common.BlackDuckJiraLogger;
import com.blackducksoftware.integration.jira.common.BlackDuckPluginDateFormatter;
import com.blackducksoftware.integration.jira.common.BlackDuckProjectMappings;
import com.blackducksoftware.integration.jira.common.JiraUserContext;
import com.blackducksoftware.integration.jira.common.TicketInfoFromSetup;
import com.blackducksoftware.integration.jira.common.model.BlackDuckProjectMapping;
import com.blackducksoftware.integration.jira.common.model.PolicyRuleSerializable;
import com.blackducksoftware.integration.jira.config.JiraServices;
import com.blackducksoftware.integration.jira.config.JiraSettingsService;
import com.blackducksoftware.integration.jira.config.PluginConfigurationDetails;
import com.blackducksoftware.integration.jira.config.model.BlackDuckJiraConfigSerializable;
import com.blackducksoftware.integration.jira.config.model.BlackDuckJiraFieldCopyConfigSerializable;
import com.blackducksoftware.integration.jira.task.conversion.TicketGenerator;
import com.google.gson.Gson;
import com.synopsys.integration.blackduck.api.generated.discovery.ApiDiscovery;
import com.synopsys.integration.blackduck.api.generated.view.NotificationView;
import com.synopsys.integration.blackduck.api.generated.view.UserView;
import com.synopsys.integration.blackduck.configuration.HubServerConfig;
import com.synopsys.integration.blackduck.configuration.HubServerConfigBuilder;
import com.synopsys.integration.blackduck.notification.CommonNotificationView;
import com.synopsys.integration.blackduck.notification.CommonNotificationViewResults;
import com.synopsys.integration.blackduck.notification.content.detail.NotificationContentDetailFactory;
import com.synopsys.integration.blackduck.rest.BlackduckRestConnection;
import com.synopsys.integration.blackduck.service.CommonNotificationService;
import com.synopsys.integration.blackduck.service.HubService;
import com.synopsys.integration.blackduck.service.HubServicesFactory;
import com.synopsys.integration.blackduck.service.NotificationService;
import com.synopsys.integration.blackduck.service.model.BlackDuckPhoneHomeCallable;
import com.synopsys.integration.exception.EncryptionException;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.phonehome.PhoneHomeCallable;
import com.synopsys.integration.phonehome.PhoneHomeService;
import com.synopsys.integration.rest.connection.RestConnection;

public class BlackDuckJiraTask {
    private static final Long NOTIFICATIONS_LOOKBACK_DAYS = 14L;
    private static final Long START_DATE_LOOKBACK_INTERVAL_FACTOR = 1L;

    private final BlackDuckJiraLogger logger = new BlackDuckJiraLogger(Logger.getLogger(this.getClass().getName()));

    private final PluginConfigurationDetails pluginConfigDetails;
    private final JiraUserContext jiraContext;
    private final Date runDate;
    private final JiraServices jiraServices = new JiraServices();
    private final JiraSettingsService jiraSettingsService;
    private final TicketInfoFromSetup ticketInfoFromSetup;
    private final Integer configuredTaskInterval;
    private final String fieldCopyMappingJson;

    private final Gson gson = HubServicesFactory.createDefaultGson();

    public BlackDuckJiraTask(final PluginConfigurationDetails configDetails, final JiraUserContext jiraContext, final JiraSettingsService jiraSettingsService, final TicketInfoFromSetup ticketInfoFromSetup,
            final Integer configuredTaskInterval) {
        this.pluginConfigDetails = configDetails;
        this.jiraContext = jiraContext;

        this.runDate = new Date();
        logger.info("Install date: " + configDetails.getInstallDateString());
        logger.info("Last run date: " + configDetails.getLastRunDateString());

        this.jiraSettingsService = jiraSettingsService;
        this.ticketInfoFromSetup = ticketInfoFromSetup;
        this.configuredTaskInterval = configuredTaskInterval;
        this.fieldCopyMappingJson = configDetails.getFieldCopyMappingJson();

        logger.debug("createVulnerabilityIssues: " + configDetails.isCreateVulnerabilityIssues());
    }

    /**
     * Setup, then generate JIRA tickets based on recent notifications
     * @return this execution's run date/time string on success, or previous start date/time on failure
     */
    public String execute(final String previousStartDate) {
        logger.debug("Previous start date: " + previousStartDate);
        final HubServerConfigBuilder blackDuckServerConfigBuilder = pluginConfigDetails.createServerConfigBuilder();
        final HubServerConfig blackDuckServerConfig;
        try {
            logger.debug("Building Black Duck configuration");
            blackDuckServerConfig = blackDuckServerConfigBuilder.build();
            logger.debug("Finished building Black Duck configuration for " + blackDuckServerConfig.getHubUrl());
        } catch (final IllegalStateException e) {
            logger.error("Unable to connect to Black Duck. This could mean Black Duck is currently unreachable, or that the Black Duck JIRA plugin is not (yet) configured correctly: " + e.getMessage());
            return previousStartDate;
        }

        final BlackDuckJiraConfigSerializable config = deSerializeConfig();
        if (config == null) {
            return previousStartDate;
        }
        final BlackDuckJiraFieldCopyConfigSerializable fieldCopyConfig = deSerializeFieldCopyConfig();

        final HubServicesFactory blackDuckServicesFactory;
        try {
            blackDuckServicesFactory = createBlackDuckServicesFactory(blackDuckServerConfig);
        } catch (final EncryptionException e) {
            logger.warn("Error handling password: " + e.getMessage());
            return previousStartDate;
        }

        final Date startDate;
        try {
            logger.debug("Determining what to use as the start date...");
            startDate = deriveStartDate(blackDuckServicesFactory, previousStartDate);
            logger.debug("Derived start date: " + startDate);
        } catch (final ParseException parseException) {
            logger.info("This is the first run, but the plugin install date cannot be parsed; Not doing anything this time, will record collection start time and start collecting notifications next time");
            return getRunDateString();
        } catch (final IntegrationException integrationException) {
            logger.error("Could not determine the last notification date from Black Duck. Please ensure that a connection can be established.");
            return previousStartDate;
        }

        try {
            final boolean getOldestNotificationsFirst = true;
            final TicketGenerator ticketGenerator = initTicketGenerator(jiraContext, blackDuckServicesFactory, getOldestNotificationsFirst, ticketInfoFromSetup, getRuleUrls(config), fieldCopyConfig);

            // Phone-Home
            final LocalDate lastPhoneHome = jiraSettingsService.getLastPhoneHome();
            if (LocalDate.now().isAfter(lastPhoneHome)) {
                final PhoneHomeCallable phCallable = blackDuckServicesFactory.createBlackDuckPhoneHomeCallable(blackDuckServicesFactory.createHubService().getHubBaseUrl(), "blackduck-jira", jiraServices.getPluginVersion());
                bdPhoneHome((BlackDuckPhoneHomeCallable) phCallable);
            }

            final BlackDuckProjectMappings blackDuckProjectMappings = new BlackDuckProjectMappings(jiraServices, config.getHubProjectMappings());

            logger.debug("Attempting to get the Black Duck user...");
            final UserView blackDuckUserItem = getBlackDuckUser(blackDuckServicesFactory.createHubService());
            if (blackDuckUserItem == null) {
                logger.warn("Will not request notifications from Black Duck because of an invalid user configuration");
                return previousStartDate;
            }
            // Generate JIRA Issues based on recent notifications
            logger.info("Getting Black Duck notifications from " + startDate + " to " + runDate);
            final Date lastNotificationDate = ticketGenerator.generateTicketsForNotificationsInDateRange(blackDuckUserItem, blackDuckProjectMappings, startDate, runDate);
            logger.debug("Finished running ticket generator. Last notification date: " + BlackDuckPluginDateFormatter.format(lastNotificationDate));
            final Date nextRunDate = new Date(lastNotificationDate.getTime() + 1l);
            return BlackDuckPluginDateFormatter.format(nextRunDate);
        } catch (final Exception e) {
            logger.error("Error processing Black Duck notifications or generating JIRA issues: " + e.getMessage(), e);
            jiraSettingsService.addBlackDuckError(e, "executeBlackDuckJiraTask");
            return previousStartDate;
        } finally {
            if (blackDuckServicesFactory != null) {
                closeRestConnection(blackDuckServicesFactory.getRestConnection());
            }
        }
    }

    public String getRunDateString() {
        return BlackDuckPluginDateFormatter.format(runDate);
    }

    private UserView getBlackDuckUser(final HubService blackDuckService) {
        try {
            return blackDuckService.getResponse(ApiDiscovery.CURRENT_USER_LINK_RESPONSE);
        } catch (final IntegrationException e) {
            final String message = "Could not get the logged in user for Black Duck.";
            logger.error(message, e);
            jiraSettingsService.addBlackDuckError(message, "getCurrentUser");
        }
        return null;
    }

    private HubServicesFactory createBlackDuckServicesFactory(final HubServerConfig blackDuckServerConfig) throws EncryptionException {
        final BlackduckRestConnection restConnection = blackDuckServerConfig.createRestConnection(logger);
        final HubServicesFactory blackDuckServicesFactory = new HubServicesFactory(gson, HubServicesFactory.createDefaultJsonParser(), restConnection, logger);
        return blackDuckServicesFactory;
    }

    void closeRestConnection(final RestConnection restConnection) {
        try {
            restConnection.close();
        } catch (final IOException e) {
            logger.error("There was a problem trying to close the connection to the Black Duck server.", e);
        }
    }

    private List<String> getRuleUrls(final BlackDuckJiraConfigSerializable config) {
        final List<String> ruleUrls = new ArrayList<>();
        final List<PolicyRuleSerializable> rules = config.getPolicyRules();
        for (final PolicyRuleSerializable rule : rules) {
            final String ruleUrl = rule.getPolicyUrl();
            logger.debug("getRuleUrls(): rule name: " + rule.getName() + "; ruleUrl: " + ruleUrl + "; checked: " + rule.getChecked());
            if ((rule.getChecked()) && (!ruleUrl.equals("undefined"))) {
                ruleUrls.add(ruleUrl);
            }
        }
        return ruleUrls;
    }

    private TicketGenerator initTicketGenerator(final JiraUserContext jiraUserContext, final HubServicesFactory hubServicesFactory, final boolean notificationsOldestFirst,
            final TicketInfoFromSetup ticketInfoFromSetup, final List<String> linksOfRulesToMonitor, final BlackDuckJiraFieldCopyConfigSerializable fieldCopyConfig) throws URISyntaxException {
        logger.debug("JIRA user: " + this.jiraContext.getJiraAdminUser().getName());

        final CommonNotificationService commonNotificationService = createCommonNotificationService(hubServicesFactory, notificationsOldestFirst);
        return new TicketGenerator(hubServicesFactory.createHubService(), hubServicesFactory.createHubBucketService(), hubServicesFactory.createNotificationService(), commonNotificationService,
                hubServicesFactory.createIssueService(), jiraServices, jiraUserContext, jiraSettingsService, ticketInfoFromSetup.getCustomFields(), pluginConfigDetails.isCreateVulnerabilityIssues(), linksOfRulesToMonitor, fieldCopyConfig);
    }

    private CommonNotificationService createCommonNotificationService(final HubServicesFactory blackDuckServicesFactory, final boolean notificationsOldestFirst) {
        final NotificationContentDetailFactory contentDetailFactory = new NotificationContentDetailFactory(blackDuckServicesFactory.getGson(), HubServicesFactory.createDefaultJsonParser());
        return blackDuckServicesFactory.createCommonNotificationService(contentDetailFactory, notificationsOldestFirst);
    }

    private BlackDuckJiraConfigSerializable deSerializeConfig() {
        if (pluginConfigDetails.getProjectMappingJson() == null) {
            logger.debug("BlackDuckNotificationCheckTask: Project Mappings not configured, therefore there is nothing to do.");
            return null;
        }

        if (pluginConfigDetails.getPolicyRulesJson() == null) {
            logger.debug("BlackDuckNotificationCheckTask: Policy Rules not configured, therefore there is nothing to do.");
            return null;
        }

        final BlackDuckJiraConfigSerializable config = new BlackDuckJiraConfigSerializable();
        config.setHubProjectMappingsJson(pluginConfigDetails.getProjectMappingJson());
        config.setPolicyRulesJson(pluginConfigDetails.getPolicyRulesJson());
        logger.debug("Mappings:");
        for (final BlackDuckProjectMapping mapping : config.getHubProjectMappings()) {
            logger.debug(mapping.toString());
        }
        logger.debug("Policy Rules:");
        for (final PolicyRuleSerializable rule : config.getPolicyRules()) {
            logger.debug(rule.toString());
        }
        return config;
    }

    private BlackDuckJiraFieldCopyConfigSerializable deSerializeFieldCopyConfig() {
        final BlackDuckJiraFieldCopyConfigSerializable fieldCopyConfig = new BlackDuckJiraFieldCopyConfigSerializable();
        fieldCopyConfig.setJson(fieldCopyMappingJson);
        return fieldCopyConfig;
    }

    private Date deriveStartDate(final HubServicesFactory blackDuckServicesFactory, final String lastRunDateString) throws ParseException, IntegrationException {
        if (lastRunDateString == null) {
            logger.info("No lastRunDate set, using the last notification date from Black Duck to determine the start date");
            return getLastNotificationDateFromBlackDuck(blackDuckServicesFactory);
        }
        return BlackDuckPluginDateFormatter.parse(lastRunDateString);
    }

    private Date getLastNotificationDateFromBlackDuck(final HubServicesFactory blackDuckServicesFactory) throws IntegrationException {
        try {
            final Date endDate = new Date();
            final Date lookbackDate = BlackDuckPluginDateFormatter
                                              .fromLocalDateTime(LocalDateTime.now().minusDays(NOTIFICATIONS_LOOKBACK_DAYS));
            logger.debug("Checking the last " + NOTIFICATIONS_LOOKBACK_DAYS + " days for notifications. From " + lookbackDate + " to " + endDate);
            final NotificationService notificationService = blackDuckServicesFactory.createNotificationService();
            final List<NotificationView> notificationViews = notificationService.getAllNotifications(lookbackDate, endDate);
            final CommonNotificationService commonNotificationService = createCommonNotificationService(blackDuckServicesFactory, false);
            final List<CommonNotificationView> commonNotifications = commonNotificationService.getCommonNotifications(notificationViews);
            final CommonNotificationViewResults commonNotificationViewResults = commonNotificationService.getCommonNotificationViewResults(commonNotifications);
            final Date lastNotificationDate = commonNotificationViewResults
                                                      .getLatestNotificationCreatedAtDate()
                                                      .orElseThrow(() -> new IntegrationException("Unable to get the latest notification date from Black Duck"));
            final LocalDateTime lastNotificationLocalDateTime = BlackDuckPluginDateFormatter.toLocalDateTime(lastNotificationDate);

            final Long lookbackTime = configuredTaskInterval.longValue() * START_DATE_LOOKBACK_INTERVAL_FACTOR;
            return BlackDuckPluginDateFormatter.fromLocalDateTime(lastNotificationLocalDateTime.minusMinutes(lookbackTime));
        } catch (final Exception e) {
            throw new IntegrationException(e);
        }
    }

    public void bdPhoneHome(final BlackDuckPhoneHomeCallable phCallable) {
        try {
            final ClusterManager clusterManager = jiraServices.getClusterManager();
            final JiraDeploymentType deploymentType;
            if (clusterManager.isClusterLicensed()) {
                deploymentType = JiraDeploymentType.DATA_CENTER;
            } else {
                deploymentType = JiraDeploymentType.SERVER;
            }

            phCallable.addMetaData("jira.version", new BuildUtilsInfoImpl().getVersion());
            phCallable.addMetaData("jira.deployment", deploymentType.name());

            final PhoneHomeService phService = new PhoneHomeService(logger, null);
            phService.phoneHome(phCallable);
            jiraSettingsService.setLastPhoneHome(LocalDate.now());
        } catch (final Exception phException) {
            logger.debug("Unable to phone home: " + phException.getMessage());
        }
    }
}
