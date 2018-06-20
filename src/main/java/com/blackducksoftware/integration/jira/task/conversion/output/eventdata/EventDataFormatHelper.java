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
package com.blackducksoftware.integration.jira.task.conversion.output.eventdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.api.generated.component.RemediatingVersionView;
import com.blackducksoftware.integration.hub.api.generated.component.RemediationOptionsView;
import com.blackducksoftware.integration.hub.api.generated.component.VersionBomLicenseView;
import com.blackducksoftware.integration.hub.api.generated.enumeration.ComplexLicenseType;
import com.blackducksoftware.integration.hub.api.generated.view.ComplexLicenseView;
import com.blackducksoftware.integration.hub.api.generated.view.ComponentVersionView;
import com.blackducksoftware.integration.hub.api.generated.view.PolicyRuleViewV2;
import com.blackducksoftware.integration.hub.api.generated.view.ProjectVersionView;
import com.blackducksoftware.integration.hub.api.generated.view.VersionBomComponentView;
import com.blackducksoftware.integration.hub.notification.content.VulnerabilityNotificationContent;
import com.blackducksoftware.integration.hub.notification.content.VulnerabilitySourceQualifiedId;
import com.blackducksoftware.integration.hub.notification.content.detail.NotificationContentDetail;
import com.blackducksoftware.integration.hub.service.ComponentService;
import com.blackducksoftware.integration.hub.service.HubService;
import com.blackducksoftware.integration.hub.service.bucket.HubBucket;
import com.blackducksoftware.integration.jira.common.HubJiraConstants;
import com.blackducksoftware.integration.jira.common.HubJiraLogger;

public class EventDataFormatHelper {
    private final HubJiraLogger logger;
    private final HubService hubService;

    public EventDataFormatHelper(final HubJiraLogger logger, final HubService hubService) {
        this.logger = logger;
        this.hubService = hubService;
    }

    public String getIssueSummary(final NotificationContentDetail detail, final Optional<PolicyRuleViewV2> optionalPolicyRule) {
        final String projectName = detail.getProjectName().orElse("?");
        final String projectVersionName = detail.getProjectVersionName().orElse("?");
        if (detail.isPolicy()) {
            final String componentString = getComponentString(detail.getComponentName(), detail.getComponentVersionName());
            final String ruleName = optionalPolicyRule.isPresent() ? optionalPolicyRule.get().name : "?";
            final String issueSummaryTemplate = "Black Duck policy violation detected on Hub project '%s' / '%s', component '%s' [Rule: '%s']";
            return String.format(issueSummaryTemplate, projectName, projectVersionName, componentString, ruleName);
        } else if (detail.isVulnerability()) {
            final StringBuilder issueSummary = new StringBuilder();
            issueSummary.append("Black Duck vulnerability status changes on Hub project '");
            issueSummary.append(projectName);
            issueSummary.append("' / '");
            issueSummary.append(projectVersionName);
            issueSummary.append("', component '");
            issueSummary.append(detail.getComponentName().orElse("?"));
            issueSummary.append("' / '");
            issueSummary.append(detail.getComponentVersionName().orElse("?"));
            issueSummary.append("'");
            return issueSummary.toString();
        }
        return "";
    }

    private String getComponentString(final Optional<String> componentName, final Optional<String> componentVersionName) {
        String componentString = "?";
        if (componentName.isPresent()) {
            componentString = componentName.get();
            if (componentVersionName.isPresent()) {
                componentString += "' / '" + componentVersionName.get();
            }
        }
        return componentString;
    }

    public String getIssueDescription(final NotificationContentDetail detail, final HubBucket hubBucket) {
        final StringBuilder issueDescription = new StringBuilder();

        issueDescription.append("Black Duck has detected ");
        if (detail.isPolicy()) {
            issueDescription.append("a policy violation.  \n");
        } else if (detail.isVulnerability()) {
            issueDescription.append("vulnerabilities. For details, see the comments below, or the project's ");
            String vulnerableComponentsLink = null;
            if (detail.getProjectVersion().isPresent()) {
                final ProjectVersionView projectVersion = hubBucket.get(detail.getProjectVersion().get());
                vulnerableComponentsLink = hubService.getFirstLinkSafely(projectVersion, ProjectVersionView.VULNERABLE_COMPONENTS_LINK);
            }
            if (vulnerableComponentsLink != null) {
                issueDescription.append("[vulnerabilities|");
                issueDescription.append(vulnerableComponentsLink);
                issueDescription.append("]");
            } else {
                issueDescription.append("vulnerabilities");
            }
            issueDescription.append(" in the Hub.");
        }

        if (detail.getComponentVersion().isPresent()) {
            try {
                final ComponentVersionView componentVersion = hubBucket.get(detail.getComponentVersion().get());
                final String licenseText = getComponentLicensesStringWithLinksAtlassianFormat(componentVersion);
                if (StringUtils.isNotBlank(licenseText)) {
                    issueDescription.append("  \nKB Component license(s): ");
                    issueDescription.append(licenseText);
                }
                if (detail.isVulnerability()) {
                    appendRemediationOptionsText(issueDescription, componentVersion);
                }
            } catch (final IntegrationException e) {
                // omit additional text
            }
        }
        return issueDescription.toString();
    }

