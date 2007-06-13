package org.safehaus.penrose.naming;

import org.safehaus.penrose.thread.ThreadManager;
import org.safehaus.penrose.schema.SchemaManager;
import org.safehaus.penrose.schema.SchemaConfig;
import org.safehaus.penrose.partition.*;
import org.safehaus.penrose.connection.ConnectionManager;
import org.safehaus.penrose.connector.ConnectorManager;
import org.safehaus.penrose.interpreter.InterpreterManager;
import org.safehaus.penrose.interpreter.InterpreterConfig;
import org.safehaus.penrose.config.PenroseConfig;
import org.safehaus.penrose.config.PenroseConfigWriter;
import org.safehaus.penrose.source.SourceManager;
import org.safehaus.penrose.source.SourceSyncManager;
import org.safehaus.penrose.source.SourceSyncConfig;
import org.safehaus.penrose.mapping.EntryMapping;
import org.safehaus.penrose.filter.FilterEvaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Endi S. Dewata
 */
public class PenroseContext {

    public Logger log = LoggerFactory.getLogger(getClass());

    public final static String THREAD_MANAGER      = "java:comp/org/safehaus/penrose/thread/ThreadManager";
    public final static String SCHEMA_MANAGER      = "java:comp/org/safehaus/penrose/schema/SchemaManager";

    public final static String SESSION_MANAGER     = "java:comp/org/safehaus/penrose/session/SessionManager";
    public final static String HANDLER_MANAGER     = "java:comp/org/safehaus/penrose/handler/HandlerManager";
    public final static String EVENT_MANAGER       = "java:comp/org/safehaus/penrose/event/EventManager";

    public final static String ENGINE_MANAGER      = "java:comp/org/safehaus/penrose/engine/EngineManager";
    public final static String INTERPRETER_MANAGER = "java:comp/org/safehaus/penrose/interpreter/InterpreterManager";
    public final static String CONNECTOR_MANAGER   = "java:comp/org/safehaus/penrose/connector/ConnectorManager";

    public final static String PARTITION_MANAGER   = "java:comp/org/safehaus/penrose/partition/PartitionManager";
    public final static String CONNECTION_MANAGER  = "java:comp/org/safehaus/penrose/connection/ConnectionManager";
    public final static String SOURCE_MANAGER      = "java:comp/org/safehaus/penrose/source/SourceManager";
    public final static String MODULE_MANAGER      = "java:comp/org/safehaus/penrose/module/ModuleManager";

    private PenroseConfig      penroseConfig;

    private ThreadManager      threadManager;
    private SchemaManager      schemaManager;
    private FilterEvaluator    filterEvaluator;
    private InterpreterManager interpreterManager;

    private ConnectorManager   connectorManager;

    private PartitionManager   partitionManager;
    private ConnectionManager  connectionManager;
    private SourceManager      sourceManager;
    private SourceSyncManager  sourceSyncManager;

    public PenroseContext() {
    }

    public ThreadManager getThreadManager() {
        return threadManager;
    }

    public void setThreadManager(ThreadManager threadManager) {
        this.threadManager = threadManager;
    }

    public SchemaManager getSchemaManager() {
        return schemaManager;
    }

    public void setSchemaManager(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
    }

    public PartitionManager getPartitionManager() {
        return partitionManager;
    }

