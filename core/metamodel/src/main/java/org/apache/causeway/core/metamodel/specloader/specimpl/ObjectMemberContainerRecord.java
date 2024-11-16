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
import java.util.function.Supplier;

import org.apache.causeway.commons.collections.Can;
import org.apache.causeway.core.metamodel.spec.ObjectSpecification;
import org.apache.causeway.core.metamodel.spec.feature.ObjectAssociation;
import org.apache.causeway.core.metamodel.specloader.facetprocessor.FacetProcessor;

public record ObjectMemberContainerRecord(
        ObjectSpecification self,
        ObjectActionContainerRecord actions,
        List<ObjectAssociation> associations
        ) {
    
    ObjectMemberContainerRecord(
            ObjectSpecification self,
            Supplier<ObjectSpecification> superclassSupplier,
            FacetProcessor facetProcessor) {
        this(self, 
                new ObjectActionContainerRecord(self, superclassSupplier, facetProcessor), 
                new ArrayList<>());
    }

    Can<ObjectAssociation> snapshotAssociations() {
        return Can.ofCollection(associations);
    }
    
  //synchronized by caller
    void replaceAssociations(List<ObjectAssociation> orderedAssociations) {
        associations.clear();
        associations.addAll(orderedAssociations);
    }

}
