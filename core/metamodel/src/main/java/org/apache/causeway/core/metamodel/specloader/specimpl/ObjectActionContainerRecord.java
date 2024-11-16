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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.causeway.applib.Identifier;
import org.apache.causeway.commons.collections.ImmutableEnumSet;
import org.apache.causeway.commons.internal.base._NullSafe;
import org.apache.causeway.commons.internal.base._Oneshot;
import org.apache.causeway.commons.internal.base._Strings;
import org.apache.causeway.commons.internal.collections._Multimaps;
import org.apache.causeway.commons.internal.collections._Multimaps.ListMultimap;
import org.apache.causeway.commons.internal.collections._Sets;
import org.apache.causeway.core.config.beans.CausewayBeanTypeRegistry;
import org.apache.causeway.core.metamodel.spec.ActionScope;
import org.apache.causeway.core.metamodel.spec.IntrospectionState;
import org.apache.causeway.core.metamodel.spec.ObjectSpecification;
import org.apache.causeway.core.metamodel.spec.feature.MixedIn;
import org.apache.causeway.core.metamodel.spec.feature.ObjectAction;
import org.apache.causeway.core.metamodel.spec.feature.ObjectActionContainer;
import org.apache.causeway.core.metamodel.specloader.facetprocessor.FacetProcessor;

public record ObjectActionContainerRecord(
        ObjectSpecification self,
        /**
         * Get the specification for this specification's class's superclass.
         */
        Supplier<ObjectSpecification> superclassSupplier,
        List<ObjectAction> regularActions,
        List<ObjectAction> mixedinActions,
        // partitions and caches objectActions by type; updated in sortCacheAndUpdateActions()
        ListMultimap<ActionScope, ObjectAction> objectActionsByType,
        _Oneshot mixedInActionAdder,
        FacetProcessor facetProcessor) 
