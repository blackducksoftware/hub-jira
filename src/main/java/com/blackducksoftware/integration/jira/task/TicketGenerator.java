/**
 * Hub JIRA Plugin
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
package com.blackducksoftware.integration.jira.task;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;

import com.blackducksoftware.integration.hub.api.generated.enumeration.NotificationType;
import com.blackducksoftware.integration.hub.api.generated.view.UserView;
import com.blackducksoftware.integration.hub.exception.HubIntegrationException;
import com.blackducksoftware.integration.hub.exception.HubItemTransformException;
import com.blackducksoftware.integration.hub.notification.NotificationDetailResult;
import com.blackducksoftware.integration.hub.notification.NotificationDetailResults;
import com.blackducksoftware.integration.hub.service.HubService;
import com.blackducksoftware.integration.hub.service.IssueService;
import com.blackducksoftware.integration.hub.service.NotificationService;
import com.blackducksoftware.integration.hub.service.bucket.HubBucket;
import com.blackducksoftware.integration.jira.common.HubJiraLogger;
import com.blackducksoftware.integration.jira.common.HubProjectMappings;
import com.blackducksoftware.integration.jira.common.JiraContext;
import com.blackducksoftware.integration.jira.common.TicketInfoFromSetup;
import com.blackducksoftware.integration.jira.config.HubJiraFieldCopyConfigSerializable;
import com.blackducksoftware.integration.jira.task.conversion.NotificationToEventConverter;
import com.blackducksoftware.integration.jira.task.conversion.output.eventdata.EventData;
import com.blackducksoftware.integration.jira.task.conversion.output.eventdata.EventDataFormatHelper;
import com.blackducksoftware.integration.jira.task.issue.HubIssueTrackerHandler;
import com.blackducksoftware.integration.jira.task.issue.JiraIssueHandler;
import com.blackducksoftware.integration.jira.task.issue.JiraServices;

/**
 * Collects recent notifications from the Hub, and generates JIRA tickets for them.
 */
public class TicketGenerator {
    private final HubJiraLogger logger = new HubJiraLogger(Logger.getLogger(this.getClass().getName()));

    private final HubService hubService;
    private final NotificationService notificationService;
    private final JiraContext jiraContext;
    private final JiraServices jiraServices;
    private final JiraSettingsService jiraSettingsService;
    private final TicketInfoFromSetup ticketInfoFromSetup;
    private final HubIssueTrackerHandler hubIssueTrackerHandler;
    private final boolean shouldCreateVulnerabilityIssues;
    private final List<String> linksOfRulesToMonitor;
    private final HubJiraFieldCopyConfigSerializable fieldCopyConfig;

    public TicketGenerator(final HubService hubService, final NotificationService notificationService, final IssueService issueService, final JiraServices jiraServices, final JiraContext jiraContext,
            final JiraSettingsService jiraSettingsService, final TicketInfoFromSetup ticketInfoFromSetup, final boolean shouldCreateVulnerabilityIssues, final List<String> listOfRulesToMonitor,
            final HubJiraFieldCopyConfigSerializable fieldCopyConfig) {
        this.hubService = hubService;
        this.notificationService = notificationService;
        this.jiraServices = jiraServices;
        this.jiraContext = jiraContext;
        this.jiraSettingsService = jiraSettingsService;
        this.ticketInfoFromSetup = ticketInfoFromSetup;
        this.hubIssueTrackerHandler = new HubIssueTrackerHandler(jiraServices, jiraSettingsService, issueService);
        this.shouldCreateVulnerabilityIssues = shouldCreateVulnerabilityIssues;
        this.linksOfRulesToMonitor = listOfRulesToMonitor;
        this.fieldCopyConfig = fieldCopyConfig;
    }

    public void generateTicketsForNotificationsInDateRange(final UserView hubUser, final HubProjectMappings hubProjectMappings, final Date startDate, final Date endDate) throws HubIntegrationException {
        if ((hubProjectMappings == null) || (hubProjectMappings.size() == 0)) {
            logger.debug("The configuration does not specify any Hub projects to monitor");
            return;
        }
        try {
            final HubBucket hubBucket = new HubBucket();
            final NotificationDetailResults results = notificationService.getAllUserNotificationDetailResultsPopulated(hubBucket, hubUser, startDate, endDate);
            final List<NotificationDetailResult> notificationDetailResults = results.getResults();
            reportAnyErrors(hubBucket);

            logger.info(String.format("There are %d notifications to handle", notificationDetailResults.size()));
            if (!notificationDetailResults.isEmpty()) {
                final JiraIssueHandler issueHandler = new JiraIssueHandler(jiraServices, jiraContext, jiraSettingsService, ticketInfoFromSetup, hubIssueTrackerHandler);
                final NotificationToEventConverter notificationConverter = new NotificationToEventConverter(jiraServices, jiraContext, jiraSettingsService, hubProjectMappings, fieldCopyConfig, new EventDataFormatHelper(logger, hubService),
                        linksOfRulesToMonitor, hubService, logger);
                handleEachIssue(notificationConverter, notificationDetailResults, issueHandler, hubBucket);
            }
        } catch (final Exception e) {
            logger.error(e);
            jiraSettingsService.addHubError(e, "generateTicketsForRecentNotifications");
        }
    }

    private void handleEachIssue(final NotificationToEventConverter converter, final List<NotificationDetailResult> notificationDetailResults, final JiraIssueHandler issueHandler, final HubBucket hubBucket)
            throws HubIntegrationException {
        for (final NotificationDetailResult detailResult : notificationDetailResults) {
            if (shouldCreateVulnerabilityIssues || !NotificationType.VULNERABILITY.equals(detailResult.getType())) {
                final Collection<EventData> events = converter.createEventDataForNotificationDetailResult(detailResult, hubBucket);
                for (final EventData event : events) {
                    try {
                        issueHandler.handleEvent(event);
                    } catch (final Exception e) {
                        logger.error(e);
                        jiraSettingsService.addHubError(e, "issueHandler.handleEvent(event)");
                    }
                }
            }
        }
    }

    private void reportAnyErrors(final HubBucket hubBucket) {
        hubBucket.getAvailableUris().parallelStream().forEach(uri -> {
            final Optional<Exception> uriError = hubBucket.getError(uri);
            if (uriError.isPresent()) {
                final Exception e = uriError.get();
                if ((e instanceof ExecutionException) && (e.getCause() != null) && (e.getCause() instanceof HubItemTransformException)) {
                    final String msg = String.format(
                            "WARNING: An error occurred while collecting supporting information from the Hub for a notification: %s; This can be caused by deletion of Hub data (project version, component, etc.) relevant to the notification soon after the notification was generated",
                            e.getMessage());
                    logger.warn(msg);
                    jiraSettingsService.addHubError(msg, "getAllNotifications");
                } else {
                    logger.error("Error retrieving notifications: " + e.getMessage(), e);
                    jiraSettingsService.addHubError(e, "getAllNotifications");
                }
            }
        });
    }

}
