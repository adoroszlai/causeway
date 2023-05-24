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
package org.apache.causeway.core.metamodel.specloader.validator;

import java.lang.annotation.Annotation;

import org.apache.causeway.core.config.progmodel.ProgrammingModelConstants;
import org.apache.causeway.core.metamodel.facetapi.FacetHolder;
import org.apache.causeway.core.metamodel.facets.FacetedMethod;
import org.apache.causeway.core.metamodel.facets.objectvalue.mandatory.MandatoryFacet;
import org.apache.causeway.core.metamodel.spec.ObjectSpecification;
import org.apache.causeway.core.metamodel.spec.feature.ObjectMember;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ValidationFailureUtils {

    public <A extends Annotation> void raiseAmbiguousMixinAnnotations(
            final FacetedMethod holder,
            final Class<A> annotationType) {

        ValidationFailure.raiseFormatted(holder,
                ProgrammingModelConstants.Violation.AMBIGUOUS_MIXIN_ANNOTATIONS
                    .builder()
                    .addVariable("annot", "@" + annotationType.getSimpleName())
                    .addVariable("mixinType", holder.getFeatureIdentifier().getFullIdentityString())
                    .buildMessage());
    }


    public void raiseMemberIdClash(
            final ObjectSpecification declaringType,
            final ObjectMember memberA,
            final ObjectMember memberB) {

        ValidationFailure.raiseFormatted(memberB,
                ProgrammingModelConstants.Violation.MEMBER_ID_CLASH
                    .builder()
                    .addVariable("type", declaringType.fqcn())
                    .addVariable("memberId", ""+memberB.getId())
                    .addVariable("member1", memberA.getFeatureIdentifier().getFullIdentityString())
                    .addVariable("member2", memberB.getFeatureIdentifier().getFullIdentityString())
                    .buildMessage());
    }

    public void raiseInvalidMemberElementType(
            final FacetHolder facetHolder,
            final ObjectSpecification declaringType,
            final ObjectSpecification elementType) {
        ValidationFailure.raiseFormatted(facetHolder,
                ProgrammingModelConstants.Violation.INVALID_MEMBER_ELEMENT_TYPE
                    .builder()
                    .addVariable("type", declaringType.fqcn())
                    .addVariable("elementType", ""+elementType)
                    .buildMessage());
    }


    //XXX assumes that given mandatoryFacet is one of the top ranking
    @Deprecated // marked deprecated, because not implemented
    public void raiseIfConflictingOptionality(final MandatoryFacet mandatoryFacet, final String message) {

        /* XXX yet has false positives
        if(isConflictingOptionality(mandatoryFacet)) {
            addFailure(mandatoryFacet, message);
        }
        */
    }

    // -- HELPER

    /*
    private void addFailure(final MandatoryFacet mandatoryFacet, final String message) {
        if(mandatoryFacet != null) {
            val holder = mandatoryFacet.getFacetHolder();
            ValidationFailure.raiseFormatted(
                    holder,
                    "%s : %s",
                    message,
                    holder.getFeatureIdentifier().getFullIdentityString());
        }
    }

    private boolean isConflictingOptionality(final MandatoryFacet mandatoryFacet) {
        if (mandatoryFacet == null) {
            return false;
        }

        //TODO maybe move this kind of logic to FacetRanking

        val facetRanking = mandatoryFacet.getSharedFacetRankingElseFail();

        // assumes that given mandatoryFacet is one of the top ranking
        _Assert.assertEquals(
                mandatoryFacet.getPrecedence(),
                facetRanking.getTopPrecedence().orElse(null));

        val topRankingFacets = facetRanking.getTopRank(mandatoryFacet.facetType());
        val firstOfTopRanking = topRankingFacets.getFirstElseFail();

        // the top ranking mandatory facets should semantically agree

        return topRankingFacets.isCardinalityMultiple()
                ? topRankingFacets
                        .stream()
                        .skip(1)
                        .anyMatch(firstOfTopRanking::semanticEquals)
                : false; // not conflicting

    }*/

}
