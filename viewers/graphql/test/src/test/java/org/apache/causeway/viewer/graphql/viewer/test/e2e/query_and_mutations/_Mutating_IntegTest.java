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
package org.apache.causeway.viewer.graphql.viewer.test.e2e.query_and_mutations;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import org.apache.causeway.viewer.graphql.viewer.test.CausewayViewerGraphqlTestModuleIntegTestAbstract;
import org.apache.causeway.viewer.graphql.viewer.test.e2e.Abstract_IntegTest;


//NOT USING @Transactional since we are running server within same transaction otherwise
@SpringBootTest(
        classes = {
                CausewayViewerGraphqlTestModuleIntegTestAbstract.TestApp.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "causeway.viewer.graphql.api-variant=QUERY_AND_MUTATIONS"
        }
)
@Order(100)
@ActiveProfiles("test")
@DirtiesContext
public class _Mutating_IntegTest extends Abstract_IntegTest {

    @Test
    void dummy() throws Exception {
    }

}
