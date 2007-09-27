/**
 * Copyright (c) 2000-2006, Identyx Corporation.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.safehaus.penrose.jdbc.adapter;

import org.safehaus.penrose.util.Formatter;
import org.safehaus.penrose.filter.Filter;
import org.safehaus.penrose.filter.SimpleFilter;
import org.safehaus.penrose.filter.FilterTool;
import org.safehaus.penrose.source.FieldConfig;
import org.safehaus.penrose.source.SourceConfig;
import org.safehaus.penrose.partition.Partition;
import org.safehaus.penrose.partition.PartitionContext;
import org.safehaus.penrose.adapter.Adapter;
import org.safehaus.penrose.jdbc.*;
import org.safehaus.penrose.jdbc.Request;
import org.safehaus.penrose.source.*;
import org.safehaus.penrose.source.jdbc.JDBCSourceSync;
import org.safehaus.penrose.ldap.*;
import org.safehaus.penrose.session.Session;
import org.safehaus.penrose.connection.Connection;
import org.safehaus.penrose.directory.Entry;
import org.safehaus.penrose.directory.SourceRef;
import org.safehaus.penrose.directory.FieldRef;
import org.safehaus.penrose.interpreter.Interpreter;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.dbcp.*;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class JDBCAdapter extends Adapter {

    public final static String DRIVER       = "driver";
    public final static String URL          = "url";
    public final static String USER         = "user";
    public final static String PASSWORD     = "password";
    public final static String QUOTE        = "quote";

    public final static String BASE_DN      = "baseDn";
    public final static String CATALOG      = "catalog";
    public final static String SCHEMA       = "schema";
    public final static String TABLE        = "table";
    public final static String FILTER       = "filter";
    public final static String SIZE_LIMIT   = "sizeLimit";

    public final static String AUTHENTICATION          = "authentication";
    public final static String AUTHENTICATION_DEFAULT  = "default";
    public final static String AUTHENTICATION_FULL     = "full";
    public final static String AUTHENTICATION_DISABLED = "disabled";

    public final static String INITIAL_SIZE                         = "initialSize";
    public final static String MAX_ACTIVE                           = "maxActive";
    public final static String MAX_IDLE                             = "maxIdle";
    public final static String MIN_IDLE                             = "minIdle";
    public final static String MAX_WAIT                             = "maxWait";

    public final static String VALIDATION_QUERY                     = "validationQuery";
    public final static String TEST_ON_BORROW                       = "testOnBorrow";
    public final static String TEST_ON_RETURN                       = "testOnReturn";
    public final static String TEST_WHILE_IDLE                      = "testWhileIdle";
    public final static String TIME_BETWEEN_EVICTION_RUNS_MILLIS    = "timeBetweenEvictionRunsMillis";
    public final static String NUM_TESTS_PER_EVICTION_RUN           = "numTestsPerEvictionRun";
    public final static String MIN_EVICTABLE_IDLE_TIME_MILLIS       = "minEvictableIdleTimeMillis";

    public final static String SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS  = "softMinEvictableIdleTimeMillis";
    public final static String WHEN_EXHAUSTED_ACTION                = "whenExhaustedAction";

    public GenericObjectPool.Config config = new GenericObjectPool.Config();
    public GenericObjectPool connectionPool;
    public DataSource ds;

    private Driver driver;
    private JDBCClient client;

    public void init() throws Exception {

        Properties properties = new Properties();
        properties.putAll(getParameters());

        String driverClass = (String)properties.remove(DRIVER);
        String url = (String)properties.remove(URL);

        PartitionContext partitionContext = partition.getPartitionContext();
        ClassLoader cl = partitionContext.getClassLoader();
        Class clazz = cl.loadClass(driverClass);

        driver = (Driver)clazz.newInstance();
        DriverManager.registerDriver(this.driver);

        String s = (String)properties.remove(INITIAL_SIZE);
        int initialSize = s == null ? 1 : Integer.parseInt(s);

        s = (String)properties.remove(MAX_ACTIVE);
        if (s != null) config.maxActive = Integer.parseInt(s);

        s = (String)properties.remove(MAX_IDLE);
        if (s != null) config.maxIdle = Integer.parseInt(s);

        s = (String)properties.remove(MAX_WAIT);
        if (s != null) config.maxWait = Integer.parseInt(s);

        s = (String)properties.remove(MIN_EVICTABLE_IDLE_TIME_MILLIS);
        if (s != null) config.minEvictableIdleTimeMillis = Integer.parseInt(s);

        s = (String)properties.remove(MIN_IDLE);
        if (s != null) config.minIdle = Integer.parseInt(s);

        s = (String)properties.remove(NUM_TESTS_PER_EVICTION_RUN);
        if (s != null) config.numTestsPerEvictionRun = Integer.parseInt(s);

        s = (String)properties.remove(TEST_ON_BORROW);
        if (s != null) config.testOnBorrow = Boolean.valueOf(s);

        s = (String)properties.remove(TEST_ON_RETURN);
        if (s != null) config.testOnReturn = Boolean.valueOf(s);

        s = (String)properties.remove(TEST_WHILE_IDLE);
        if (s != null) config.testWhileIdle = Boolean.valueOf(s);

        s = (String)properties.remove(TIME_BETWEEN_EVICTION_RUNS_MILLIS);
        if (s != null) config.timeBetweenEvictionRunsMillis = Integer.parseInt(s);

        //s = (String)properties.remove(SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS);
        //if (s != null) config.softMinEvictableIdleTimeMillis = Integer.parseInt(s);

        //s = (String)properties.remove(WHEN_EXHAUSTED_ACTION);
        //if (s != null) config.whenExhaustedAction = Byte.parseByte(s);

        connectionPool = new GenericObjectPool(null, config);

        String validationQuery = (String)properties.remove(VALIDATION_QUERY);

        ConnectionFactory connectionFactory = new DriverConnectionFactory(this.driver, url, properties);

        //PoolableConnectionFactory poolableConnectionFactory =
                new PoolableConnectionFactory(
                        connectionFactory,
                        connectionPool,
                        null, // statement pool factory
                        validationQuery, // test query
                        false, // read only
                        true // auto commit
                );

        log.debug("Starting "+adapterConfig.getName()+" adapter.");

        //log.debug("Initializing "+initialSize+" connections.");
        for (int i = 0; i < initialSize; i++) {
             connectionPool.addObject();
         }

        ds = new PoolingDataSource(connectionPool);

        client = new JDBCClient(driver, getParameters());
    }

    public void destroy() throws Exception {
        log.debug("Stopping "+adapterConfig.getName()+" adapter.");
        client.close();
    }

    public Object openConnection() throws Exception {
        return client.getConnection();
    }

    public String getFieldNames(SourceConfig sourceConfig) throws Exception {
        StringBuilder sb = new StringBuilder();

        Collection<FieldConfig> fields = sourceConfig.getFieldConfigs();
        for (FieldConfig fieldConfig : fields) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(fieldConfig.getOriginalName());
        }

        return sb.toString();
    }

    public String getOringialPrimaryKeyFieldNamesAsString(SourceConfig sourceConfig) throws Exception {
        StringBuilder sb = new StringBuilder();

        Collection<String> fields = sourceConfig.getOriginalPrimaryKeyNames();
        for (String name : fields) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(name);
        }

        return sb.toString();
    }

    public boolean isJoinSupported() {
        return true;
    }

    public String getSyncClassName() {
        return JDBCSourceSync.class.getName();
    }

    public JDBCClient getClient(Session session, Partition partition, Source source) throws Exception {

        String authentication = source.getParameter(AUTHENTICATION);
        if (debug) log.debug("Authentication: "+authentication);

        Connection connection = source.getConnection();
        JDBCClient client;

        if (AUTHENTICATION_FULL.equals(authentication)) {
            if (debug) log.debug("Getting connection info from session.");

            client = session == null ? null : (JDBCClient)session.getAttribute(partition.getName()+".connection."+connection.getName());

            if (client == null) {

                if (session == null || session.isRootUser()) {
                    if (debug) log.debug("Creating new connection.");

                    client = new JDBCClient(driver, connection.getParameters());

                } else {
                    if (debug) log.debug("Missing credentials.");
                    throw LDAP.createException(LDAP.INVALID_CREDENTIALS);
                }
            }

        } else {
            client = this.client;
        }

        return client;
    }

    public void closeClient(Session session, Partition partition, Source source, JDBCClient client) throws Exception {

        //String authentication = source.getParameter(AUTHENTICATON);
        //if (debug) log.debug("Authentication: "+authentication);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Storage
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void create(Source source) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Create "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        client.createTable(source);
    }

    public void rename(Source oldSource, Source newSource) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Rename "+oldSource.getName()+" to "+newSource.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        client.renameTable(oldSource, newSource);
    }

    public void drop(Source source) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Drop "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        client.dropTable(source);
    }

    public void clear(Source source) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Clean "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        client.cleanTable(source);
    }

    public void status(Source source) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Clean "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        client.showStatus(source);
    }

    public long getCount(Source source) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Count "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        return client.getCount(source);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Add
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void add(
            final Session session,
            final Source source,
            final AddRequest request,
            final AddResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Add "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        JDBCClient client = getClient(session, partition, source);

        try {
            InsertStatement statement = new InsertStatement();
            statement.setSource(source);

            RDN rdn = request.getDn().getRdn();

            if (rdn != null) {
                for (String name : rdn.getNames()) {

                    Object value = rdn.get(name);

                    Field field = source.getField(name);
                    if (field == null) throw new Exception("Unknown field: " + name);

                    statement.addAssignment(new Assignment(field, value));
                }
            }

            Attributes attributes = request.getAttributes();

            for (String name : attributes.getNames()) {
                if (rdn != null && rdn.contains(name)) continue;

                Object value = attributes.getValue(name); // get first value

                Field field = source.getField(name);
                if (field == null) throw new Exception("Unknown field: " + name);

                statement.addAssignment(new Assignment(field, value));
            }

            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setStatement(statement);

            UpdateResponse updateResponse = new UpdateResponse();

            client.executeUpdate(updateRequest, updateResponse);

            log.debug("Add operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    public void add(
            Session session,
            Entry entry,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            AddRequest request,
            AddResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Add "+ sourceRefs, 80));
            log.debug(Formatter.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        SourceRef sourceRef = sourceRefs.iterator().next();
        Source source = sourceRef.getSource();
        JDBCClient client = getClient(session, partition, source);

        Interpreter interpreter = partition.newInterpreter();

        try {
            AddRequestBuilder builder = new AddRequestBuilder(
                    sourceRefs,
                    sourceValues,
                    interpreter,
                    request,
                    response
            );

            Collection<Request> requests = builder.generate();
            for (Request req : requests) {
                UpdateRequest updateRequest = (UpdateRequest) req;
                UpdateResponse updateResponse = new UpdateResponse();

                client.executeUpdate(updateRequest, updateResponse);
            }

            log.debug("Add operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Compare
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void compare(
            final Session session,
            final Source source,
            final CompareRequest request,
            final CompareResponse response
    ) throws Exception {

        SearchRequest newRequest = new SearchRequest();
        newRequest.setDn(request.getDn());
        newRequest.setScope(SearchRequest.SCOPE_BASE);

        SimpleFilter filter = new SimpleFilter(request.getAttributeName(), "=", request.getAttributeValue());
        newRequest.setFilter(filter);

        SearchResponse newResponse = new SearchResponse();

        search(session, source, newRequest, newResponse);

        boolean result = newResponse.hasNext();

        if (debug) log.debug("Compare operation completed ["+result+"].");
        response.setReturnCode(result ? LDAP.COMPARE_TRUE : LDAP.COMPARE_FALSE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Delete
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void delete(
            final Session session,
            final Source source,
            final DeleteRequest request,
            final DeleteResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Delete "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        JDBCClient client = getClient(session, partition, source);

        try {
            DeleteStatement statement = new DeleteStatement();

            SourceRef sourceRef = new SourceRef(source);
            statement.setSourceRef(sourceRef);

            RDN rdn = request.getDn().getRdn();
            Filter filter = null;
            for (String name : rdn.getNames()) {
                Object value = rdn.get(name);

                SimpleFilter sf = new SimpleFilter(name, "=", value);
                filter = FilterTool.appendAndFilter(filter, sf);
            }

            statement.setFilter(filter);

            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setStatement(statement);

            UpdateResponse updateResponse = new UpdateResponse();

            client.executeUpdate(updateRequest, updateResponse);

            log.debug("Delete operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    public void delete(
            Session session,
            Entry entry,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            DeleteRequest request,
            DeleteResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Delete "+ sourceRefs, 80));
            log.debug(Formatter.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        SourceRef sourceRef = sourceRefs.iterator().next();
        Source source = sourceRef.getSource();
        JDBCClient client = getClient(session, partition, source);

        Interpreter interpreter = partition.newInterpreter();

        try {
            DeleteRequestBuilder builder = new DeleteRequestBuilder(
                    sourceRefs,
                    sourceValues,
                    interpreter,
                    request,
                    response
            );

            Collection<Request> requests = builder.generate();
            for (Request req : requests) {
                UpdateRequest updateRequest = (UpdateRequest) req;
                UpdateResponse updateResponse = new UpdateResponse();

                client.executeUpdate(updateRequest, updateResponse);
            }

            log.debug("Delete operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Modify
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void modify(
            final Session session,
            final Source source,
            final ModifyRequest request,
            final ModifyResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Modify "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        JDBCClient client = getClient(session, partition, source);

        try {
            UpdateStatement statement = new UpdateStatement();

            SourceRef sourceRef = new SourceRef(source);
            statement.setSourceRef(sourceRef);

            RDN rdn = request.getDn().getRdn();

            Collection<Modification> modifications = request.getModifications();
            for (Modification modification : modifications) {

                int type = modification.getType();
                Attribute attribute = modification.getAttribute();
                String name = attribute.getName();

                Field field = source.getField(name);
                if (field == null) continue;

                switch (type) {
                    case Modification.ADD:
                    case Modification.REPLACE:
                        Object value = rdn.get(name);
                        if (value == null) value = attribute.getValue();
                        statement.addAssignment(new Assignment(field, value));
                        break;

                    case Modification.DELETE:
                        statement.addAssignment(new Assignment(field, null));
                        break;
                }
            }

            Filter filter = null;
            for (String name : rdn.getNames()) {
                Object value = rdn.get(name);

                SimpleFilter sf = new SimpleFilter(name, "=", value);
                filter = FilterTool.appendAndFilter(filter, sf);
            }

            statement.setFilter(filter);

            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setStatement(statement);

            UpdateResponse updateResponse = new UpdateResponse();

            client.executeUpdate(updateRequest, updateResponse);

            log.debug("Modify operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    public void modify(
            Session session,
            Entry entry,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            ModifyRequest request,
            ModifyResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Modify "+ sourceRefs, 80));
            log.debug(Formatter.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        SourceRef sourceRef = sourceRefs.iterator().next();
        Source source = sourceRef.getSource();
        JDBCClient client = getClient(session, partition, source);

        Interpreter interpreter = partition.newInterpreter();

        try {
            ModifyRequestBuilder builder = new ModifyRequestBuilder(
                    sourceRefs,
                    sourceValues,
                    interpreter,
                    request,
                    response
            );

            Collection<Request> requests = builder.generate();
            for (Request req : requests) {
                UpdateRequest updateRequest = (UpdateRequest) req;
                UpdateResponse updateResponse = new UpdateResponse();

                client.executeUpdate(updateRequest, updateResponse);
            }

            log.debug("Modify operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ModRDN
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void modrdn(
            final Session session,
            final Source source,
            final ModRdnRequest request,
            final ModRdnResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("ModRdn "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        JDBCClient client = getClient(session, partition, source);

        try {
            UpdateStatement statement = new UpdateStatement();

            SourceRef sourceRef = new SourceRef(source);
            statement.setSourceRef(sourceRef);

            RDN newRdn = request.getNewRdn();
            for (String name : newRdn.getNames()) {
                Object value = newRdn.get(name);

                Field field = source.getField(name);
                if (field == null) continue;

                statement.addAssignment(new Assignment(field, value));
            }

            RDN rdn = request.getDn().getRdn();
            Filter filter = null;
            for (String name : rdn.getNames()) {
                Object value = rdn.get(name);

                SimpleFilter sf = new SimpleFilter(name, "=", value);
                filter = FilterTool.appendAndFilter(filter, sf);
            }

            statement.setFilter(filter);

            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setStatement(statement);

            UpdateResponse updateResponse = new UpdateResponse();

            client.executeUpdate(updateRequest, updateResponse);

            log.debug("ModRdn operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    public void modrdn(
            Session session,
            Entry entry,
            Collection<SourceRef> sourceRefs,
            SourceValues sourceValues,
            ModRdnRequest request,
            ModRdnResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("ModRdn "+ sourceRefs, 80));
            log.debug(Formatter.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        SourceRef sourceRef = sourceRefs.iterator().next();
        Source source = sourceRef.getSource();
        JDBCClient client = getClient(session, partition, source);

        Interpreter interpreter = partition.newInterpreter();

        try {
            ModRdnRequestBuilder builder = new ModRdnRequestBuilder(
                    sourceRefs,
                    sourceValues,
                    interpreter,
                    request,
                    response
            );

            Collection<Request> requests = builder.generate();
            for (Request req : requests) {
                UpdateRequest updateRequest = (UpdateRequest) req;
                UpdateResponse updateResponse = new UpdateResponse();

                client.executeUpdate(updateRequest, updateResponse);
            }

            log.debug("ModRdn operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Search
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void search(
            final Session session,
            final Source source,
            final SearchRequest request,
            final SearchResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Search "+source.getName(), 80));
            log.debug(Formatter.displaySeparator(80));
        }

        JDBCClient client = getClient(session, partition, source);

        try {
            response.setSizeLimit(request.getSizeLimit());

            SelectStatement statement = new SelectStatement();

            SourceRef sourceRef = new SourceRef(source);

            Filter filter = null;

            DN dn = request.getDn();
            if (dn != null) {
                RDN rdn = dn.getRdn();
                for (String name : rdn.getNames()) {
                    Object value = rdn.get(name);

                    SimpleFilter sf = new SimpleFilter(name, "=", value);
                    filter = FilterTool.appendAndFilter(filter, sf);
                }
            }

            filter = FilterTool.appendAndFilter(filter, request.getFilter());

            statement.addFieldRefs(sourceRef.getFieldRefs());
            statement.addSourceRef(sourceRef);
            statement.setFilter(filter);

            String where = source.getParameter(FILTER);
            if (where != null) {
                statement.setWhere(where);
            }

            statement.setOrders(sourceRef.getPrimaryKeyFieldRefs());

            QueryRequest queryRequest = new QueryRequest();
            queryRequest.setStatement(statement);

            QueryResponse queryResponse = new QueryResponse() {
                public void add(Object object) throws Exception {
                    ResultSet rs = (ResultSet)object;

                    if (sizeLimit > 0 && totalCount >= sizeLimit) {
                        throw LDAP.createException(LDAP.SIZE_LIMIT_EXCEEDED);
                    }

                    SearchResult searchResult = createSearchResult(source, rs);
                    response.add(searchResult);

                    totalCount++;
                }
                public void close() throws Exception {
                    response.close();
                }
            };

            String sizeLimit = source.getParameter(SIZE_LIMIT);
            if (sizeLimit != null) {
                queryResponse.setSizeLimit(Long.parseLong(sizeLimit));
            }

            client.executeQuery(queryRequest, queryResponse);

            log.debug("Search operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    public void search(
            final Session session,
            final Collection<SourceRef> primarySourceRefs,
            final Collection<SourceRef> localSourceRefs,
            final Collection<SourceRef> sourceRefs,
            final SourceValues sourceValues,
            final SearchRequest request,
            final SearchResponse response
    ) throws Exception {

        if (debug) {
            log.debug(Formatter.displaySeparator(80));
            log.debug(Formatter.displayLine("Search "+ sourceRefs, 80));
            log.debug(Formatter.displaySeparator(80));

            log.debug("Source values:");
            sourceValues.print();
        }

        SourceRef sourceRef = sourceRefs.iterator().next();
        Source source = sourceRef.getSource();
        JDBCClient client = getClient(session, partition, source);

        Interpreter interpreter = partition.newInterpreter();

        try {
            response.setSizeLimit(request.getSizeLimit());

            SearchRequestBuilder builder = new SearchRequestBuilder(
                    interpreter,
                    partition,
                    primarySourceRefs,
                    localSourceRefs,
                    sourceRefs,
                    sourceValues,
                    request,
                    response
            );

            QueryRequest queryRequest = builder.generate();
            QueryResponse queryResponse = new QueryResponse() {

                SearchResult lastResult;

                public void add(Object object) throws Exception {
                    ResultSet rs = (ResultSet)object;

                    if (sizeLimit > 0 && totalCount >= sizeLimit) {
                        throw LDAP.createException(LDAP.SIZE_LIMIT_EXCEEDED);
                    }

                    SearchResult searchResult = createSearchResult(primarySourceRefs, sourceRefs, rs);
                    if (searchResult == null) return;

                    if (lastResult == null) {
                        lastResult = searchResult;

                    } else if (searchResult.getDn().equals(lastResult.getDn())) {
                        mergeSearchResult(searchResult, lastResult);

                    } else {
                        response.add(lastResult);
                        lastResult = searchResult;
                    }

                    totalCount++;

                    if (debug) {
                        searchResult.print();
                    }
                }

                public void close() throws Exception {
                    if (lastResult != null) {
                        response.add(lastResult);
                    }
                    response.close();
                }
            };

            String sizeLimit = source.getParameter(SIZE_LIMIT);
            if (sizeLimit != null) {
                queryResponse.setSizeLimit(Long.parseLong(sizeLimit));
            }

            client.executeQuery(queryRequest, queryResponse);

            log.debug("Search operation completed.");

        } finally {
            closeClient(session, partition, source, client);
        }
    }

    public SearchResult createSearchResult(
            Source source,
            ResultSet rs
    ) throws Exception {

        Attributes attributes = new Attributes();
        RDNBuilder rb = new RDNBuilder();

        int column = 1;

        for (Field field : source.getFields()) {

            Object value = rs.getObject(column++);
            if (value == null) continue;

            String fieldName = field.getName();
            attributes.addValue(fieldName, value);

            if (field.isPrimaryKey()) rb.set(fieldName, value);
        }

        DNBuilder db = new DNBuilder();
        db.append(rb.toRdn());

        String baseDn = source.getParameter(BASE_DN);
        if (baseDn != null) {
            db.append(baseDn);
        }

        DN dn = db.toDn();

        return new SearchResult(dn, attributes);
    }

    public SearchResult createSearchResult(
            Collection<SourceRef> primarySourceRefs,
            Collection<SourceRef> sourceRefs,
            ResultSet rs
    ) throws Exception {

        SearchResult searchResult = new SearchResult();

        SourceValues sourceValues = new SourceValues();
        RDNBuilder rb = new RDNBuilder();

        int column = 1;

        for (SourceRef sourceRef : sourceRefs) {
            String alias = sourceRef.getAlias();
            boolean primarySource = primarySourceRefs.contains(sourceRef);

            Attributes fields = new Attributes();

            for (FieldRef fieldRef : sourceRef.getFieldRefs()) {

                Object value = rs.getObject(column++);

                String fieldName = fieldRef.getName();
                String name = alias + "." + fieldName;

                if (primarySource && fieldRef.isPrimaryKey()) {
                    if (value == null) return null;
                    rb.set(name, value);
                }

                if (value == null) continue;
                fields.addValue(fieldName, value);
            }

            sourceValues.set(alias, fields);
        }

        searchResult.setSourceValues(sourceValues);
        searchResult.setDn(new DN(rb.toRdn()));

        return searchResult;
    }

    public void mergeSearchResult(SearchResult source, SearchResult destination) {
        SourceValues sourceValues = source.getSourceValues();
        SourceValues destinationValues = destination.getSourceValues();

        destinationValues.add(sourceValues);
    }

    public JDBCClient getClient() {
        return client;
    }

    public void setClient(JDBCClient client) {
        this.client = client;
    }
}
