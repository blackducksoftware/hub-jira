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
package com.blackducksoftware.integration.jira.issue.ui;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.customfields.CustomFieldUtils;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldException;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.NavigableField;
import com.blackducksoftware.integration.jira.common.BlackDuckJiraConstants;
import com.blackducksoftware.integration.jira.common.exception.JiraException;
import com.blackducksoftware.integration.jira.web.model.Fields;
import com.blackducksoftware.integration.jira.web.model.IdToNameMapping;

public class JiraFieldUtils {
    private static final Logger logger = LoggerFactory.getLogger(JiraFieldUtils.class);

    public static Fields getTargetFields(final FieldManager fieldManager) throws JiraException {
        final Fields targetFields = new Fields();
        retrieveIdToNameMapping(fieldManager, BlackDuckJiraConstants.COMPONENTS_FIELD_ID).ifPresent(targetFields::add);
        retrieveIdToNameMapping(fieldManager, BlackDuckJiraConstants.VERSIONS_FIELD_ID).ifPresent(targetFields::add);
        addNonBdsCustomFields(fieldManager, targetFields);
        logger.debug("targetFields: " + targetFields);
        return targetFields;
    }

    private static void addNonBdsCustomFields(final FieldManager fieldManager, final Fields targetFields) throws JiraException {
        final Set<NavigableField> navFields;
        try {
            navFields = fieldManager.getAllAvailableNavigableFields();
        } catch (final FieldException e) {
            final String msg = "Error getting JIRA fields: " + e.getMessage();
            logger.error(msg, e);
            throw new JiraException(msg, e);
        }
        for (final NavigableField field : navFields) {
            if (field.getId().startsWith(CustomFieldUtils.CUSTOM_FIELD_PREFIX)) {
                logger.debug("Found custom field: Id: " + field.getId() + "; Name: " + field.getName() + "; nameKey: " + field.getNameKey());
                if (!isBdsCustomField(field)) {
                    targetFields.add(new IdToNameMapping(field.getId(), field.getName()));
                } else {
                    logger.debug("This is a BDS field; omitting it");
                }
            } else {
                logger.debug("Field with ID " + field.getId() + " is not a custom field");
            }
        }
    }

    private static boolean isBdsCustomField(final Field field) {
        // @formatter:off
        return BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_PROJECT.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_PROJECT_VERSION.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_PROJECT_VERSION_URL.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_PROJECT_VERSION_NICKNAME.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_PROJECT_OWNER.equals(field.getName())

            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT_URL.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT_VERSION.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT_VERSION_URL.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT_ORIGIN_ID.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT_ORIGIN.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT_USAGE.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_COMPONENT_REVIEWER.equals(field.getName())

            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_PROJECT_VERSION_LAST_UPDATED.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_LICENSE_NAMES.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_LICENSE_URL.equals(field.getName())

            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_POLICY_RULE.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_POLICY_RULE_URL.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_POLICY_RULE_OVERRIDABLE.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_POLICY_RULE_DESCRIPTION.equals(field.getName())
            || BlackDuckJiraConstants.BLACKDUCK_CUSTOM_FIELD_POLICY_RULE_SEVERITY.equals(field.getName())
         ;
         // @formatter:on
    }

    private static Optional<IdToNameMapping> retrieveIdToNameMapping(FieldManager fieldManager, String jiraConstants) {
        final Field versionsField = fieldManager.getField(jiraConstants);
        if (versionsField == null) {
            logger.error("Error getting field (field id: " + jiraConstants + ") for field copy target field list");
            return Optional.empty();
        }

        return Optional.of(new IdToNameMapping(jiraConstants, versionsField.getName()));
    }
}
