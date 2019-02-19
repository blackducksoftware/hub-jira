/*
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
function readAdminData() {
    AJS.$.ajax({
        url: AJS.contextPath() + "/rest/blackduck-jira-integration/1.0/admin/",
        dataType: "json",
        success: function (admin) {
            fillInJiraGroups(admin.hubJiraGroups, admin.jiraGroups);

            handleError('hubJiraGroupsError', admin.hubJiraGroupsError, true, false);
        },
        error: function (response) {
            handleDataRetrievalError(response, "hubJiraGroupsError", "There was a problem retrieving the Admin configuration.", "Admin Error");
        },
        complete: function (jqXHR, textStatus) {
            console.log("Completed get of groups: " + textStatus);
        }
    });
}

function readIntervalData() {
    AJS.$.ajax({
        url: AJS.contextPath() + "/rest/blackduck-jira-integration/1.0/interval/",
        dataType: "json",
        success: function (config) {
            updateValue("intervalBetweenChecks", config.intervalBetweenChecks);

            handleError('generalSettingsError', config.generalSettingsError, true, false);
        },
        error: function (response) {
            handleDataRetrievalError(response, "generalSettingsError", "There was a problem retrieving the Interval.", "Interval Error");
        },
        complete: function (jqXHR, textStatus) {
            console.log("Completed get of interval: " + textStatus);
        }
    });
}

function fillInJiraGroups(hubJiraGroups, jiraGroups) {
    let splitHubJiraGroups = null;
    if (hubJiraGroups != null) {
        splitHubJiraGroups = hubJiraGroups.split(",");
    }
    const jiraGroupList = AJS.$("#" + hubJiraGroupsId);
    if (jiraGroups != null && jiraGroups.length > 0) {
        for (let j = 0; j < jiraGroups.length; j++) {
            let optionSelected = false;
            if (splitHubJiraGroups != null) {
                for (let g = 0; g < splitHubJiraGroups.length; g++) {
                    if (splitHubJiraGroups[g] === jiraGroups[j]) {
                        optionSelected = true;
                    }
                }
            }

            let newOption = AJS.$('<option>', {
                value: jiraGroups[j],
                text: jiraGroups[j],
                selected: optionSelected
            });

            jiraGroupList.append(newOption);
        }
    } else if (splitHubJiraGroups != null) {
        for (let j = 0; j < splitHubJiraGroups.length; j++) {
            let newOption = AJS.$('<option>', {
                value: splitHubJiraGroups[j],
                text: splitHubJiraGroups[j],
                selected: true
            });

            jiraGroupList.append(newOption);
        }
    }
    jiraGroupList.auiSelect2();
}

function readJiraProjects() {
    AJS.$.ajax({
        url: AJS.contextPath() + "/rest/blackduck-jira-integration/1.0/jiraProjects/",
        dataType: "json",
        success: function (config) {
            fillInJiraProjects(config.jiraProjects);

            handleError(jiraProjectListErrorId, config.jiraProjectsError, false, false);
            handleError(errorMessageFieldId, config.errorMessage, true, false);

            gotJiraProjects = true;
        },
        error: function (response) {
            handleDataRetrievalError(response, jiraProjectListErrorId, "There was a problem retrieving the JIRA Projects.", "JIRA Project Error");
        },
        complete: function (jqXHR, textStatus) {
            console.log("Completed get of JIRA projects: " + textStatus);
        }
    });
}

function fillInJiraProjects(jiraProjects) {
    const mappingElement = AJS.$("#" + hubProjectMappingElement);
    const jiraProjectList = mappingElement.find("datalist[id='" + jiraProjectListId + "']");
    if (jiraProjects != null && jiraProjects.length > 0) {
        for (let j = 0; j < jiraProjects.length; j++) {
            jiraProjectMap.set(String(jiraProjects[j].projectId), jiraProjects[j]);
            let newOption = AJS.$('<option>', {
                value: jiraProjects[j].projectName,
                projectKey: String(jiraProjects[j].projectId),
                projectError: jiraProjects[j].projectError
            });

            jiraProjectList.append(newOption);
        }
    }
}

function updateAccessConfig() {
    putAccessConfig(AJS.contextPath() + '/rest/blackduck-jira-integration/1.0/admin', 'Save successful.', 'The configuration is not valid.');
}

function putAccessConfig(restUrl, successMessage, failureMessage) {
    const hubJiraGroups = encodeURI(AJS.$("#" + hubJiraGroupsId).val());

    AJS.$.ajax({
        url: restUrl,
        type: "PUT",
        dataType: "json",
        contentType: "application/json",
        data: '{ "hubJiraGroups": "' + hubJiraGroups
            + '"}',
        processData: false,
        success: function () {
            hideError('hubJiraGroupsError');
            showStatusMessage(successStatus, 'Success!', successMessage);
            initCreatorCandidates();
        },
        error: function (response) {
            try {
                var admin = JSON.parse(response.responseText);
                handleError('hubJiraGroupsError', admin.hubJiraGroupsError, true, true);

                showStatusMessage(errorStatus, 'ERROR!', failureMessage);
            } catch (err) {
                // in case the response is not our error object
                alert(response.responseText);
            }
        },
        complete: function (jqXHR, textStatus) {
            stopProgressSpinner('adminSaveSpinner');
        }
    });
}


