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
package com.blackducksoftware.integration.jira.issue.handler;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.jira.blackduck.BlackDuckDataHelper;
import com.blackducksoftware.integration.jira.common.BlackDuckJiraConstants;
import com.blackducksoftware.integration.jira.issue.model.IssueCategory;
import com.synopsys.integration.blackduck.api.core.LinkSingleResponse;
import com.synopsys.integration.blackduck.api.generated.component.RemediatingVersionView;
import com.synopsys.integration.blackduck.api.generated.component.RiskCountView;
import com.synopsys.integration.blackduck.api.generated.component.VersionBomLicenseView;
import com.synopsys.integration.blackduck.api.generated.enumeration.ComplexLicenseType;
import com.synopsys.integration.blackduck.api.generated.enumeration.RiskCountType;
import com.synopsys.integration.blackduck.api.generated.response.RemediationOptionsView;
import com.synopsys.integration.blackduck.api.generated.view.ComplexLicenseView;
import com.synopsys.integration.blackduck.api.generated.view.ComponentVersionView;
import com.synopsys.integration.blackduck.api.generated.view.LicenseView;
import com.synopsys.integration.blackduck.api.generated.view.ProjectVersionView;
import com.synopsys.integration.blackduck.api.generated.view.RiskProfileView;
import com.synopsys.integration.exception.IntegrationException;

public class DataFormatHelper {
    private final Logger logger = LoggerFactory.getLogger(DataFormatHelper.class);
    private final BlackDuckDataHelper blackDuckDataHelper;

    public DataFormatHelper(final BlackDuckDataHelper blackDuckDataHelper) {
        this.blackDuckDataHelper = blackDuckDataHelper;
    }

    public String getIssueDescription(final IssueCategory issueCategory, final String projectVersionUrl, final String componentName, final String componentVersionUrl, final boolean includeRemediationInfo) {
        final StringBuilder issueDescription = new StringBuilder();

        issueDescription.append("Black Duck has detected ");
        if (IssueCategory.POLICY.equals(issueCategory) || IssueCategory.SECURITY_POLICY.equals(issueCategory)) {
            issueDescription.append("a policy violation.  \n\n");
        } else if (IssueCategory.VULNERABILITY.equals(issueCategory)) {
            issueDescription.append("vulnerabilities. For details, see the comments below, or the project's ");
            String vulnerableComponentsLink = null;
            final ProjectVersionView projectVersion = blackDuckDataHelper.getResponseNullable(projectVersionUrl, ProjectVersionView.class);
            if (projectVersion != null) {
                vulnerableComponentsLink = blackDuckDataHelper.getFirstLinkSafely(projectVersion, ProjectVersionView.VULNERABLE_COMPONENTS_LINK);
            }
            if (vulnerableComponentsLink != null) {
                final String vulnerableComponentsLinkWithQueryParams = createUrlWithComponentNameQuery(vulnerableComponentsLink, componentName).orElse(null);
                issueDescription.append("[vulnerabilities|");
                issueDescription.append(vulnerableComponentsLinkWithQueryParams);
                issueDescription.append("]");
            } else {
                issueDescription.append("vulnerabilities");
            }
            issueDescription.append(" in Black Duck.  \n\n");
        }

        if (componentVersionUrl != null) {
            final ComponentVersionView componentVersion = blackDuckDataHelper.getResponseNullable(componentVersionUrl, ComponentVersionView.class);
            final String licenseText = getComponentLicensesStringWithLinksAtlassianFormat(componentVersion);
            if (StringUtils.isNotBlank(licenseText)) {
                issueDescription.append("KB Component license(s): ");
                issueDescription.append(licenseText);
            }
            if (includeRemediationInfo) {
                appendRemediationOptionsText(issueDescription, componentVersion);
            }
        }
        return issueDescription.toString();
    }

