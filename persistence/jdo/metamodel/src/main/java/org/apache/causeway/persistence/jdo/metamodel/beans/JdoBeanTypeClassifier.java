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
package org.apache.causeway.persistence.jdo.metamodel.beans;

import javax.jdo.annotations.EmbeddedOnly;

import org.apache.causeway.applib.id.LogicalType;
import org.apache.causeway.commons.internal.annotations.BeanInternal;
import org.apache.causeway.commons.internal.reflection._Annotations;
import org.apache.causeway.core.config.beans.CausewayBeanMetaData;
import org.apache.causeway.core.config.beans.CausewayBeanTypeClassifierSpi;
import org.apache.causeway.core.config.beans.PersistenceStack;

/**
 * ServiceLoader plugin, classifies PersistenceCapable types into BeanSort.ENTITY.
 * @since 2.0
 */
@BeanInternal
public class JdoBeanTypeClassifier implements CausewayBeanTypeClassifierSpi {
    
    @Override
    public CausewayBeanMetaData classify(final LogicalType logicalType) {
        
        var type = logicalType.getCorrespondingClass();

        var persistenceCapableAnnotOpt = _Annotations
                .synthesize(type, javax.jdo.annotations.PersistenceCapable.class);
        if(!persistenceCapableAnnotOpt.isPresent()) return null; // we don't see fit to classify given type
            
        var persistenceCapableAnnot = persistenceCapableAnnotOpt.orElseThrow();

        var embeddedOnlyAttribute = persistenceCapableAnnot.embeddedOnly();
        // Whether objects of this type can only be embedded,
        // hence have no ID that binds them to the persistence layer
        final boolean embeddedOnly = Boolean.valueOf(embeddedOnlyAttribute)
                || _Annotations.synthesize(type, EmbeddedOnly.class).isPresent();
        if(embeddedOnly) return null; // don't categorize as entity ... fall through in the caller's logic

        return CausewayBeanMetaData.entity(PersistenceStack.JDO, logicalType);
    }

}