    public String generateVulnerabilitiesComment(final VulnerabilityNotificationContent vulnerabilityContent) {
        final StringBuilder commentText = new StringBuilder();
        commentText.append("(Black Duck Hub JIRA plugin auto-generated comment)\n");
        appendVulnerabilitiesCommentText(commentText, vulnerabilityContent.newVulnerabilityIds, "added");
        appendVulnerabilitiesCommentText(commentText, vulnerabilityContent.updatedVulnerabilityIds, "updated");
        appendVulnerabilitiesCommentText(commentText, vulnerabilityContent.deletedVulnerabilityIds, "deleted");
        return commentText.toString();
    }

    private void appendRemediationOptionsText(final StringBuilder stringBuilder, final ComponentVersionView componentVersionView) {
        // TODO use the HubService once the Hub APIs have the link.
        final ComponentService componentService = new ComponentService(hubService);
        RemediationOptionsView remediationOptions;
        try {
            remediationOptions = componentService.getRemediationInformation(componentVersionView);
        } catch (final IntegrationException e) {
            logger.debug("Could not get remediation information: ");
            logger.debug(e.getMessage());
            return;
        }
        if (remediationOptions != null) {
            // TODO This has "Beta" text. Change that text when confidence in the information is high.
            stringBuilder.append("\n\nRemediation Information (Beta):\n");
            if (remediationOptions.fixesPreviousVulnerabilities != null) {
                appendRemediationVersionText(stringBuilder, remediationOptions.fixesPreviousVulnerabilities, "fixes previous vulnerabilities");
            }
            if (remediationOptions.latestAfterCurrent != null) {
                appendRemediationVersionText(stringBuilder, remediationOptions.latestAfterCurrent, "is the most recent");
            }
            if (remediationOptions.noVulnerabilities != null) {
                appendRemediationVersionText(stringBuilder, remediationOptions.noVulnerabilities, "has no known vulnerabilities");
            }
        }
    }

    private void appendRemediationVersionText(final StringBuilder stringBuilder, final RemediatingVersionView remediatingVersionView, final String versionComment) {
        stringBuilder.append(" * Version [");
        stringBuilder.append(remediatingVersionView.name);
        stringBuilder.append("|");
        stringBuilder.append(remediatingVersionView.componentVersion);
        stringBuilder.append("] ");
        stringBuilder.append(versionComment);
        if (remediatingVersionView.vulnerabilityCount != null && remediatingVersionView.vulnerabilityCount > 0) {
            stringBuilder.append(". Vulnerability count: ");
            stringBuilder.append(remediatingVersionView.vulnerabilityCount);
        }
        stringBuilder.append(".\n");
    }

    private void appendVulnerabilitiesCommentText(final StringBuilder commentText, final List<VulnerabilitySourceQualifiedId> vulns, final String verb) {
        final boolean hasContent = vulns != null && !vulns.isEmpty();
        final String formattedVerb = hasContent ? "*" + verb + "*" : "_" + verb + "_";
        commentText.append("Vulnerabilities " + formattedVerb + ": ");
        int index = 0;
        if (hasContent) {
            for (final VulnerabilitySourceQualifiedId vuln : vulns) {
                commentText.append(vuln.vulnerabilityId + " (" + vuln.source + ")");
                if ((index + 1) < vulns.size()) {
                    commentText.append(", ");
                }
                index++;
            }
        } else {
            commentText.append("None");
        }
        commentText.append("\n");
    }

    public String getComponentLicensesStringPlainText(final VersionBomComponentView componentVersion) throws IntegrationException {
        if (componentVersion.licenses != null && !componentVersion.licenses.isEmpty()) {
            EventDataLicense license;
            if (componentVersion.licenses.size() == 1) {
                license = new EventDataLicense(componentVersion.licenses.get(0));
            } else {
                license = new EventDataLicense(componentVersion.licenses);
            }
            return getComponentLicensesString(license, false);
        }
        return "";
    }

