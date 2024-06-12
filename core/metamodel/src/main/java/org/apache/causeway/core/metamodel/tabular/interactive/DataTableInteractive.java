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
package org.apache.causeway.core.metamodel.tabular.interactive;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.springframework.lang.Nullable;

import org.apache.causeway.applib.Identifier;
import org.apache.causeway.applib.annotation.TableDecorator;
import org.apache.causeway.applib.annotation.Where;
import org.apache.causeway.applib.services.bookmark.Bookmark;
import org.apache.causeway.applib.services.search.CollectionSearchService;
import org.apache.causeway.commons.collections.Can;
import org.apache.causeway.commons.internal.base._Casts;
import org.apache.causeway.commons.internal.binding._BindableAbstract;
import org.apache.causeway.commons.internal.binding._Bindables;
import org.apache.causeway.commons.internal.binding._Observables;
import org.apache.causeway.commons.internal.binding._Observables.LazyObservable;
import org.apache.causeway.commons.internal.collections._Maps;
import org.apache.causeway.commons.internal.exceptions._Exceptions;
import org.apache.causeway.core.metamodel.consent.InteractionInitiatedBy;
import org.apache.causeway.core.metamodel.consent.InteractionResult;
import org.apache.causeway.core.metamodel.context.MetaModelContext;
import org.apache.causeway.core.metamodel.interactions.InteractionHead;
import org.apache.causeway.core.metamodel.interactions.InteractionUtils;
import org.apache.causeway.core.metamodel.interactions.ObjectVisibilityContext;
import org.apache.causeway.core.metamodel.interactions.VisibilityContext;
import org.apache.causeway.core.metamodel.interactions.managed.ActionInteraction;
import org.apache.causeway.core.metamodel.interactions.managed.CollectionInteraction;
import org.apache.causeway.core.metamodel.interactions.managed.ManagedAction;
import org.apache.causeway.core.metamodel.interactions.managed.ManagedCollection;
import org.apache.causeway.core.metamodel.interactions.managed.ManagedMember;
import org.apache.causeway.core.metamodel.interactions.managed.MultiselectChoices;
import org.apache.causeway.core.metamodel.object.ManagedObject;
import org.apache.causeway.core.metamodel.object.MmSortUtils;
import org.apache.causeway.core.metamodel.object.PackedManagedObject;
import org.apache.causeway.core.metamodel.spec.ObjectSpecification;
import org.apache.causeway.core.metamodel.spec.feature.ObjectMember;
import org.apache.causeway.core.metamodel.tabular.simple.DataTable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

