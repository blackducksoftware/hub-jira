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
package com.blackducksoftware.integration.jira.blackduck;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;

import com.blackducksoftware.integration.jira.workflow.notification.NotificationContentDetail;
import com.google.gson.JsonElement;
import com.synopsys.integration.blackduck.api.UriSingleResponse;
import com.synopsys.integration.blackduck.api.core.BlackDuckResponse;
import com.synopsys.integration.blackduck.api.core.BlackDuckView;
import com.synopsys.integration.blackduck.api.core.LinkMultipleResponses;
import com.synopsys.integration.blackduck.api.core.LinkSingleResponse;
import com.synopsys.integration.blackduck.api.generated.component.RiskCountView;
import com.synopsys.integration.blackduck.api.generated.enumeration.NotificationType;
import com.synopsys.integration.blackduck.api.generated.enumeration.PolicySummaryStatusType;
import com.synopsys.integration.blackduck.api.generated.enumeration.RiskCountType;
import com.synopsys.integration.blackduck.api.generated.response.RemediationOptionsView;
import com.synopsys.integration.blackduck.api.generated.view.ComponentVersionView;
import com.synopsys.integration.blackduck.api.generated.view.PolicyRuleView;
import com.synopsys.integration.blackduck.api.generated.view.ProjectVersionView;
import com.synopsys.integration.blackduck.api.generated.view.RiskProfileView;
import com.synopsys.integration.blackduck.api.generated.view.VersionBomComponentView;
import com.synopsys.integration.blackduck.api.manual.component.VulnerabilitySourceQualifiedId;
import com.synopsys.integration.blackduck.service.BlackDuckService;
import com.synopsys.integration.blackduck.service.ComponentService;
import com.synopsys.integration.blackduck.service.bucket.BlackDuckBucket;
import com.synopsys.integration.blackduck.service.model.ProjectVersionWrapper;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.rest.exception.IntegrationRestException;

public class BlackDuckDataHelper {
    private final Logger logger;
    private final BlackDuckService blackDuckService;
    private final BlackDuckBucket blackDuckBucket;

    public BlackDuckDataHelper(final Logger logger, final BlackDuckService blackDuckService, final BlackDuckBucket blackDuckBucket) {
        this.logger = logger;
        this.blackDuckService = blackDuckService;
        this.blackDuckBucket = blackDuckBucket;
    }

    public String getBlackDuckBaseUrl() {
        return blackDuckService.getBlackDuckBaseUrl();
    }

    public VersionBomComponentView getBomComponent(final UriSingleResponse<VersionBomComponentView> bomComponentLocation) throws IntegrationException {
        final IntegrationRestException restException;
        try {
            final VersionBomComponentView versionBomComponentView = getResponse(bomComponentLocation);
            return versionBomComponentView;
        } catch (final IntegrationRestException caughtRestException) {
            restException = caughtRestException;
        } catch (final Exception genericException) {
            logger.error("An Error occurred when retrieving the bom component. ", genericException);
            throw genericException;
        }
        throw restException;
    }

    public Optional<RemediationOptionsView> getRemediationInformation(final ComponentVersionView componentVersionView) {
        // TODO use the BlackDuckService once the Black Duck APIs have the link.
        final ComponentService componentService = new ComponentService(blackDuckService, new Slf4jIntLogger(logger));
        try {
            return componentService.getRemediationInformation(componentVersionView);
        } catch (final IntegrationException e) {
            logger.debug("Could not get remediation information: ");
            logger.debug(e.getMessage());
        }
        return Optional.empty();
    }

    public ProjectVersionWrapper getProjectVersionWrapper(final NotificationContentDetail detail) throws IntegrationException {
        final Optional<String> optionalProjectVersionUri = detail.getProjectVersion().map(UriSingleResponse::getUri);
        if (optionalProjectVersionUri.isPresent()) {
            return getProjectVersionWrapper(optionalProjectVersionUri.get());
        }

        final Optional<UriSingleResponse<VersionBomComponentView>> optionalBomComponentResponse = detail.getBomComponent();
        if (optionalBomComponentResponse.isPresent()) {
            final VersionBomComponentView versionBomComponent = getResponse(optionalBomComponentResponse.get());
            return getProjectVersionWrapper(versionBomComponent);
        }
        throw new IntegrationException("No Black Duck project data available from the notification.");
    }

    public ProjectVersionWrapper getProjectVersionWrapper(final VersionBomComponentView versionBomComponent) throws IntegrationException {
        // TODO Stop using this when Black Duck supports going back to the project-version
        final Optional<String> versionBomComponentHref = versionBomComponent.getHref();
        if (versionBomComponentHref.isPresent()) {
            final String versionHref = versionBomComponentHref.get();
            final int componentsIndex = versionHref.indexOf(ProjectVersionView.COMPONENTS_LINK);
            final String projectVersionUri = versionHref.substring(0, componentsIndex - 1);

            return getProjectVersionWrapper(projectVersionUri);
        }

        return new ProjectVersionWrapper();
    }

