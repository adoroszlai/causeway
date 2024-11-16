/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.causeway.core.metamodel.specloader.specimpl;

import java.util.ArrayList;
import java.util.List;

import org.apache.causeway.commons.collections.Can;
import org.apache.causeway.core.metamodel.spec.feature.ObjectAction;
import org.apache.causeway.core.metamodel.spec.feature.ObjectAssociation;

record ObjectSpecificationBody(
        List<ObjectAction> objectActions,
        List<ObjectAssociation> associations
        ) {
    
    ObjectSpecificationBody() {
        this(new ArrayList<>(), new ArrayList<>());
    }
    
    Can<ObjectAction> snapshotActions() {
        return Can.ofCollection(objectActions);
    }

    Can<ObjectAssociation> snapshotAssociations() {
        return Can.ofCollection(associations);
    }
    
    //synchronized by caller
    void replaceActions(List<ObjectAction> orderedActions) {
        objectActions.clear();
        objectActions.addAll(orderedActions);
    }
    
  //synchronized by caller
    void replaceAssociations(List<ObjectAssociation> orderedAssociations) {
        associations.clear();
        associations.addAll(orderedAssociations);
    }
}