public class DataTableInteractive
implements MultiselectChoices {

    // -- FACTORIES

    public static DataTableInteractive empty(final ManagedMember managedMember, final Where where) {
        return new DataTableInteractive(managedMember, where, Can.empty());
    }

    public static DataTableInteractive forCollection(
            final ManagedCollection managedCollection) {
        return new DataTableInteractive(managedCollection, managedCollection.getWhere(),
            managedCollection
            .streamElements()
            .collect(Can.toCan()));
    }

    public static DataTableInteractive forAction(
            final ManagedAction managedAction,
            final Can<ManagedObject> args,
            final ManagedObject actionResult) {

        if(actionResult==null) {
            new DataTableInteractive(managedAction, managedAction.getWhere(), Can.empty());
        }
        if(!(actionResult instanceof PackedManagedObject)) {
            throw _Exceptions.unexpectedCodeReach();
        }

        val elements = ((PackedManagedObject)actionResult).unpack();
        elements.forEach(ManagedObject::getBookmark);

        return new DataTableInteractive(managedAction, managedAction.getWhere(), elements);
    }

    // -- CONSTRUCTION

    // as this is a layer of abstraction, don't expose via getter
    final @NonNull ManagedMember managedMember;
    final @NonNull Where where;

    @Getter private final @NonNull LazyObservable<Can<ManagedObject>> dataElements;
    @Getter private final @NonNull _BindableAbstract<String> searchArgument; // filter the data rows
    @Getter private final @NonNull LazyObservable<Can<DataRow>> dataRowsFiltered;
    @Getter private final @NonNull LazyObservable<Can<DataRow>> dataRowsSelected;
    @Getter private final _BindableAbstract<Boolean> selectAllToggle;
    @Getter private final _BindableAbstract<ColumnSort> columnSort;

    @Getter private final @NonNull LazyObservable<Can<DataColumn>> dataColumns;
    @Getter private final @NonNull LazyObservable<String> title;

    private final @Nullable BiPredicate<Object, String> searchPredicate;

    private DataTableInteractive(
            // we need access to the owner in support of imperative title and referenced column detection
            final ManagedMember managedMember,
            final Where where,
            final Can<ManagedObject> elements) {

        val mmc = MetaModelContext.instanceElseFail();

        this.managedMember = managedMember;
        this.where = where;
        this.searchPredicate = _Casts.uncheckedCast(
                mmc.lookupService(CollectionSearchService.class)
                .flatMap(collectionSearchService->collectionSearchService
                        .searchPredicate(managedMember.getElementType().getCorrespondingClass()))
                .orElse(null));

        dataElements = _Observables.lazy(()->elements.map(
            mmc::injectServicesInto));

        searchArgument = _Bindables.forValue(null);
        columnSort = _Bindables.forValue(null);

        dataRowsFiltered = _Observables.lazy(()->
            dataElements.getValue().stream()
                //XXX future extension: filter by searchArgument
                .filter(this::ignoreHidden)
                .filter(adaptSearchPredicate())
                .sorted(sortingComparator()
                        .orElseGet(()->(a, b)->0)) // else don't sort (no-op comparator for streams)
                .map(domainObject->new DataRow(this, domainObject))
                .collect(Can.toCan()));

        dataRowsSelected = _Observables.lazy(()->
            dataRowsFiltered.getValue().stream()
            .filter(dataRow->dataRow.getSelectToggle().getValue().booleanValue())
            .collect(Can.toCan()));

        selectAllToggle = _Bindables.forValue(Boolean.FALSE);
        selectAllToggle.addListener((e,o,isAllOn)->{
            //_Debug.onClearToggleAll(o, isAllOn, isClearToggleAllEvent.get());
            if(isClearToggleAllEvent.get()) {
                return;
            }
            dataRowsSelected.invalidate();
            try {
                isToggleAllEvent.set(true);
                dataRowsFiltered.getValue().forEach(dataRow->dataRow.getSelectToggle().setValue(isAllOn));
            } finally {
                isToggleAllEvent.set(false);
            }
        });

        searchArgument.addListener((e,o,n)->{
            dataRowsFiltered.invalidate();
            dataRowsSelected.invalidate();
        });

        columnSort.addListener((e,o,n)->{
            dataRowsFiltered.invalidate();
        });

        dataColumns = _Observables.lazy(()->
            managedMember.getElementType()
            .streamAssociationsForColumnRendering(managedMember.getIdentifier(), managedMember.getOwner())
            .map(assoc->new DataColumn(this, assoc))
            .collect(Can.toCan()));

        //XXX future extension: the title could dynamically reflect the number of elements selected
        //eg... 5 Orders selected
        title = _Observables.lazy(()->
            managedMember
            .getFriendlyName());
    }

    public boolean isSearchSupported() {
        return searchPredicate!=null;
    }

    public int getPageSize(final int pageSizeDefault) {
        return getMetaModel().getPageSize().orElse(pageSizeDefault);
    }

    public Optional<TableDecorator> getTableDecoratorIfAny() {
        return getMetaModel().getTableDecorator();
    }

    /**
     * Count filtered data rows.
     */
    public int getElementCount() {
        return dataRowsFiltered.getValue().size();
    }

    public ObjectMember getMetaModel() {
        return managedMember.getMetaModel();
    }

    public ObjectSpecification getElementType() {
        return getMetaModel().getElementType();
    }

    private final Map<UUID, Optional<DataRow>> dataRowByUuidLookupCache = _Maps.newConcurrentHashMap();
    public Optional<DataRow> lookupDataRow(final @NonNull UUID uuid) {
        // lookup can be safely cached
        return dataRowByUuidLookupCache.computeIfAbsent(uuid, __->getDataRowsFiltered().getValue().stream()
                .filter(dr->dr.getUuid().equals(uuid))
                .findFirst());
    }

    // -- SEARCH

    private Predicate<? super ManagedObject> adaptSearchPredicate() {
        return searchPredicate==null
                ? managedObject->true
                : managedObject->searchPredicate
                    .test(managedObject.getPojo(), searchArgument.getValue());
    }

    // -- SORTING

    /**
     * Sorting helper class, that has the column index to be sorted by and the sort direction.
     */
    @RequiredArgsConstructor
    public static class ColumnSort implements Serializable {
        private static final long serialVersionUID = 1L;
        final int columnIndex;
        final MmSortUtils.SortDirection sortDirection;
        Optional<Comparator<ManagedObject>> asComparator(final Can<DataColumn> columns) {
            val columnToSort = columns.get(columnIndex).orElse(null);
            val sortProperty = columnToSort.getAssociationMetaModel().getSpecialization().leftIfAny();
            return Optional.ofNullable(sortProperty)
                    .map(prop->MmSortUtils.orderingBy(sortProperty, sortDirection));
        }
    }

    private Optional<Comparator<ManagedObject>> sortingComparator() {
        return Optional.ofNullable(columnSort.getValue())
                .flatMap(sort->sort.asComparator(dataColumns.getValue()))
                .or(()->managedMember.getMetaModel().getElementComparator());
    }

    // -- TOGGLE ALL

    final AtomicBoolean isToggleAllEvent = new AtomicBoolean();
    private final AtomicBoolean isClearToggleAllEvent = new AtomicBoolean();
    public void clearToggleAll() {
        try {
            isClearToggleAllEvent.set(true);
            selectAllToggle.setValue(Boolean.FALSE);
        } finally {
            isClearToggleAllEvent.set(false);
        }
    }

    // -- DATA ROW VISIBILITY

    private boolean ignoreHidden(final ManagedObject adapter) {
        final InteractionResult visibleResult =
                InteractionUtils.isVisibleResult(
                        adapter.getSpecification(),
                        createVisibleInteractionContext(adapter));
        return visibleResult.isNotVetoing();
    }

    private VisibilityContext createVisibleInteractionContext(final ManagedObject objectAdapter) {
        return new ObjectVisibilityContext(
                InteractionHead.regular(objectAdapter),
                objectAdapter.getSpecification().getFeatureIdentifier(),
                InteractionInitiatedBy.USER,
                Where.ALL_TABLES);
    }

    // -- ASSOCIATED ACTION WITH MULTI SELECT

    @Override
    public Can<ManagedObject> getSelected() {
      return getDataRowsSelected()
                .getValue()
                .map(DataRow::getRowElement);
    }

    public ActionInteraction startAssociatedActionInteraction(final String actionId, final Where where) {
        val featureId = managedMember.getIdentifier();
        if(!featureId.getType().isPropertyOrCollection()) {
            return ActionInteraction.empty(String.format("[no such collection %s; instead got %s;"
                    + "(while searching for an associated action %s)]",
                    featureId,
                    featureId.getType(),
                    actionId));
        }
        return ActionInteraction.startWithMultiselect(managedMember.getOwner(), actionId, where, this);
    }

    // -- EXPORT

    public DataTable export() {
        return new DataTable(
                getElementType(),
                getTitle().getValue(),
                getDataColumns().getValue()
                    .map(DataColumn::getAssociationMetaModel),
                getDataRowsFiltered().getValue()
                    .stream()
                    .map(dr->dr.getRowElement())
                    .collect(Can.toCan()));
    }

    // used internally for serialization
    private DataTable exportAll() {
        return new DataTable(
                getElementType(),
                getTitle().getValue(),
                getDataColumns().getValue()
                    .map(DataColumn::getAssociationMetaModel),
                getDataElements().getValue());
    }

    // -- MEMENTO

    public Memento getMemento() {
        return Memento.create(this);
    }

    /**
     * Recreation from given 'bookmarkable' {@link ManagedObject} (owner),
     * without triggering domain events.
     * Either originates from a <i>Collection</i> or an <i>Action</i>'s
     * non-scalar result.
     * <p>
     * In the <i>Action</i> case, requires the <i>Action</i>'s arguments
     * for reconstruction.
     * <p>
     * Responsibility for recreation of the owner is with the caller
     * to allow for simpler object graph reconstruction (shared owner).
     * <p>
     * However, we keep track of the argument list here.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Memento implements Serializable {
        private static final long serialVersionUID = 1L;

        static Memento create(
                final @Nullable DataTableInteractive tableInteractive) {
            return new Memento(
                    tableInteractive.managedMember.getIdentifier(),
                    tableInteractive.where,
                    tableInteractive.exportAll(),
                    tableInteractive.searchArgument.getValue(),
                    Can.empty() //FIXME - flesh out
                    );
        }

        private final @NonNull Identifier featureId;
        private final @NonNull Where where;
        private final @NonNull DataTable dataTable;
        private final @Nullable String searchArgument;
        private final @Nullable Can<Bookmark> selected;

        public DataTableInteractive getDataTableModel(final ManagedObject owner) {

            if(owner.getPojo()==null) {
                // owner (if entity) might have been deleted
                throw _Exceptions.illegalArgument("cannot recreate from memento for deleted object");
            }

            val memberId = featureId.getMemberLogicalName();

            final ManagedMember managedMember = featureId.getType().isPropertyOrCollection()
                    ? CollectionInteraction.start(owner, memberId, where)
                        .getManagedCollection().orElseThrow()
                    : ActionInteraction.start(owner, memberId, where)
                        .getManagedActionElseFail();

            var dataTableInteractive = new DataTableInteractive(managedMember, where,
                    dataTable.streamDataElements().collect(Can.toCan()));

            dataTableInteractive.searchArgument.setValue(searchArgument);
            dataTableInteractive.dataRowsFiltered.getValue()
                .forEach(dataRow->{
                    System.err.printf("%s%n", "set toggle");
                    dataRow.getSelectToggle().setValue(true);
                });
            return dataTableInteractive;
        }
    }

}
