package org.jahia.modules.graphql.provider.dxm.sdl.parsing;

import graphql.GraphQLException;
import graphql.annotations.processor.GraphQLAnnotationsComponent;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.language.*;
import graphql.schema.*;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.jahia.modules.graphql.provider.dxm.predicate.FieldSorterInput;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNode;
import org.jahia.modules.graphql.provider.dxm.relay.DXRelay;
import org.jahia.modules.graphql.provider.dxm.sdl.SDLConstants;
import org.jahia.modules.graphql.provider.dxm.sdl.SDLUtil;
import org.jahia.modules.graphql.provider.dxm.sdl.extension.FinderMixinInterface;
import org.jahia.modules.graphql.provider.dxm.sdl.extension.FinderAdapter;
import org.jahia.modules.graphql.provider.dxm.sdl.extension.PropertyFetcherExtensionInterface;
import org.jahia.modules.graphql.provider.dxm.sdl.fetchers.*;
import org.jahia.modules.graphql.provider.dxm.sdl.parsing.status.SDLDefinitionStatus;
import org.jahia.modules.graphql.provider.dxm.sdl.parsing.status.SDLDefinitionStatusType;
import org.jahia.modules.graphql.provider.dxm.sdl.parsing.status.SDLSchemaInfo;
import org.jahia.modules.graphql.provider.dxm.sdl.registration.SDLRegistrationService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;

@Component(service = SDLSchemaService.class, immediate = true)
public class SDLSchemaService {

    private static Logger logger = LoggerFactory.getLogger(SDLSchemaService.class);

    private DXRelay relay;
    private GraphQLSchema graphQLSchema;
    private SDLRegistrationService sdlRegistrationService;
    private Map<String, List<SDLSchemaInfo>> bundlesSDLSchemaStatus = new TreeMap<>();
    private Map<String, SDLDefinitionStatus> sdlDefinitionStatusMap = new TreeMap<>();
    private Map<Object, GraphQLInputType> sdlSpecialInputTypes = new HashMap<>();
    private List<FinderMixinInterface> finderMixins = new ArrayList<>();
    private Map<String, PropertyFetcherExtensionInterface> propertyFetcherExtensions = new HashMap<>();
    private Map<String, ConnectionHelper.ConnectionTypeInfo> connectionFieldNameToSDLType = new HashMap<>();
    private Map<String, GraphQLObjectType> edges = new HashMap<>();
    private Map<String, GraphQLObjectType> connections = new HashMap<>();

    public enum SpecialInputTypes {

        //add input type def here
        FIELD_SORTER_INPUT("FieldSorterInput", FieldSorterInput.class);

        private String name;
        private Class klass;

        SpecialInputTypes(String name, Class klass) {
            this.name = name;
            this.klass = klass;
        }

        public String getName() {
            return name;
        }

        public Class getKlass() {
            return klass;
        }
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY, policyOption = ReferencePolicyOption.GREEDY)
    public void setSdlRegistrationService(SDLRegistrationService sdlRegistrationService) {
        this.sdlRegistrationService = sdlRegistrationService;
    }

