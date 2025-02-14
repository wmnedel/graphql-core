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
package org.jahia.modules.graphql.provider.dxm.nodetype;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrPropertyType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * GraphQL representation of a JCR property definition
 */
@GraphQLName("JCRPropertyDefinition")
@GraphQLDescription("GraphQL representation of a JCR property definition")
public class GqlJcrPropertyDefinition implements GqlJcrItemDefinition {

    private ExtendedPropertyDefinition definition;

    public GqlJcrPropertyDefinition(ExtendedPropertyDefinition definition) {
        this.definition = definition;
    }

    @Override
    @GraphQLName("name")
    @GraphQLNonNull
    @GraphQLDescription("Gets the name of the child item.")
    public String getName() {
        return definition.getName();
    }

    @Override
    @GraphQLNonNull
    @GraphQLDescription("Reports whether the item is mandatory. A mandatory item is one that, if its parent node exists, must also exist.")
    public boolean isMandatory() {
        return definition.isMandatory();
    }

    @Override
    @GraphQLNonNull
    @GraphQLDescription("Reports whether the item is to be automatically created when its parent node is created.")
    public boolean isAutoCreated() {
        return definition.isAutoCreated();
    }

    @Override
    @GraphQLNonNull
    @GraphQLDescription("Reports whether the child item is protected.")
    public boolean isProtected() {
        return definition.isProtected();
    }

    @Override
    @GraphQLNonNull
    @GraphQLDescription("Reports whether the child item is hidden from UI.")
    public boolean isHidden() {
        return definition.isHidden();
    }

    @GraphQLNonNull
    @GraphQLField
    @GraphQLDescription("Property constraints")
    public List<String> getConstraints() {
        return Arrays.stream(definition.getValueConstraintObjects()).map(ValueConstraint::getString).collect(Collectors.toList());
    }

    @Override
    @GraphQLName("declaringNodeType")
    @GraphQLNonNull
    @GraphQLDescription("Gets the node type that contains the declaration of this definition.")
    public GqlJcrNodeType getDeclaringNodeType() {
        return new GqlJcrNodeType(definition.getDeclaringNodeType());
    }

    @GraphQLField
    @GraphQLName("internationalized")
    @GraphQLDescription("Reports whether this property has language dependant values.")
    @GraphQLNonNull
    public boolean isInternationalized() {
        return definition.isInternationalized();
    }

    @GraphQLField
    @GraphQLDescription("Reports whether this property can have multiple values.")
    @GraphQLNonNull
    public boolean isMultiple() {
        return definition.isMultiple();
    }

    @GraphQLField
    @GraphQLName("requiredType")
    @GraphQLDescription("Gets the required type of the property.")
    @GraphQLNonNull
    public GqlJcrPropertyType getRequiredType() {
        return GqlJcrPropertyType.fromValue(definition.getRequiredType());
    }

    @GraphQLField
    @GraphQLName("displayName")
    @GraphQLDescription("Gets the displayable name of the property for the given language code. Return the system name in case the label doesn't exists")
    @GraphQLNonNull
    public String getDisplayName(@GraphQLName("language") @GraphQLDescription("Language") @GraphQLNonNull String language) {
        String displayName = definition.getLabel(new Locale(language));
        return StringUtils.isNotEmpty(displayName) ? displayName : getName();
    }
}