    public Optional<NotificationType> getNotificationTypeFromPolicyRule(final PolicyRuleView policyRule) {
        final Optional<PolicySummaryStatusType> optionalPolicySummaryStatusType = getPolicySummaryStatusTypeFromRule(policyRule);
        if (optionalPolicySummaryStatusType.isPresent()) {
            switch (optionalPolicySummaryStatusType.get()) {
                case IN_VIOLATION:
                    return Optional.of(NotificationType.RULE_VIOLATION);
                case NOT_IN_VIOLATION:
                    return Optional.of(NotificationType.RULE_VIOLATION_CLEARED);
                case IN_VIOLATION_OVERRIDDEN:
                    return Optional.of(NotificationType.POLICY_OVERRIDE);
            }
        }
        return Optional.empty();
    }

    private Optional<PolicySummaryStatusType> getPolicySummaryStatusTypeFromRule(final PolicyRuleView policyRule) {
        // TODO remove when blackduck-common-api supports this field
        if (policyRule.getEnabled()) {
            final String jsonFieldName = "policyApprovalStatus";
            final JsonElement policyElement = policyRule.getJsonElement();
            if (null != policyElement && policyElement.isJsonObject()) {
                final JsonElement approvalStatusElement = policyElement.getAsJsonObject().get(jsonFieldName);
                if (null != approvalStatusElement && approvalStatusElement.isJsonPrimitive()) {
                    final String approvalStatusName = approvalStatusElement.getAsString();
                    return Optional.of(EnumUtils.getEnum(PolicySummaryStatusType.class, approvalStatusName));
                }
            }
        }
        return Optional.empty();
    }

    private ProjectVersionWrapper getProjectVersionWrapper(final String projectVersionUri) throws IntegrationException {
        final ProjectVersionView projectVersion = getResponse(projectVersionUri, ProjectVersionView.class);

        final ProjectVersionWrapper wrapper = new ProjectVersionWrapper();
        wrapper.setProjectVersionView(projectVersion);
        blackDuckService.getResponse(projectVersion, ProjectVersionView.PROJECT_LINK_RESPONSE).ifPresent(project -> wrapper.setProjectView(project));
        return wrapper;
    }

    public boolean doesNotificationOnlyHaveDeletes(final List<VulnerabilitySourceQualifiedId> addedIds, final List<VulnerabilitySourceQualifiedId> updatedIds, final List<VulnerabilitySourceQualifiedId> deletedIds) {
        return deletedIds != null && deletedIds.size() > 0 && addedIds != null && addedIds.size() == 0 && updatedIds != null && updatedIds.size() == 0;
    }

    public boolean doesSecurityRiskProfileHaveVulnerabilities(final RiskProfileView securityRiskProfile) {
        logger.debug("Checking if the component still has vulnerabilities...");
        final int vulnerablitiesCount = getSumOfRiskCounts(securityRiskProfile.getCounts());
        logger.debug("Number of vulnerabilities found: " + vulnerablitiesCount);
        if (vulnerablitiesCount > 0) {
            logger.debug("This component still has vulnerabilities");
            return true;
        }
        return false;
    }

    private int getSumOfRiskCounts(final List<RiskCountView> vulnerabilityCounts) {
        int count = 0;
        for (final RiskCountView riskCount : vulnerabilityCounts) {
            if (!RiskCountType.OK.equals(riskCount.getCountType())) {
                count += riskCount.getCount().intValue();
            }
        }
        return count;
    }

    public <T extends BlackDuckResponse> T getResponse(final String uri, final Class<T> clazz) throws IntegrationException {
        return getResponse(new UriSingleResponse<>(uri, clazz));
    }

    public <T extends BlackDuckResponse> T getResponse(final UriSingleResponse<T> uriSingleResponse) throws IntegrationException {
        T response = blackDuckBucket.get(uriSingleResponse);
        if (response == null) {
            response = blackDuckService.getResponse(uriSingleResponse);
            blackDuckBucket.addValid(uriSingleResponse.getUri(), response);
        }
        return response;
    }

    public <T extends BlackDuckResponse> Optional<T> getResponse(final BlackDuckView view, final LinkSingleResponse<T> linkSingleResponse) throws IntegrationException {
        return blackDuckService.getResponse(view, linkSingleResponse);
    }

    public <T extends BlackDuckResponse> List<T> getAllResponses(final BlackDuckView view, final LinkMultipleResponses<T> linkMultipleResponses) throws IntegrationException {
        return blackDuckService.getAllResponses(view, linkMultipleResponses);
    }

    public <T extends BlackDuckResponse> T getResponseNullable(final String uri, final Class<T> clazz) {
        try {
            return getResponse(uri, clazz);
        } catch (final IntegrationException e) {
            logger.debug("Could not get response from Black Duck.", e);
        }
        return null;
    }

    public Optional<String> getFirstLink(final BlackDuckView view, final String linkKey) {
        return view.getFirstLink(linkKey);
    }

    public String getFirstLinkSafely(final BlackDuckView view, final String linkKey) {
        return getFirstLink(view, linkKey).orElse(null);
    }

    public Optional<String> getHref(final BlackDuckView view) {
        return view.getHref();
    }

    public String getHrefNullable(final BlackDuckView view) {
        final Optional<String> href = getHref(view);
        return href.orElse(null);
    }
}
