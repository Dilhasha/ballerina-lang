/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.types.subtypedata;

import io.ballerina.types.EnumerableCharString;
import io.ballerina.types.EnumerableSubtype;
import io.ballerina.types.EnumerableType;

/**
 * Represent CharStringSubtype.
 *
 * @since 2201.12.0
 */
public class CharStringSubtype extends EnumerableSubtype {

    public boolean allowed;
    public EnumerableCharString[] values;

    private CharStringSubtype(boolean allowed, EnumerableCharString[] values) {
        this.allowed = allowed;
        this.values = values;
    }

    public static CharStringSubtype from(boolean allowed, EnumerableCharString[] values) {
        return new CharStringSubtype(allowed, values);
    }

    @Override
    public boolean allowed() {
        return allowed;
    }

    @Override
    public EnumerableType[] values() {
        return values;
    }
}
