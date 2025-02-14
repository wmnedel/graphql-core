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

import graphql.annotations.annotationTypes.*;
import graphql.annotations.connection.GraphQLConnection;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrQuery;
import org.jahia.modules.graphql.provider.dxm.predicate.FieldEvaluator;
import org.jahia.modules.graphql.provider.dxm.predicate.FieldFiltersInput;
import org.jahia.modules.graphql.provider.dxm.predicate.FilterHelper;
import org.jahia.modules.graphql.provider.dxm.relay.DXPaginatedData;
import org.jahia.modules.graphql.provider.dxm.relay.DXPaginatedDataConnectionFetcher;
import org.jahia.modules.graphql.provider.dxm.relay.PaginationHelper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

@GraphQLTypeExtension(GqlJcrQuery.class)
public class NodeTypeJCRQueryExtensions {

    @GraphQLField
    @GraphQLName("nodeTypeByName")
    @GraphQLDescription("Get a node type by its name")
    public static GqlJcrNodeType getNodeTypeByName(@GraphQLNonNull @GraphQLName("name") @GraphQLDescription("Node type name") String name) {
        try {
            return new GqlJcrNodeType(NodeTypeRegistry.getInstance().getNodeType(name));
        } catch (NoSuchNodeTypeException e) {
            throw new DataFetchingException(e);
        }
    }

    @GraphQLField
    @GraphQLName("nodeTypesByNames")
    @GraphQLDescription("Get multiple node types by their names")
    public static Collection<GqlJcrNodeType> getNodeTypesByNames(@GraphQLNonNull @GraphQLName("names") @GraphQLDescription("Node type names") Collection<String> names) {
        LinkedHashSet<GqlJcrNodeType> result = new LinkedHashSet<>(names.size());
        for (String name : names) {
            ExtendedNodeType nodeType;
            try {
                nodeType = NodeTypeRegistry.getInstance().getNodeType(name);
            } catch (NoSuchNodeTypeException e) {
                throw new DataFetchingException(e);
            }
            result.add(new GqlJcrNodeType(nodeType));
        }
        return result;
    }

    @GraphQLField
    @GraphQLName("nodeTypes")
    @GraphQLDescription("Get a list of nodetypes based on specified parameter")
    @GraphQLConnection(connectionFetcher = DXPaginatedDataConnectionFetcher.class)
    public static DXPaginatedData<GqlJcrNodeType> getNodeTypes(@GraphQLName("filter") @GraphQLDescription("Filter on node type") NodeTypesListInput input,
                                                               @GraphQLName("fieldFilter") @GraphQLDescription("Filter by graphQL fields values") FieldFiltersInput fieldFilter,
                                                               DataFetchingEnvironment environment) {
        try {
            PaginationHelper.Arguments arguments = PaginationHelper.parseArguments(environment);
            Stream<GqlJcrNodeType> mapped = NodeTypeHelper.getNodeTypes(input).map(GqlJcrNodeType::new)
                    .filter(FilterHelper.getFieldPredicate(fieldFilter, FieldEvaluator.forConnection(environment)));
            return PaginationHelper.paginate(mapped, GqlJcrNodeType::getName, arguments);
        } catch (RepositoryException e) {
            throw new DataFetchingException(e);
        }
    }
}
