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
package org.apache.causeway.core.metamodel.spec;

import java.util.List;

import org.springframework.util.ClassUtils;

import org.apache.causeway.commons.collections.Can;
import org.apache.causeway.commons.internal.base._Lazy;
import org.apache.causeway.commons.internal.collections._Lists;
import org.apache.causeway.core.metamodel.services.classsubstitutor.ClassSubstitutorRegistry;
import org.apache.causeway.core.metamodel.specloader.specimpl.ObjectSpecificationAbstract;

public record HierarchicalRecord(
        ObjectSpecification self,
        _Lazy<ObjectSpecification> superclassLazy,
        _Lazy<Can<ObjectSpecification>> interfacesLazy,
        Subclasses directSubclasses,
        // built lazily
        _Lazy<Subclasses> transitiveSubclassesLazy
        ) implements Hierarchical {
    
    /**
     * @implNote thread-safe
     */
    public static class Subclasses {

        // List performs better compared to a Set, when the number of elements is low
        private Can<ObjectSpecification> classes = Can.empty();
        private final Object $lock = new Object();

        public void addSubclass(final ObjectSpecification subclass) {
            synchronized($lock) {
                classes = classes.addUnique(subclass);
            }
        }

        public boolean hasSubclasses() {
            synchronized($lock) {
                return classes.isNotEmpty();
            }
        }

        public Can<ObjectSpecification> snapshot() {
            synchronized($lock) {
                return classes;
            }
        }
    }
    
    public HierarchicalRecord(ObjectSpecification self) {
        this(self, 
                _Lazy.threadSafe(()->populateSuperclass(self)), 
                _Lazy.threadSafe(()->populateInterfaces(self)), 
                new Subclasses(), 
                _Lazy.threadSafe(()->populateTransitiveSubclasses(self)));
    }

    private static ObjectSpecification populateSuperclass(ObjectSpecification self) {
        final Class<?> superclass = self.getCorrespondingClass().getSuperclass();
        if (superclass == null) return null;
        var superclassSpec = self.getSpecificationLoader()
                .loadSpecification(superclass, IntrospectionState.NOT_INTROSPECTED);
        if (superclassSpec != null) {
            updateAsSubclassTo(self, superclassSpec);
        }
        return superclassSpec;
    }
    private static void updateAsSubclassTo(
            final ObjectSpecification self, 
            final List<ObjectSpecification> supertypeSpecs) {
        for (final ObjectSpecification supertypeSpec : supertypeSpecs) {
            updateAsSubclassTo(self, supertypeSpec);
        }
    }
    private static void updateAsSubclassTo(
            final ObjectSpecification self, 
            final ObjectSpecification supertypeSpec) {
        if (supertypeSpec instanceof ObjectSpecificationAbstract introspectableSpec) {
            introspectableSpec.hierarchy.directSubclasses().addSubclass(self);    
        }
    }
    
    private static Can<ObjectSpecification> populateInterfaces(ObjectSpecification self) {
        final Class<?>[] interfaceTypes = self.getCorrespondingClass().getInterfaces();
        final List<ObjectSpecification> interfaceSpecs = _Lists.newArrayList();
        
        var classSubstitutorRegistry = self
                .getServiceRegistry().lookupServiceElseFail(ClassSubstitutorRegistry.class);
        
        for (var interfaceType : interfaceTypes) {
            var interfaceSubstitute = classSubstitutorRegistry.getSubstitution(interfaceType);
            if (interfaceSubstitute.isReplace()) {
                var interfaceSpec = self.getSpecificationLoader().loadSpecification(interfaceSubstitute.getReplacement());
                interfaceSpecs.add(interfaceSpec);
            }
        }
        updateAsSubclassTo(self, interfaceSpecs);
        return Can.ofCollection(interfaceSpecs);
    }
    
    private static Subclasses populateTransitiveSubclasses(ObjectSpecification self) {
        final Subclasses appendTo = new Subclasses();
        appendSubclasses(self, appendTo);
        Subclasses transitiveSubclasses = appendTo;
        return transitiveSubclasses;
    }
    
    private static void appendSubclasses(
            final ObjectSpecification objectSpecification,
            final Subclasses appendTo) {
        var directSubclasses = objectSpecification.subclasses(Depth.DIRECT);
        for (ObjectSpecification subclass : directSubclasses) {
            appendTo.addSubclass(subclass);
            appendSubclasses(subclass, appendTo);
        }
    }
    
    public final Class<?> getCorrespondingClass() {
        return self.getCorrespondingClass();
    }
    
    @Override
    public ObjectSpecification superclass() {
        return superclassLazy.get();
    }
    
    @Override
    public boolean hasSubclasses() {
        return directSubclasses.hasSubclasses();
    }
    
    @Override
    public Can<ObjectSpecification> subclasses(Depth depth) {
        return depth == Depth.DIRECT
                ? directSubclasses().snapshot()
                : transitiveSubclassesLazy().get().snapshot();
    }

    @Override
    public boolean isOfType(ObjectSpecification other) {
        var thisClass = this.getCorrespondingClass();
        var otherClass = other.getCorrespondingClass();

        return thisClass == otherClass
                || otherClass.isAssignableFrom(thisClass);
    }

    @Override
    public boolean isOfTypeResolvePrimitive(ObjectSpecification other) {
        var thisClass = ClassUtils.resolvePrimitiveIfNecessary(this.getCorrespondingClass());
        var otherClass = ClassUtils.resolvePrimitiveIfNecessary(other.getCorrespondingClass());

        return thisClass == otherClass
                || otherClass.isAssignableFrom(thisClass);
    }
    
    @Override
    public Can<ObjectSpecification> interfaces() {
        return interfacesLazy.get(); 
    }
    
}
