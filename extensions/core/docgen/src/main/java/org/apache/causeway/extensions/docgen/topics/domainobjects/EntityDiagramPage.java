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
package org.apache.causeway.extensions.docgen.topics.domainobjects;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.springframework.stereotype.Component;

import org.apache.causeway.core.config.beans.CausewayBeanTypeRegistry;
import org.apache.causeway.core.metamodel.spec.ObjectSpecification;
import org.apache.causeway.core.metamodel.specloader.SpecificationLoader;
import org.apache.causeway.extensions.docgen.CausewayModuleExtDocgen;
import org.apache.causeway.extensions.docgen.applib.HelpPage;
import org.apache.causeway.valuetypes.asciidoc.applib.value.AsciiDoc;

import lombok.RequiredArgsConstructor;

@Component
@Named(CausewayModuleExtDocgen.NAMESPACE + ".EntityDiagramPage")
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class EntityDiagramPage implements HelpPage {

    private final SpecificationLoader specLoader;
    private final CausewayBeanTypeRegistry beanTypeRegistry;

    @Override
    public String getTitle() {
        return "Entity Diagram";
    }

    @Override
    public AsciiDoc getContent() {
        return AsciiDoc.valueOf(
                "== Entities\n\n"
                + _DiagramUtils.plantumlBlock(entityTypesAsDiagram()));
    }

    // -- HELPER

    private String entityTypesAsDiagram() {
        return streamEntityTypes()
            .map(spec->_DiagramUtils.object(spec))
            .collect(Collectors.joining("\n"));

        //TODO add entity relations - that is, model the object graph
    }

    private Stream<ObjectSpecification> streamEntityTypes() {
        return beanTypeRegistry.getEntityTypes().keySet()
            .stream()
            .map(specLoader::specForType)
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

}

