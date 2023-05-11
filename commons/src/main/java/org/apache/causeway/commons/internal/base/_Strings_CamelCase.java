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
package org.apache.causeway.commons.internal.base;

import java.util.StringTokenizer;
import java.util.function.UnaryOperator;

import org.springframework.lang.Nullable;

import lombok.val;
import lombok.experimental.UtilityClass;

/**
 * package private utility for {@link _Strings}
 */
@UtilityClass
class _Strings_CamelCase {

    @Nullable
    String camelCase(final @Nullable String input, final UnaryOperator<String> firstTokenMapper) {

        if(input==null) return null;
        if(input.length()==0) return input;

        val sb = new StringBuffer(input.length());
        val tokenizer = new StringTokenizer(input);
        int tokenCount = 0;

        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            ++tokenCount;

            if(tokenCount==1) {
                // convert first token/word using firstTokenMapper
                sb.append(firstTokenMapper.apply(token));
            } else {
                // convert token/word to capitalized
                sb.append(token.substring(0, 1).toUpperCase());
                sb.append(token.substring(1));
            }
        }
        return sb.toString();
    }
}