    public String getComponentLicensesStringWithLinksAtlassianFormat(final VersionBomComponentView componentVersion) throws IntegrationException {
        if (componentVersion.licenses != null && !componentVersion.licenses.isEmpty()) {
            EventDataLicense license;
            if (componentVersion.licenses.size() == 1) {
                license = new EventDataLicense(componentVersion.licenses.get(0));
            } else {
                license = new EventDataLicense(componentVersion.licenses);
            }
            return getComponentLicensesString(license, true);
        }
        return "";
    }

    public String getComponentLicensesStringPlainText(final ComponentVersionView componentVersion) throws IntegrationException {
        final EventDataLicense license = new EventDataLicense(componentVersion.license);
        return getComponentLicensesString(license, false);
    }

    public String getComponentLicensesStringWithLinksAtlassianFormat(final ComponentVersionView componentVersion) throws IntegrationException {
        final EventDataLicense license = new EventDataLicense(componentVersion.license);
        return getComponentLicensesString(license, true);
    }

    private String getComponentLicensesString(final EventDataLicense eventDataLicense, final boolean includeLinks) throws IntegrationException {
        String licensesString = "";
        if (eventDataLicense.isPopulated()) {
            final ComplexLicenseType type = eventDataLicense.licenseType;
            final StringBuilder sb = new StringBuilder();

            if (type != null) {
                final String licenseJoinString = (ComplexLicenseType.CONJUNCTIVE.equals(type)) ? HubJiraConstants.LICENSE_NAME_JOINER_AND : HubJiraConstants.LICENSE_NAME_JOINER_OR;
                int licenseIndex = 0;
                for (final EventDataLicense license : eventDataLicense.licenses) {
                    if (licenseIndex++ > 0) {
                        sb.append(licenseJoinString);
                    }
                    createLicenseString(sb, license, includeLinks);
                }

            } else {
                createLicenseString(sb, eventDataLicense, includeLinks);
            }
            licensesString = sb.toString();
        }
        return licensesString;
    }

    private void createLicenseString(final StringBuilder sb, final EventDataLicense license, final boolean includeLinks) throws IntegrationException {
        final String licenseTextUrl = getLicenseTextUrl(license);
        logger.debug("Link to licence text: " + licenseTextUrl);

        if (includeLinks) {
            sb.append("[");
        }
        sb.append(license.licenseDisplay);
        if (includeLinks) {
            sb.append("|");
            sb.append(licenseTextUrl);
            sb.append("]");
        }
    }

    private String getLicenseTextUrl(final EventDataLicense license) throws IntegrationException {
        final String licenseUrl = license.licenseUrl;
        final ComplexLicenseView fullLicense = hubService.getResponse(licenseUrl, ComplexLicenseView.class);
        final String licenseTextUrl = hubService.getFirstLink(fullLicense, "text");
        return licenseTextUrl;
    }

    class EventDataLicense {
        public final String licenseUrl;
        public final String licenseDisplay;
        public final ComplexLicenseType licenseType;
        public final List<EventDataLicense> licenses;

        public EventDataLicense(final List<VersionBomLicenseView> licenses) {
            this.licenseUrl = null;
            this.licenseDisplay = "Multiple licenses";
            this.licenseType = ComplexLicenseType.CONJUNCTIVE;
            this.licenses = createLicenseListFromBom(licenses);
        }

        public EventDataLicense(final ComplexLicenseView licenseView) {
            this.licenseUrl = licenseView.license;
            this.licenseDisplay = licenseView.licenseDisplay;
            this.licenseType = licenseView.type;
            this.licenses = createLicenseListFromComplex(licenseView.licenses);
        }

        public EventDataLicense(final VersionBomLicenseView licenseView) {
            this.licenseUrl = licenseView.license;
            this.licenseDisplay = licenseView.licenseDisplay;
            this.licenseType = licenseView.licenseType;
            this.licenses = createLicenseListFromBom(licenseView.licenses);
        }

        public List<EventDataLicense> createLicenseListFromComplex(final List<ComplexLicenseView> licenses) {
            final List<EventDataLicense> eventDataLicenses = new ArrayList<>();
            if (licenses != null) {
                for (final ComplexLicenseView license : licenses) {
                    eventDataLicenses.add(new EventDataLicense(license));
                }
            }
            return eventDataLicenses;
        }

        public List<EventDataLicense> createLicenseListFromBom(final List<VersionBomLicenseView> licenses) {
            final List<EventDataLicense> eventDataLicenses = new ArrayList<>();
            if (licenses != null) {
                for (final VersionBomLicenseView license : licenses) {
                    eventDataLicenses.add(new EventDataLicense(license));
                }
            }
            return eventDataLicenses;
        }

        public boolean isPopulated() {
            return licenseDisplay != null && licenseUrl != null;
        }

    }

}
