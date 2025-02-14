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
package org.jahia.modules.graphql.provider.dxm.site;


import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNode;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNodeImpl;
import org.jahia.modules.graphql.provider.dxm.node.SpecializedType;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@GraphQLName("JCRSite")
@GraphQLDescription("GraphQL representation of a site node")
@SpecializedType("jnt:virtualsite")
public class GqlJcrSite extends GqlJcrNodeImpl implements GqlJcrNode {

    private JCRSiteNode siteNode;

    public GqlJcrSite(JCRNodeWrapper node) {
        super(node);
        this.siteNode = (JCRSiteNode) node;
    }

    @GraphQLField
    @GraphQLName("sitekey")
    @GraphQLDescription("Site key")
    public String getSiteKey() {
        return siteNode.getSiteKey();
    }

    @GraphQLField
    @GraphQLName("serverName")
    @GraphQLDescription("Site server name")
    public String getServerName() {
        return siteNode.getServerName();
    }

    @GraphQLField
    @GraphQLName("description")
    @GraphQLDescription("Site description")
    public String getDescription() {
        return siteNode.getDescription();
    }

    @GraphQLField
    @GraphQLName("defaultLanguage")
    @GraphQLDescription("Site default language")
    public String getDefaultLanguage() {
        return siteNode.getDefaultLanguage();
    }

    @GraphQLField
    @GraphQLName("installedModules")
    @GraphQLDescription("Retrieves a collection of module IDs, which are installed on the site, the node belongs to")
    public Collection<String> getInstalledModules() {
        return siteNode.getInstalledModules();
    }

    @GraphQLField
    @GraphQLName("installedModulesWithAllDependencies")
    @GraphQLDescription("Retrieves a collection of module IDs, which are installed on the site, the node belongs to, as well as dependencies of those modules")
    public Collection<String> getInstalledModulesWithAllDependencies() {
        return siteNode.getInstalledModulesWithAllDependencies();
    }

    @GraphQLField
    @GraphQLName("languages")
    @GraphQLDescription("Site languages")
    public Collection<GqlSiteLanguage> getLanguages() {
        List<GqlSiteLanguage> result = new ArrayList<>();

        for (String s : siteNode.getLanguages()) {
            result.add(new GqlSiteLanguage(s, true, siteNode.getActiveLiveLanguages().contains(s), siteNode.getMandatoryLanguages().contains(s)));
        }
        for (String s : siteNode.getInactiveLanguages()) {
            result.add(new GqlSiteLanguage(s, false, false, siteNode.getMandatoryLanguages().contains(s)));
        }

        return result;
    }

}
