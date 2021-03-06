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
package com.blackducksoftware.integration.jira.web.model;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class IdToNameMapping implements Serializable, Comparable<IdToNameMapping> {
    private static final long serialVersionUID = -6879420109287472484L;

    @XmlElement
    private String id;

    @XmlElement
    private String name;

    public IdToNameMapping() {
    }

    public IdToNameMapping(final String id, final String name) {
        super();
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(final IdToNameMapping idToNameMapping) {
        if ((idToNameMapping == null) || (idToNameMapping.getName() == null)) {
            return 1;
        }

        if (getName() == null) {
            return -1;
        }

        return getName().compareTo(idToNameMapping.getName());
    }

    @Override
    public String toString() {
        return "IdToNameMapping [id=" + id + ", name=" + name + "]";
    }
}