    public void generateSchema() {
        if (sdlRegistrationService != null && sdlRegistrationService.getSDLResources().size() > 0) {
            graphQLSchema = null;
            bundlesSDLSchemaStatus.clear();
            sdlDefinitionStatusMap.clear();

            TypeDefinitionRegistry typeDefinitionRegistry = prepareTypeRegistryDefinition();
            Map<TypeDefinition, String> sources = new HashMap<>();

            parseResources(typeDefinitionRegistry, sources);

            Set<TypeDefinition> invalidTypes = new HashSet<>();
            do {
                invalidTypes.clear();
                sources.forEach((type, bundle) -> {
                    SDLDefinitionStatus status = SDLTypeChecker.checkType(this, type, typeDefinitionRegistry);
                    sdlDefinitionStatusMap.put(type.getName(), status);
                    if (status.getStatus() != SDLDefinitionStatusType.OK) {
                        invalidTypes.add(type);
                        getOrCreateBundleSDLSchemaList(bundle).add(new SDLSchemaInfo(bundle, SDLSchemaInfo.SDLSchemaStatus.DEFINITION_ERROR, MessageFormat.format("Definition {0} : {1}", type.getName(), status.getStatusString())));
                    }
                });
                sources.keySet().removeAll(invalidTypes);
                invalidTypes.forEach(typeDefinitionRegistry::remove);
            } while (!invalidTypes.isEmpty());
            SDLTypeChecker.printStatuses(sdlDefinitionStatusMap);
            SchemaGenerator schemaGenerator = new SchemaGenerator();

            TypeDefinitionRegistry cleanedTypeRegistry = new TypeDefinitionRegistry();
            typeDefinitionRegistry.types().forEach((k, t) -> cleanedTypeRegistry.add(t));
            typeDefinitionRegistry.objectTypeExtensions().forEach((k, t) -> t.forEach(e -> cleanObjectExtensions(e, sources, cleanedTypeRegistry)));
            typeDefinitionRegistry.scalars().forEach((k, t) -> cleanedTypeRegistry.add(t));
            typeDefinitionRegistry.getDirectiveDefinitions().forEach((k, t) -> cleanedTypeRegistry.add(t));
            try {
                graphQLSchema = schemaGenerator.makeExecutableSchema(
                        SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false),
                        cleanedTypeRegistry,
                        SDLRuntimeWiring.runtimeWiring()
                );
            } catch (Exception e) {
                logger.warn("Invalid type definition(s) detected during schema generation {} ", e.getMessage());
            }
        }
    }

    private void cleanObjectExtensions(ObjectTypeExtensionDefinition e, Map<TypeDefinition, String> sources, TypeDefinitionRegistry cleanedTypeRegistry) {
        List<FieldDefinition> fieldDefinitions = e.getFieldDefinitions().stream().filter(f -> {
            if (f.getName().endsWith(SDLConstants.CONNECTION_QUERY_SUFFIX) && (f.getName().contains(FinderFetchersFactory.FetcherType.PATH.getSuffix()) || f.getName().contains(FinderFetchersFactory.FetcherType.ID.getSuffix()))) {
                //Remove extensions that are not compatible with a connection type
                String bundleName = sources.get(e);
                getOrCreateBundleSDLSchemaList(bundleName).add(new SDLSchemaInfo(bundleName, SDLSchemaInfo.SDLSchemaStatus.DEFINITION_ERROR, MessageFormat.format("You cannot use [{0}] as a query connection extension", f.getName())));
                logger.error("You cannot use [{}] as a query connection extension", f.getName());
                return true;
            }
            return false;
        }).collect(Collectors.toList());
        e.getFieldDefinitions().removeAll(fieldDefinitions);
        if (!e.getFieldDefinitions().isEmpty()) {
            //Add extensions that still have valid field definitions defined
            cleanedTypeRegistry.add(e);
        }
    }

    private void parseResources(TypeDefinitionRegistry typeDefinitionRegistry, Map<TypeDefinition, String> sources) {
        SchemaParser schemaParser = new SchemaParser();
        connections.clear();
        edges.clear();
        connectionFieldNameToSDLType.clear();
        for (Map.Entry<String, URL> entry : sdlRegistrationService.getSDLResources().entrySet()) {
            if (entry.getKey().equals("graphql-core")) continue;

            String bundle = entry.getKey();
            try {
                TypeDefinitionRegistry parsedRegistry = schemaParser.parse(new InputStreamReader(entry.getValue().openStream()));
                handleCustomConnectionTypes(parsedRegistry);
                parsedRegistry.types().forEach((key, type) -> sources.put(type, bundle));
                parsedRegistry.objectTypeExtensions().forEach((type, list) -> list.forEach(ext -> sources.put(ext, bundle)));
                typeDefinitionRegistry.merge(parsedRegistry);
                getOrCreateBundleSDLSchemaList(bundle);
            } catch (IOException ex) {
                logger.error("Failed to read sdl resource.", ex);
            } catch (GraphQLException ex) {
                logger.warn("Failed to merge schema from bundle [{}]: {}", bundle, ex.getMessage());
                getOrCreateBundleSDLSchemaList(bundle).add(new SDLSchemaInfo(bundle, SDLSchemaInfo.SDLSchemaStatus.SYNTAX_ERROR, ex.getMessage()));
            }
        }
    }

    public List<GraphQLFieldDefinition> getSDLQueries() {
        List<GraphQLFieldDefinition> defs = new ArrayList<>();
        if (graphQLSchema != null) {

            /** implicit data fetcher for all customer types  **/
            applyDefaultFetchers(defs);

            List<GraphQLFieldDefinition> fieldDefinitions = graphQLSchema.getQueryType().getFieldDefinitions();
            for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
                GraphQLObjectType objectType = fieldDefinition.getType() instanceof GraphQLList ?
                        (GraphQLObjectType) ((GraphQLList) fieldDefinition.getType()).getWrappedType() : (GraphQLObjectType) fieldDefinition.getType();

                GraphQLDirective directive = objectType.getDirective(SDLConstants.MAPPING_DIRECTIVE);

                if (directive == null) {
                    continue;
                }

                String nodeType = directive.getArgument(SDLConstants.MAPPING_DIRECTIVE_NODE).getValue().toString();

                //Handle connections
                if (fieldDefinition.getName().contains(SDLConstants.CONNECTION_QUERY_SUFFIX)) {
                    String queryFieldName = fieldDefinition.getName().replace(SDLConstants.CONNECTION_QUERY_SUFFIX, "");
                    String typeName = queryFieldName;

                    if (connectionFieldNameToSDLType.containsKey(fieldDefinition.getName())) {
                        typeName = connectionFieldNameToSDLType.get(fieldDefinition.getName()).getConnectionName().replace(SDLConstants.CONNECTION_QUERY_SUFFIX, "");
                    }

                    GraphQLOutputType node = (GraphQLOutputType) ((GraphQLList) fieldDefinition.getType()).getWrappedType();
                    GraphQLObjectType connectionType = ConnectionHelper.getOrCreateConnection(this, node, typeName);
                    FinderBaseDataFetcher typeFetcher = FinderFetchersFactory.getFetcher(fieldDefinition, nodeType);
                    List<GraphQLArgument> args = relay.getConnectionFieldArguments();
                    args.add(SDLUtil.wrapArgumentsInType(String.format("%s%s", queryFieldName, SDLConstants.CONNECTION_ARGUMENTS_SUFFIX), typeFetcher.getArguments()));
                    SDLPaginatedDataConnectionFetcher<GqlJcrNode> fetcher = new SDLPaginatedDataConnectionFetcher<>((FinderListDataFetcher) typeFetcher);
                    GraphQLFieldDefinition sdlDef = GraphQLFieldDefinition.newFieldDefinition(fieldDefinition)
                            .dataFetcher(fetcher)
                            .type(connectionType)
                            .argument(args)
                            .build();
                    defs.add(sdlDef);
                } else {
                    FinderBaseDataFetcher fetcher = FinderFetchersFactory.getFetcher(fieldDefinition, nodeType);
                    GraphQLFieldDefinition sdlDef = GraphQLFieldDefinition.newFieldDefinition(fieldDefinition)
                            .dataFetcher(fetcher)
                            .argument(fetcher.getArguments())
                            .build();
                    defs.add(sdlDef);
                }
            }

        }
        return defs;
    }

    public List<GraphQLType> getSDLTypes() {
        List<GraphQLType> types = new ArrayList<>();
        if (graphQLSchema != null) {
            List<String> reservedType = Arrays.asList("Query", "Mutation", "Subscription");
            for (Map.Entry<String, GraphQLType> gqlTypeEntry : graphQLSchema.getTypeMap().entrySet()) {
                if (!gqlTypeEntry.getKey().startsWith("__") && !reservedType.contains(gqlTypeEntry.getKey()) && !(gqlTypeEntry.getValue() instanceof GraphQLScalarType)) {
                    types.add(gqlTypeEntry.getValue());
                }
            }
        }
        return types;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, SDLDefinitionStatus> getSdlDefinitionStatusMap() {
        return sdlDefinitionStatusMap;
    }

    public Map<String, List<SDLSchemaInfo>> getBundlesSDLSchemaStatus() {
        return bundlesSDLSchemaStatus;
    }

    private List<SDLSchemaInfo> getOrCreateBundleSDLSchemaList(String bundle) {
        if (!bundlesSDLSchemaStatus.containsKey(bundle)) {
            bundlesSDLSchemaStatus.put(bundle, new LinkedList<>());
        }

        return bundlesSDLSchemaStatus.get(bundle);
    }

    private void applyDefaultFetchers(List<GraphQLFieldDefinition> defs) {
        for (GraphQLType type : graphQLSchema.getAllTypesAsList()) {
            if (type instanceof GraphQLObjectType) {
                GraphQLDirective directive = ((GraphQLObjectType) type).getDirective(SDLConstants.MAPPING_DIRECTIVE);
                if (directive != null) {
                    applyDefaultFetcher(defs, directive, (GraphQLOutputType) type, FinderFetchersFactory.FetcherType.ID);
                    applyDefaultFetcher(defs, directive, (GraphQLOutputType) type, FinderFetchersFactory.FetcherType.PATH);
                }
            }
        }
    }

    private void applyDefaultFetcher(final List<GraphQLFieldDefinition> defs, final GraphQLDirective directive,
                                     GraphQLOutputType type, final FinderFetchersFactory.FetcherType defaultFinder) {
        boolean shouldIgnoreDefaultQueries = false;
        if (directive.getArgument(SDLConstants.MAPPING_DIRECTIVE_IGNORE_DEFAULT_QUERIES).getValue() != null) {
            shouldIgnoreDefaultQueries = (Boolean) directive.getArgument(SDLConstants.MAPPING_DIRECTIVE_IGNORE_DEFAULT_QUERIES).getValue();
        }

        if (!shouldIgnoreDefaultQueries) {
            //when the type is defined in extending Query, it is type of GraphQLList like [MyType]
            GraphQLOutputType baseType = type;
            if (type instanceof GraphQLList) {
                baseType = (GraphQLOutputType) ((GraphQLList) type).getWrappedType();
            }

            GraphQLArgument argument = ((GraphQLObjectType) baseType).getDirective(SDLConstants.MAPPING_DIRECTIVE).getArgument(SDLConstants.MAPPING_DIRECTIVE_NODE);

            if (argument != null) {
                final String finderName = defaultFinder.getName(baseType.getName());

                Finder finder = new Finder(finderName);
                finder.setType(argument.getValue().toString());
                List<GraphQLArgument> args = new ArrayList<>();
                FinderBaseDataFetcher dataFetcher = FinderFetchersFactory.getFetcherType(finder, defaultFinder);
                args.addAll(dataFetcher.getArguments());
                List<FinderMixinInterface> applicableMixins = new ArrayList<>();

                for (FinderMixinInterface finderMixin : finderMixins) {
                    if (finderMixin.applyOnFinder(finder)) {
                        applicableMixins.add(finderMixin);
                        args.addAll(finderMixin.getArguments());
                    }
                }

                defs.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(finderName)
                        .description("default finder for " + finderName)
                        .dataFetcher(new FinderAdapter(dataFetcher, applicableMixins))
                        .argument(args)
                        .type(type)
                        .build());
            }
        }
    }

    private TypeDefinitionRegistry prepareTypeRegistryDefinition() {
        TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry();
        typeDefinitionRegistry.add(new ObjectTypeDefinition("Query"));
        typeDefinitionRegistry.add(new ScalarTypeDefinition("Date"));
        typeDefinitionRegistry.add(DirectiveDefinition.newDirectiveDefinition()
                .name(SDLConstants.MAPPING_DIRECTIVE)
                .directiveLocations(Arrays.asList(
                        DirectiveLocation.newDirectiveLocation().name("OBJECT").build(),
                        DirectiveLocation.newDirectiveLocation().name("FIELD_DEFINITION").build()))
                .inputValueDefinitions(Arrays.asList(
                        InputValueDefinition.newInputValueDefinition().name(SDLConstants.MAPPING_DIRECTIVE_NODE).type(TypeName.newTypeName(GraphQLString.getName()).build()).build(),
                        InputValueDefinition.newInputValueDefinition().name(SDLConstants.MAPPING_DIRECTIVE_PROPERTY).type(TypeName.newTypeName(GraphQLString.getName()).build()).build(),
                        InputValueDefinition.newInputValueDefinition().name(SDLConstants.MAPPING_DIRECTIVE_IGNORE_DEFAULT_QUERIES).type(TypeName.newTypeName(GraphQLBoolean.getName()).build()).build()))
                .build());

        typeDefinitionRegistry.add(DirectiveDefinition.newDirectiveDefinition()
                .name(SDLConstants.FETCHER_DIRECTIVE)
                .directiveLocations(Arrays.asList(
                        DirectiveLocation.newDirectiveLocation().name("FIELD_DEFINITION").build()))
                .inputValueDefinitions(Arrays.asList(
                        InputValueDefinition.newInputValueDefinition().name(SDLConstants.FETCHER_DIRECTIVE_NAME).type(TypeName.newTypeName(GraphQLString.getName()).build()).build()))
                .build());

        return typeDefinitionRegistry;
    }

    private void handleCustomConnectionTypes(TypeDefinitionRegistry typeDefinitionRegistry) {
        int queryIndex = 0;
        //Handle Query extension - clone/replace extension with new fields
        if (typeDefinitionRegistry.objectTypeExtensions().containsKey("Query")) {
            ObjectTypeExtensionDefinition query = typeDefinitionRegistry.objectTypeExtensions().get("Query").get(queryIndex);
            List<FieldDefinition> fields = query.getFieldDefinitions();

            //Collect connection fields i. e. ones that map to <TypeName>Connection
            for (FieldDefinition f : fields) {
                if (f.getName().endsWith(SDLConstants.CONNECTION_QUERY_SUFFIX) && f.getType() instanceof TypeName) {
                    String connectionName = ((TypeName) f.getType()).getName();
                    String type = connectionName.replace(SDLConstants.CONNECTION_QUERY_SUFFIX, "");
                    connectionFieldNameToSDLType.put(f.getName(), new ConnectionHelper.ConnectionTypeInfo(type, connectionName));
                }
            }

            //Transform query to handle connections. The transformation replaces "someConnection" : <TypeName>Connection
            //with "someConnection": [<TypeName>] as it needs to return a list in order for graphql to handle it
            if (!connectionFieldNameToSDLType.isEmpty()) {
                ObjectTypeExtensionDefinition newQuery = ConnectionHelper.newQueryWithoutConnections(query, connectionFieldNameToSDLType);
                newQuery = newQuery.transformExtension(new ConnectionHelper.TransformQueryExtension(connectionFieldNameToSDLType));
                typeDefinitionRegistry.objectTypeExtensions().get("Query").remove(queryIndex);
                typeDefinitionRegistry.add(newQuery);
            }
        }

        //Handle individual types with connections. Any type with 'fieldName : <Type name>Connection' signature
        //will be transformed to 'fieldName' : [<Type name>]
        for (ObjectTypeDefinition objectType : typeDefinitionRegistry.getTypes(ObjectTypeDefinition.class)) {
            Iterator<FieldDefinition> fields = objectType.getFieldDefinitions().iterator();

            while (fields.hasNext()) {
                FieldDefinition field = fields.next();
                if (field.getType() instanceof TypeName && ((TypeName) field.getType()).getName().endsWith(SDLConstants.CONNECTION_QUERY_SUFFIX)) {
                    fields.remove();
                    String connectionName = ((TypeName) field.getType()).getName();
                    String type = connectionName.replace(SDLConstants.CONNECTION_QUERY_SUFFIX, "");
                    connectionFieldNameToSDLType.put(objectType.getName() + "." + field.getName(), new ConnectionHelper.ConnectionTypeInfo(type, connectionName));
                }
            }

            for (Map.Entry<String, ConnectionHelper.ConnectionTypeInfo> entry : connectionFieldNameToSDLType.entrySet()) {
                if (!entry.getKey().startsWith(objectType.getName() + ".")) {
                    continue;
                }

                //Add new field with directive to trigger custom fetcher loading in directive wiring
                String name = StringUtils.substringAfter(entry.getKey(), ".");
                objectType.getFieldDefinitions().add(FieldDefinition.newFieldDefinition()
                        .directive(Directive.newDirective()
                                .name(SDLConstants.MAPPING_DIRECTIVE)
                                .arguments(Arrays.asList(Argument.newArgument()
                                        .name(SDLConstants.MAPPING_DIRECTIVE_PROPERTY)
                                        .value(new StringValue(SDLConstants.MAPPING_DIRECTIVE_FAKE_PROPERTY))
                                        .build()))
                                .build())
                        .name(name)
                        .type(new ListType(TypeName.newTypeName(entry.getValue().getMappedToType()).build()))
                        .build());
            }
        }
    }

    public void setRelay(DXRelay relay) {
        this.relay = relay;
    }

    public void refreshSpecialInputTypes(GraphQLAnnotationsComponent graphQLAnnotations, ProcessingElementsContainer container) {
        this.sdlSpecialInputTypes.clear();
        for (SpecialInputTypes specialInputType : SpecialInputTypes.values()) {
            this.sdlSpecialInputTypes.put(specialInputType.name, graphQLAnnotations.getInputTypeProcessor().getInputTypeOrRef(specialInputType.klass, container));
        }
    }

    public GraphQLInputType getSDLSpecialInputType(String name) {
        return this.sdlSpecialInputTypes.get(name);
    }

    public void addExtensions(DXGraphQLExtensionsProvider extensionsProvider) {
        addFinderMixins(extensionsProvider.getFinderMixins());
        addPropertyFetcherExtensions(extensionsProvider.getPropertyFetchers());
    }

    public void clearExtensions() {
        clearFinderMixins();
        clearPropertyFetcherExtensions();
    }

    public Map<String, PropertyFetcherExtensionInterface> getPropertyFetcherExtensions() {
        return propertyFetcherExtensions;
    }

    public Map<String, ConnectionHelper.ConnectionTypeInfo> getConnectionFieldNameToSDLType() {
        return connectionFieldNameToSDLType;
    }

    public Map<String, GraphQLObjectType> getEdges() {
        return edges;
    }

    public Map<String, GraphQLObjectType> getConnections() {
        return connections;
    }

    public DXRelay getRelay() {
        return relay;
    }

    private void addFinderMixins(List<FinderMixinInterface> mixins) {
        finderMixins.addAll(mixins);
    }

    private void clearFinderMixins() {
        finderMixins.clear();
    }

    private void addPropertyFetcherExtensions(Map<String, PropertyFetcherExtensionInterface> propertyFetcherExtensions) {
        this.propertyFetcherExtensions.putAll(propertyFetcherExtensions);
    }

    private void clearPropertyFetcherExtensions() {
        this.propertyFetcherExtensions.clear();
    }
}