    public void setPartitionManager(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public InterpreterManager getInterpreterManager() {
        return interpreterManager;
    }

    public void setInterpreterManager(InterpreterManager interpreterManager) {
        this.interpreterManager = interpreterManager;
    }

    public ConnectorManager getConnectorManager() {
        return connectorManager;
    }

    public void setConnectorManager(ConnectorManager connectorManager) {
        this.connectorManager = connectorManager;
    }

    public SourceManager getSourceManager() {
        return sourceManager;
    }

    public void setSourceManager(SourceManager sourceManager) {
        this.sourceManager = sourceManager;
    }

    public SourceSyncManager getSourceSyncManager() {
        return sourceSyncManager;
    }

    public void setSourceSyncManager(SourceSyncManager sourceSyncManager) {
        this.sourceSyncManager = sourceSyncManager;
    }

    public void init(PenroseConfig penroseConfig) throws Exception {
        this.penroseConfig = penroseConfig;

        threadManager = new ThreadManager();
        threadManager.setPenroseConfig(penroseConfig);
        threadManager.setPenroseContext(this);

        schemaManager = new SchemaManager();
        schemaManager.setPenroseConfig(penroseConfig);
        schemaManager.setPenroseContext(this);

        filterEvaluator = new FilterEvaluator();
        filterEvaluator.setSchemaManager(schemaManager);

        interpreterManager = new InterpreterManager();
        interpreterManager.setPenroseConfig(penroseConfig);
        interpreterManager.setPenroseContext(this);

        connectorManager = new ConnectorManager();
        connectorManager.setPenroseConfig(penroseConfig);
        connectorManager.setPenroseContext(this);

        partitionManager = new PartitionManager();
        partitionManager.setPenroseConfig(penroseConfig);
        partitionManager.setPenroseContext(this);

        connectionManager = new ConnectionManager();
        connectionManager.setPenroseConfig(penroseConfig);
        connectionManager.setPenroseContext(this);

        sourceManager = new SourceManager();
        sourceManager.setPenroseConfig(penroseConfig);
        sourceManager.setPenroseContext(this);

        sourceSyncManager = new SourceSyncManager();
        sourceSyncManager.setPenroseConfig(penroseConfig);
        sourceSyncManager.setPenroseContext(this);
    }

    public void load() throws Exception {
        load(penroseConfig.getHome());
    }

    public void load(String dir) throws Exception {

        for (String name : penroseConfig.getSystemPropertyNames()) {
            String value = penroseConfig.getSystemProperty(name);

            System.setProperty(name, value);
        }

        for (SchemaConfig schemaConfig : penroseConfig.getSchemaConfigs()) {
            schemaManager.init(dir, schemaConfig);
        }

        for (InterpreterConfig interpreterConfig : penroseConfig.getInterpreterConfigs()) {
            interpreterManager.init(interpreterConfig);
        }

        connectorManager.init(penroseConfig.getConnectorConfig());

        for (PartitionConfig partitionConfig : penroseConfig.getPartitionConfigs()) {
            partitionManager.load(dir, partitionConfig);
        }

        for (Partition partition : partitionManager.getPartitions()) {

            for (ConnectionConfig connectionConfig : partition.getConnectionConfigs()) {
                connectionManager.init(partition, connectionConfig);
            }

            for (SourceConfig sourceConfig : partition.getSources().getSourceConfigs()) {
                sourceManager.init(partition, sourceConfig);
            }

            for (SourceSyncConfig sourceSyncConfig : partition.getSources().getSourceSyncConfigs()) {
                sourceSyncManager.init(partition, sourceSyncConfig);
            }

            for (EntryMapping entryMapping : partition.getEntryMappings()) {
                sourceManager.init(partition, entryMapping);
            }
        }
    }


    public void store() throws Exception {

        String home = penroseConfig.getHome();
        String filename = (home == null ? "" : home+File.separator)+"conf"+File.separator+"server.xml";
        log.debug("Storing Penrose configuration into "+filename);

        PenroseConfigWriter serverConfigWriter = new PenroseConfigWriter(filename);
        serverConfigWriter.write(penroseConfig);

        partitionManager.store(home, penroseConfig.getPartitionConfigs());
    }

    public void start() throws Exception {
        connectionManager.start();
        sourceSyncManager.start();
        connectorManager.start();
        threadManager.start();
    }

    public void stop() throws Exception {
        threadManager.stop();
        connectorManager.stop();
        sourceSyncManager.stop();
        connectionManager.stop();
    }

    public void clear() throws Exception {
        connectorManager.clear();
        connectionManager.clear();
        partitionManager.clear();
        interpreterManager.clear();
        schemaManager.clear();
    }

    public PenroseConfig getPenroseConfig() {
        return penroseConfig;
    }

    public void setPenroseConfig(PenroseConfig penroseConfig) {
        this.penroseConfig = penroseConfig;
    }

    public FilterEvaluator getFilterEvaluator() {
        return filterEvaluator;
    }

    public void setFilterEvaluator(FilterEvaluator filterEvaluator) {
        this.filterEvaluator = filterEvaluator;
    }
}
