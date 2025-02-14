package org.jahia.modules.graphql.provider.dxm.sdl.fetchers;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNode;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNodeImpl;
import org.jahia.modules.graphql.provider.dxm.sdl.SDLUtil;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.List;

import static graphql.Scalars.GraphQLString;

public class ByIdFinderDataFetcher extends FinderBaseDataFetcher {

    public ByIdFinderDataFetcher(Finder finder) {
        super(finder.getType(), finder);
    }

    @Override
    public List<GraphQLArgument> getArguments() {
        List<GraphQLArgument> defaultArguments = getDefaultArguments();
        defaultArguments.add(GraphQLArgument.newArgument().name("id").type(GraphQLString).build());
        return defaultArguments;
    }

    @Override
    public GqlJcrNode get(DataFetchingEnvironment environment) {
        try {
            return new GqlJcrNodeImpl(getCurrentUserSession(environment).getNodeByIdentifier((String) SDLUtil.getArgument("id", environment)));
        } catch (ItemNotFoundException e) {
            return null;
        } catch (RepositoryException e) {
            throw new DataFetchingException(e);
        }
    }
}