implements ObjectActionContainer {
    
    public ObjectActionContainerRecord(
            ObjectSpecification self,
            Supplier<ObjectSpecification> superclassSupplier,
            FacetProcessor facetProcessor) {
        this(self, superclassSupplier, 
                new ArrayList<>(), new ArrayList<>(), _Multimaps.newListMultimap(),
                new _Oneshot(), facetProcessor);
    }
    
    public boolean isTypeHierarchyRoot() {
        return superclassSupplier().get()==null;
    }
    
    public Stream<ObjectAction> stream(
            final ImmutableEnumSet<ActionScope> actionScopes,
            final MixedIn mixedIn) {
        return actionScopes.stream()
                .flatMap(actionScope->_NullSafe.stream(objectActionsByType.get(actionScope)))
                .filter(mixedIn.toFilter());
    }

    @Override
    public final Optional<ObjectAction> getAction(
            final String id, final ImmutableEnumSet<ActionScope> scopes, final MixedIn mixedIn) {

        var declaredAction = getDeclaredAction(id, mixedIn); // no inheritance nor type considered

        if(declaredAction.isPresent()) {
            // action found but if its not the right type, stop searching
            if(!scopes.contains(declaredAction.get().getScope())) {
                return Optional.empty();
            }
            return declaredAction;
        }

        return isTypeHierarchyRoot()
                ? Optional.empty() // stop searching
                : superclassSupplier().get().getAction(id, scopes, mixedIn);
    }

    @Override
    public final Stream<ObjectAction> streamActions(
            final ImmutableEnumSet<ActionScope> actionTypes,
            final MixedIn mixedIn,
            final Consumer<ObjectAction> onActionOverloaded) {

        var actionStream = isTypeHierarchyRoot()
                ? streamDeclaredActions(actionTypes, mixedIn) // stop going deeper
                : Stream.concat(
                        streamDeclaredActions(actionTypes, mixedIn),
                        superclassSupplier().get().streamActions(actionTypes, mixedIn));

        var actionSignatures = _Sets.<String>newHashSet();
        var actionIds = _Sets.<String>newHashSet();

        return actionStream

            // as of contributing super-classes same actions might appear more than once (overriding)
            .filter(action->{
                if(action.isMixedIn()) {
                    return true; // do not filter mixedIn actions based on signature
                }
                var isUnique = actionSignatures
                        .add(action.getFeatureIdentifier().getMemberNameAndParameterClassNamesIdentityString());
                return isUnique;
            })
    
            // ensure we don't emit duplicates
            .filter(action->{
                var isUnique = actionIds.add(action.getId());
                if(!isUnique) {
                    onActionOverloaded.accept(action);
                }
                return isUnique;
            });
    }

    @Override
    public Stream<ObjectAction> streamDeclaredActions(ImmutableEnumSet<ActionScope> actionScopes, MixedIn mixedIn) {
        self.introspectUpTo(IntrospectionState.FULLY_INTROSPECTED);
        mixedInActionAdder.trigger(this::createMixedInActionsAndResort);
        return stream(actionScopes, mixedIn);
    }
    /**
     * one-shot: must be no-op, if already created
     */
    private void createMixedInActionsAndResort() {
        var include = self.isEntityOrViewModelOrAbstract()
                || self.getBeanSort().isManagedBeanContributing()
                // in support of composite value-type constructor mixins
                || self.getBeanSort().isValue();
        if(!include) return;
        addMixedinActions(createMixedInActions());
    }
    /**
     * Creates all mixed in actions for this spec.
     */
    private Stream<ObjectActionMixedIn> createMixedInActions() {
        return self.getServiceRegistry()
                .lookupServiceElseFail(CausewayBeanTypeRegistry.class)
                .streamMixinTypes()
            .flatMap(this::createMixedInAction);
    }
    @Deprecated //REFACTOR: remove circular dependency specloader<->objecspec
    private Stream<ObjectActionMixedIn> createMixedInAction(final Class<?> mixinType) {
        var mixinSpec = self.getSpecificationLoader().loadSpecification(mixinType,
                IntrospectionState.FULLY_INTROSPECTED);
        return createMixedInAction(mixinSpec);
    }
    private Stream<ObjectActionMixedIn> createMixedInAction(final ObjectSpecification mixinSpec) {
        if (mixinSpec == null
                || mixinSpec == self) {
            return Stream.empty();
        }
        var mixinFacet = mixinSpec.mixinFacet().orElse(null);
        if(mixinFacet == null) {
            // this shouldn't happen; to be covered by meta-model validation later
            return Stream.empty();
        }
        if(!mixinFacet.isMixinFor(self.getCorrespondingClass())) {
            return Stream.empty();
        }
        // don't mixin Object_ mixins to domain services
        if(self.getBeanSort().isManagedBeanContributing()
                && mixinFacet.isMixinFor(java.lang.Object.class)) {
            return Stream.empty();
        }

        var mixinMethodName = mixinFacet.getMainMethodName();

        return mixinSpec.streamActions(ActionScope.ANY, MixedIn.EXCLUDED)
        // value types only support constructor mixins
        .filter(this::whenIsValueThenIsAlsoConstructorMixin)
        .filter(_SpecPredicates::isMixedInAction)
        .map(ObjectActionDefault.class::cast)
        .map(_MixedInMemberFactory.mixedInAction(self, mixinSpec, mixinMethodName))
        .peek(facetProcessor::processMemberOrder);
    }
    /**
     * Whether the mixin's main method returns an instance of type equal to the mixee's type.
     * <p>
     * Introduced to support constructor mixins for value-types and
     * also to support associated <i>Actions</i> for <i>Action Parameters</i>.
     */
    private boolean whenIsValueThenIsAlsoConstructorMixin(final ObjectAction act) {
        return self.getBeanSort().isValue()
                ? Objects.equals(self, act.getReturnType())
                : true;
    }
    
    @Override
    public Optional<ObjectAction> getDeclaredAction(String id, ImmutableEnumSet<ActionScope> actionScopes,
            MixedIn mixedIn) {
        self.introspectUpTo(IntrospectionState.FULLY_INTROSPECTED);

        return _Strings.isEmpty(id)
            ? Optional.empty()
            : streamDeclaredActions(actionScopes, mixedIn)
                .filter(action->
                    id.equals(action.getFeatureIdentifier().getMemberNameAndParameterClassNamesIdentityString())
                            || id.equals(action.getFeatureIdentifier().getMemberLogicalName())
                )
                .findFirst();
    }

    @Override
    public Stream<ObjectAction> streamRuntimeActions(MixedIn mixedIn) {
        var actionScopes = ActionScope.forEnvironment(self.getSystemEnvironment());
        return streamActions(actionScopes, mixedIn);
    }

    @Override
    public Stream<ObjectAction> streamActionsForColumnRendering(Identifier memberIdentifier) {
        // TODO Auto-generated method stub
        return null;
    }

    public void rebuild(List<ObjectAction> orderedActions) {
        for (var actionType : ActionScope.values()) {
            var objectActionForType = objectActionsByType.getOrElseNew(actionType);
            objectActionForType.clear();
            orderedActions.stream()
            .filter(ObjectAction.Predicates.ofActionType(actionType))
            .forEach(objectActionForType::add);
        }
    }

    public void addRegularActions(Stream<ObjectAction> actions) {
        regularActions.clear();
        actions.forEach(regularActions::add);
    }
    
    public void addMixedinActions(Stream<ObjectActionMixedIn> actions) {
        mixedinActions.clear();
        actions.forEach(mixedinActions::add);
        
        if(mixedinActions.isEmpty()) return;

        // note: we are doing this before any member sorting
        _MemberIdClashReporting.flagAnyMemberIdClashes(self, regularActions, mixedinActions);
    }
    
}
