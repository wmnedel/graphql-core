/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.graphql.provider.dxm.node;

import graphql.annotations.annotationTypes.GraphQLName;

import javax.jcr.PropertyType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@GraphQLName("JCRPropertyType")
public enum GqlJcrPropertyType {
    BOOLEAN(PropertyType.BOOLEAN),
    DATE(PropertyType.DATE),
    DECIMAL(PropertyType.DECIMAL),
    LONG(PropertyType.LONG),
    DOUBLE(PropertyType.DOUBLE),
    BINARY(PropertyType.BINARY),
    NAME(PropertyType.NAME),
    PATH(PropertyType.PATH),
    REFERENCE(PropertyType.REFERENCE),
    STRING(PropertyType.STRING),
    UNDEFINED(PropertyType.UNDEFINED),
    URI(PropertyType.URI),
    WEAKREFERENCE(PropertyType.WEAKREFERENCE);

    private int value;

    private static final Map<Integer,GqlJcrPropertyType> lookup = new HashMap<Integer,GqlJcrPropertyType>();

    static {
        for(GqlJcrPropertyType s : EnumSet.allOf(GqlJcrPropertyType.class))
            lookup.put(s.getValue(), s);
    }

    GqlJcrPropertyType(int value) {
        this.value = value;
    }

    public static GqlJcrPropertyType fromValue(int type) {
        if (lookup.containsKey(type)) {
            return lookup.get(type);
        }
        throw new IllegalArgumentException("unknown type: " + type);
    }

    public int getValue() {
        return value;
    }
}