    public Optional<String> createUrlWithComponentNameQuery(final String url, final String componentName) {
        if (StringUtils.isNotBlank(url) && StringUtils.isNotBlank(componentName)) {
            try {
                // Black Duck does not encode query parameters in the traditional way (UTF-8), so we must do a little extra work to ensure the encoding will match.
                // https://stackoverflow.com/a/49796882/6921621
                final String unencodedUrl = String.format("%s?q=componentName:%s", url, componentName);
                final URL encodedUrl = new URL(unencodedUrl);
                final URI uri = new URI(encodedUrl.getProtocol(), encodedUrl.getUserInfo(), IDN.toASCII(encodedUrl.getHost()), encodedUrl.getPort(), encodedUrl.getPath(), encodedUrl.getQuery(), encodedUrl.getRef());

                final String asciiString = uri.toASCIIString();
                return Optional.of(asciiString);
            } catch (final MalformedURLException | URISyntaxException e) {
                logger.debug("Error encoding url: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    public String createIssueSummary(final IssueCategory issueCategory, final String projectName, final String projectVersionName, final String componentName, final String componentVersionName, final String ruleName) {
        if (IssueCategory.POLICY.equals(issueCategory) || IssueCategory.SECURITY_POLICY.equals(issueCategory)) {
            final String policySummaryTemplate = "Policy Violation: Project '%s' / '%s', Component '%s', Rule '%s'";
            return String.format(policySummaryTemplate, projectName, projectVersionName, getComponentString(componentName, componentVersionName), ruleName);
        } else if (IssueCategory.VULNERABILITY.equals(issueCategory)) {
            final String vulnerabilitySummaryTemplate = "Vulnerability: Project '%s' / '%s', Component '%s' / '%s'";
            return String.format(vulnerabilitySummaryTemplate, projectName, projectVersionName, componentName, componentVersionName);
        } else {
            return null;
        }
    }

    public String generateVulnerabilitiesCommentForPolicy(final Set<NotificationVulnerability> vulnerabilities) {
        final StringBuilder commentText = new StringBuilder();
        commentText.append("(Black Duck plugin auto-generated comment)\n");
        appendVulnerabilitiesCommentText(commentText, vulnerabilities, "found");
        return commentText.toString();
    }

    public String generateVulnerabilitiesComment(final List<NotificationVulnerability> addedIds, final List<NotificationVulnerability> updatedIds, final List<NotificationVulnerability> deletedIds, RiskProfileView riskProfileView) {
        final StringBuilder commentText = new StringBuilder();
        commentText.append("(Black Duck plugin auto-generated comment)\n");
        appendVulnerabilitiesCommentText(commentText, addedIds, "added");
        appendVulnerabilitiesCommentText(commentText, updatedIds, "updated");
        appendVulnerabilitiesCommentText(commentText, deletedIds, "deleted");
        appendVulnerabilitiesCountComment(commentText, riskProfileView);
        return commentText.toString();
    }

    private void appendVulnerabilitiesCountComment(StringBuilder commentText, RiskProfileView riskProfileView) {
        commentText.append("\n");
        commentText.append("Total Vulnerabilities:");

        final List<RiskCountView> counts = riskProfileView.getCounts();
        for (RiskCountView riskCountView : counts) {
            final Integer count = riskCountView.getCount();
            final RiskCountType countType = riskCountView.getCountType();
            if (count != null && count > 0 && countType != null) {
                final String severity = countType.prettyPrint();
                commentText.append("\n");
                commentText.append(severity);
                commentText.append(": ");
                commentText.append(count);
            }
        }
    }

    private void appendRemediationOptionsText(final StringBuilder stringBuilder, final ComponentVersionView componentVersionView) {
        final Optional<RemediationOptionsView> optionalRemediation = blackDuckDataHelper.getRemediationInformation(componentVersionView);
        if (optionalRemediation.isPresent()) {
            final RemediationOptionsView remediationOptions = optionalRemediation.get();
            stringBuilder.append("\nRemediation Information:\n");
            if (remediationOptions.getFixesPreviousVulnerabilities() != null) {
                appendRemediationVersionText(stringBuilder, remediationOptions.getFixesPreviousVulnerabilities(), "fixes previous vulnerabilities");
            }
            if (remediationOptions.getLatestAfterCurrent() != null) {
                appendRemediationVersionText(stringBuilder, remediationOptions.getLatestAfterCurrent(), "is the most recent");
            }
            if (remediationOptions.getNoVulnerabilities() != null) {
                appendRemediationVersionText(stringBuilder, remediationOptions.getNoVulnerabilities(), "has no known vulnerabilities");
            }
        }
    }

    private void appendRemediationVersionText(final StringBuilder stringBuilder, final RemediatingVersionView remediatingVersionView, final String versionComment) {
        stringBuilder.append(" * Version [");
        stringBuilder.append(remediatingVersionView.getName());
        stringBuilder.append("|");
        stringBuilder.append(remediatingVersionView.getComponentVersion());
        stringBuilder.append("] ");
        stringBuilder.append(versionComment);
        if (remediatingVersionView.getVulnerabilityCount() != null && remediatingVersionView.getVulnerabilityCount() > 0) {
            stringBuilder.append(". Vulnerability count: ");
            stringBuilder.append(remediatingVersionView.getVulnerabilityCount());
        }
        stringBuilder.append(".\n");
    }

    private void appendVulnerabilitiesCommentText(final StringBuilder commentText, final Collection<NotificationVulnerability> vulns, final String verb) {
        final boolean hasContent = vulns != null && !vulns.isEmpty();
        final String formattedVerb = hasContent ? String.format("*%s*", verb) : String.format("_%s_", verb);
        commentText.append(String.format("Vulnerabilities %s: ", formattedVerb));
        int index = 0;
        if (hasContent) {
            for (final NotificationVulnerability vuln : vulns) {
                commentText.append(vuln.getVulnerabilityId() + " (" + vuln.getSource() + ")");
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

    public String getBomLastUpdatedDateFormatted(final ProjectVersionView projectVersion) {
        try {
            return blackDuckDataHelper.getResponse(projectVersion, ProjectVersionView.RISKPROFILE_LINK_RESPONSE)
                       .map(versionRiskProfileView -> new SimpleDateFormat().format(versionRiskProfileView.getBomLastUpdatedAt()))
                       .orElse("");
        } catch (final IntegrationException e) {
            logger.debug(String.format("Could not find the risk profile: %s", e.getMessage()));
        }
        return "";
    }

    public String getComponentLicensesStringPlainText(final List<VersionBomLicenseView> licenses) {
        if (CollectionUtils.isNotEmpty(licenses)) {
            final EventDataLicense license = getEventLicense(licenses);
            return getComponentLicensesString(license, false);
        }
        return "";
    }

    private EventDataLicense getEventLicense(final List<VersionBomLicenseView> licenses) {
        if (licenses.size() == 1) {
            return new EventDataLicense(licenses.get(0));
        }

        return new EventDataLicense(licenses);
    }

    public String getComponentLicensesStringWithLinksAtlassianFormat(final ComponentVersionView componentVersion) {
        if (componentVersion != null) {
            final EventDataLicense license = new EventDataLicense(componentVersion.getLicense());
            return getComponentLicensesString(license, true);
        }
        return "";
    }

    public String getLicenseTextLink(final List<VersionBomLicenseView> licenses, final String licenseName) {
        if (CollectionUtils.isNotEmpty(licenses)) {
            VersionBomLicenseView versionBomLicense = licenses.get(0);
            for (final VersionBomLicenseView license : licenses) {
                if (licenseName.equals(license.getLicenseDisplay())) {
                    versionBomLicense = license;
                }
            }
            try {
                final LicenseView genericLicense = blackDuckDataHelper.getResponse(versionBomLicense.getLicense(), LicenseView.class);
                final Optional<LicenseView> kbLicense = blackDuckDataHelper.getResponse(genericLicense, new LinkSingleResponse<>("license", LicenseView.class));
                if (kbLicense.isPresent()) {
                    return blackDuckDataHelper.getFirstLink(kbLicense.get(), LicenseView.TEXT_LINK).orElse("");
                }
            } catch (final Exception e) {
                logger.debug("Unable to get the BOM component license text.");
            }
        }
        return "";
    }

    private String getComponentString(final String componentName, final String componentVersionName) {
        String componentString = "?";
        if (componentName != null) {
            componentString = componentName;
            if (componentVersionName != null) {
                componentString += "' / '" + componentVersionName;
            }
        }
        return componentString;
    }

    private String getComponentLicensesString(final EventDataLicense eventDataLicense, final boolean includeLinks) {
        String licensesString = "";
        if (eventDataLicense.isPopulated()) {
            final ComplexLicenseType type = eventDataLicense.licenseType;
            final StringBuilder sb = new StringBuilder();

            if (type != null) {
                final String licenseJoinString = (ComplexLicenseType.CONJUNCTIVE.equals(type)) ? BlackDuckJiraConstants.LICENSE_NAME_JOINER_AND : BlackDuckJiraConstants.LICENSE_NAME_JOINER_OR;
                int licenseIndex = 0;
                for (final EventDataLicense license : eventDataLicense.licenses) {
                    if (licenseIndex++ > 0) {
                        sb.append(licenseJoinString);
                    }
                    createLicenseString(sb, license.licenseUrl, license.licenseDisplay, includeLinks);
                }

            } else {
                createLicenseString(sb, eventDataLicense.licenseUrl, eventDataLicense.licenseDisplay, includeLinks);
            }
            licensesString = sb.toString();
        }
        return licensesString;
    }

    private void createLicenseString(final StringBuilder sb, final String licenseUrl, String licenseDisplay, final boolean includeLinks) {
        final String licenseTextUrl = getLicenseTextUrl(licenseUrl);
        logger.debug("Link to license text: " + licenseTextUrl);

        if (includeLinks) {
            sb.append("[");
        }
        sb.append(licenseDisplay);
        if (includeLinks) {
            sb.append("|");
            sb.append(licenseTextUrl);
            sb.append("]");
        }
    }

    private String getLicenseTextUrl(final String licenseUrl) {
        try {
            final ComplexLicenseView fullLicense = blackDuckDataHelper.getResponse(licenseUrl, ComplexLicenseView.class);
            return blackDuckDataHelper.getFirstLink(fullLicense, "text").orElse(blackDuckDataHelper.getBlackDuckBaseUrl());
        } catch (final Exception e) {
            logger.debug("Error getting license text url.");
        }
        return blackDuckDataHelper.getBlackDuckBaseUrl();
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
            this.licenseUrl = licenseView.getLicense();
            this.licenseDisplay = licenseView.getLicenseDisplay();
            this.licenseType = licenseView.getType();
            this.licenses = createLicenseListFromComplex(licenseView.getLicenses());
        }

        public EventDataLicense(final VersionBomLicenseView licenseView) {
            this.licenseUrl = licenseView.getLicense();
            this.licenseDisplay = licenseView.getLicenseDisplay();
            this.licenseType = licenseView.getLicenseType();
            this.licenses = createLicenseListFromBom(licenseView.getLicenses());
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
            return licenseDisplay != null && (licenseUrl != null || licenseType != null);
        }

    }
}
