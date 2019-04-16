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
package com.blackducksoftware.integration.jira.common.settings.model;

public class PluginIssueCreationConfigModel {
    private GeneralIssueCreationConfigModel general;
    private ProjectMappingConfigModel projectMapping;
    private TicketCriteriaConfigModel ticketCriteria;

    public PluginIssueCreationConfigModel(final GeneralIssueCreationConfigModel general, final ProjectMappingConfigModel projectMapping, final TicketCriteriaConfigModel ticketCriteria) {
        this.general = general;
        this.projectMapping = projectMapping;
        this.ticketCriteria = ticketCriteria;
    }

    public GeneralIssueCreationConfigModel getGeneral() {
        return general;
    }

    public ProjectMappingConfigModel getProjectMapping() {
        return projectMapping;
    }

    public TicketCriteriaConfigModel getTicketCriteria() {
        return ticketCriteria;
    }

}
