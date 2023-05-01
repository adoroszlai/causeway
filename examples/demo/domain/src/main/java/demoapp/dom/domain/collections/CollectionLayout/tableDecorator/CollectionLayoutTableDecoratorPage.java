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
package demoapp.dom.domain.collections.CollectionLayout.tableDecorator;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.causeway.applib.annotation.Collection;
import org.apache.causeway.applib.annotation.CollectionLayout;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.ObjectSupport;
import org.apache.causeway.applib.annotation.TableDecorator;

import demoapp.dom.domain.collections.CollectionLayout.sortedBy.CollectionLayoutSortedByPage;
import demoapp.dom.domain.collections.CollectionLayout.sortedBy.child.CollectionLayoutSortedByChildVm;

import demoapp.dom.domain.collections.CollectionLayout.tableDecorator.child.CollectionLayoutTableDecoratorChildVm;

import lombok.Getter;
import lombok.Setter;

import demoapp.dom._infra.asciidocdesc.HasAsciiDocDescription;
import demoapp.dom.domain._entities.DemoEntity;

//tag::class[]
@Named("demo.CollectionLayoutTableDecoratorPage")
@DomainObject(nature=Nature.VIEW_MODEL)
@XmlRootElement(name = "root")
@XmlType
@XmlAccessorType(XmlAccessType.FIELD)
public class CollectionLayoutTableDecoratorPage implements HasAsciiDocDescription {

    @ObjectSupport public String title() {
        return "@CollectionLayout#tableDecorator";
    }

//tag::children[]
    @Collection()
    @CollectionLayout(
            tableDecorator = TableDecorator.DatatablesNet.class     // <.>
    )
    @XmlElementWrapper(name = "children")
    @XmlElement(name = "child")
    @Getter @Setter
    private List<CollectionLayoutTableDecoratorChildVm> children = new ArrayList<>();
//end::children[]

}
//end::class[]
