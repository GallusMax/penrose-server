/**
 * Copyright (c) 2000-2005, Identyx Corporation.
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
package org.safehaus.penrose.engine;

import org.safehaus.penrose.SearchResults;
import org.safehaus.penrose.*;
import org.safehaus.penrose.util.Formatter;
import org.safehaus.penrose.config.Config;
import org.safehaus.penrose.filter.Filter;
import org.safehaus.penrose.filter.SimpleFilter;
import org.safehaus.penrose.filter.OrFilter;
import org.safehaus.penrose.filter.AndFilter;
import org.safehaus.penrose.interpreter.Interpreter;
import org.safehaus.penrose.graph.Graph;
import org.safehaus.penrose.graph.GraphEdge;
import org.safehaus.penrose.thread.ThreadPool;
import org.safehaus.penrose.thread.Queue;
import org.safehaus.penrose.thread.MRSWLock;
import org.safehaus.penrose.mapping.*;
import org.apache.log4j.Logger;
import org.ietf.ldap.LDAPException;

import javax.naming.directory.SearchResult;
import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class Engine {

    Logger log = Logger.getLogger(getClass());

    private EngineConfig engineConfig;
    private EngineContext engineContext;

    private Map graphs = new HashMap();
    private Map primarySources = new HashMap();

    private Map locks = new HashMap();
    private Queue queue = new Queue();

    private ThreadPool threadPool;

    private boolean stopping = false;

    private EngineFilterTool filterTool;
    private SearchEngine searchEngine;
    private LoadEngine loadEngine;
    private MergeEngine mergeEngine;
    private JoinEngine joinEngine;

    /**
     * Initialize the engine with a Penrose instance
     *
     * @param engineContext
     * @throws Exception
     */
    public void init(EngineConfig engineConfig, EngineContext engineContext) throws Exception {
        this.engineConfig = engineConfig;
        this.engineContext = engineContext;

        log.debug("-------------------------------------------------");
        log.debug("Initializing "+engineConfig.getEngineName()+" engine ...");

        String s = engineConfig.getParameter(EngineConfig.THREAD_POOL_SIZE);
        int threadPoolSize = s == null ? EngineConfig.DEFAULT_THREAD_POOL_SIZE : Integer.parseInt(s);

        filterTool = new EngineFilterTool(engineContext);

        searchEngine = new SearchEngine(this);
        loadEngine = new LoadEngine(this);
        mergeEngine = new MergeEngine(this);
        joinEngine = new JoinEngine(this);

        threadPool = new ThreadPool(threadPoolSize);

        execute(new RefreshThread(this));
    }

    public void analyze(Config config) throws Exception {

        for (Iterator i=config.getRootEntryDefinitions().iterator(); i.hasNext(); ) {
            EntryDefinition entryDefinition = (EntryDefinition)i.next();
            analyze(entryDefinition);
        }
    }

    public void analyze(EntryDefinition entryDefinition) throws Exception {

        //log.debug("Entry "+entryDefinition.getDn()+":");

        Source source = computePrimarySource(entryDefinition);
        if (source != null) {
            primarySources.put(entryDefinition, source);
            //log.debug(" - primary source: "+source);
        }

        Graph graph = computeGraph(entryDefinition);
        if (graph != null) {
            graphs.put(entryDefinition, graph);
            //log.debug(" - graph: "+graph);
        }

        Config config = engineContext.getConfig(entryDefinition.getDn());
        Collection children = config.getChildren(entryDefinition);
        if (children != null) {
            for (Iterator i=children.iterator(); i.hasNext(); ) {
                EntryDefinition childDefinition = (EntryDefinition)i.next();
                analyze(childDefinition);
            }
        }
	}

    public Source getPrimarySource(EntryDefinition entryDefinition) throws Exception {
        Source source = (Source)primarySources.get(entryDefinition);
        if (source != null) return source;

        Config config = engineContext.getConfig(entryDefinition.getDn());
        entryDefinition = config.getParent(entryDefinition);
        if (entryDefinition == null) return null;

        return getPrimarySource(entryDefinition);
    }

    Source computePrimarySource(EntryDefinition entryDefinition) throws Exception {

        Collection rdnAttributes = entryDefinition.getRdnAttributes();

        // TODO need to handle multiple rdn attributes
        AttributeDefinition rdnAttribute = (AttributeDefinition)rdnAttributes.iterator().next();

        if (rdnAttribute.getConstant() == null) {
            String variable = rdnAttribute.getVariable();
            if (variable != null) {
                int i = variable.indexOf(".");
                String sourceName = variable.substring(0, i);
                Source source = entryDefinition.getSource(sourceName);
                return source;
            }

            Interpreter interpreter = engineContext.newInterpreter();

            Expression expression = rdnAttribute.getExpression();
            String foreach = expression.getForeach();
            if (foreach != null) {
                int i = foreach.indexOf(".");
                String sourceName = foreach.substring(0, i);
                Source source = entryDefinition.getSource(sourceName);
                return source;
            }

            Collection variables = interpreter.parseVariables(expression.getScript());

            for (Iterator i=variables.iterator(); i.hasNext(); ) {
                String sourceName = (String)i.next();
                Source source = entryDefinition.getSource(sourceName);
                if (source != null) return source;
            }
        }

        Collection sources = entryDefinition.getSources();
        if (sources.isEmpty()) return null;

        return (Source)sources.iterator().next();
    }

    public Map getGraphs() {
        return graphs;
    }

    public void setGraphs(Map graphs) {
        this.graphs = graphs;
    }

    public Map getPrimarySources() {
        return primarySources;
    }

    public void setPrimarySources(Map primarySources) {
        this.primarySources = primarySources;
    }

    public Graph getGraph(EntryDefinition entryDefinition) throws Exception {

        return (Graph)graphs.get(entryDefinition);
    }

    Graph computeGraph(EntryDefinition entryDefinition) throws Exception {

        //log.debug("Graph for "+entryDefinition.getDn()+":");

        //if (entryDefinition.getSources().isEmpty()) return null;

        Graph graph = new Graph();

        Config config = engineContext.getConfig(entryDefinition.getDn());
        Collection sources = config.getEffectiveSources(entryDefinition);
        //if (sources.size() == 0) return null;

        for (Iterator i=sources.iterator(); i.hasNext(); ) {
            Source source = (Source)i.next();
            graph.addNode(source);
        }

        Collection relationships = config.getEffectiveRelationships(entryDefinition);
        for (Iterator i=relationships.iterator(); i.hasNext(); ) {
            Relationship relationship = (Relationship)i.next();
            //log.debug("Checking ["+relationship.getExpression()+"]");

            String lhs = relationship.getLhs();
            int lindex = lhs.indexOf(".");
            if (lindex < 0) continue;

            String lsourceName = lhs.substring(0, lindex);
            Source lsource = config.getEffectiveSource(entryDefinition, lsourceName);
            if (lsource == null) continue;

            String rhs = relationship.getRhs();
            int rindex = rhs.indexOf(".");
            if (rindex < 0) continue;

            String rsourceName = rhs.substring(0, rindex);
            Source rsource = config.getEffectiveSource(entryDefinition, rsourceName);
            if (rsource == null) continue;

            Set nodes = new HashSet();
            nodes.add(lsource);
            nodes.add(rsource);

            GraphEdge edge = graph.getEdge(nodes);
            if (edge == null) {
                edge = new GraphEdge(lsource, rsource, new ArrayList());
                graph.addEdge(edge);
            }

            Collection list = (Collection)edge.getObject();
            list.add(relationship);
        }

        Collection edges = graph.getEdges();
        for (Iterator i=edges.iterator(); i.hasNext(); ) {
            GraphEdge edge = (GraphEdge)i.next();
            //log.debug(" - "+edge);
        }

        return graph;
    }

    public void execute(Runnable runnable) throws Exception {
        threadPool.execute(runnable);
    }

    public void stop() throws Exception {
        if (stopping) return;
        stopping = true;

        // wait for all the worker threads to finish
        threadPool.stopRequestAllWorkers();
    }

    public boolean isStopping() {
        return stopping;
    }

	public synchronized MRSWLock getLock(String dn) {

		MRSWLock lock = (MRSWLock)locks.get(dn);

		if (lock == null) lock = new MRSWLock(queue);
		locks.put(dn, lock);

		return lock;
	}

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public EngineContext getEngineContext() {
        return engineContext;
    }

    public void setEngineContext(EngineContext engineContext) {
        this.engineContext = engineContext;
    }

    public int add(
            Entry parent,
            EntryDefinition entryDefinition,
            AttributeValues attributeValues)
            throws Exception {

        AttributeValues sourceValues = parent.getSourceValues();

        Collection sources = entryDefinition.getSources();
        for (Iterator i=sources.iterator(); i.hasNext(); ) {
            Source source = (Source)i.next();

            AttributeValues output = new AttributeValues();
            Row pk = engineContext.getTransformEngine().translate(source, attributeValues, output);
            if (pk == null) continue;

            log.debug(" - "+pk+": "+output);
            for (Iterator j=output.getNames().iterator(); j.hasNext(); ) {
                String name = (String)j.next();
                Collection values = output.get(name);
                sourceValues.add(source.getName()+"."+name, values);
            }
        }

        log.debug("Adding entry "+entryDefinition.getRdn()+","+parent.getDn()+" with values: "+sourceValues);

        Config config = engineContext.getConfig(entryDefinition.getDn());

        Graph graph = getGraph(entryDefinition);
        Source primarySource = getPrimarySource(entryDefinition);
        String startingSourceName = getStartingSourceName(entryDefinition);
        if (startingSourceName == null) return LDAPException.SUCCESS;

        Source startingSource = config.getEffectiveSource(entryDefinition, startingSourceName);
        log.debug("Starting from source: "+startingSourceName);

        Collection relationships = graph.getEdgeObjects(startingSource);
        for (Iterator i=relationships.iterator(); i.hasNext(); ) {
            Collection list = (Collection)i.next();

            for (Iterator j=list.iterator(); j.hasNext(); ) {
                Relationship relationship = (Relationship)j.next();
                log.debug("Relationship "+relationship);

                String lhs = relationship.getLhs();
                String rhs = relationship.getRhs();

                if (rhs.startsWith(startingSourceName+".")) {
                    String exp = lhs;
                    lhs = rhs;
                    rhs = exp;
                }

                Collection lhsValues = sourceValues.get(lhs);
                log.debug(" - "+lhs+" -> "+rhs+": "+lhsValues);
                sourceValues.set(rhs, lhsValues);
            }
        }

        AddGraphVisitor visitor = new AddGraphVisitor(engineContext, entryDefinition, sourceValues);
        graph.traverse(visitor, primarySource);

        if (visitor.getReturnCode() != LDAPException.SUCCESS) return visitor.getReturnCode();

        engineContext.getEntryFilterCache(parent == null ? null : parent.getDn(), entryDefinition).invalidate();

        return LDAPException.SUCCESS;
    }

    public int delete(Entry entry) throws Exception {

        EntryDefinition entryDefinition = entry.getEntryDefinition();

        AttributeValues sourceValues = entry.getSourceValues();
        //getFieldValues(entry.getDn(), sourceValues);

        Graph graph = getGraph(entryDefinition);
        Source primarySource = getPrimarySource(entryDefinition);

        log.debug("Deleting entry "+entry.getDn()+" ["+sourceValues+"]");

        DeleteGraphVisitor visitor = new DeleteGraphVisitor(engineContext, entryDefinition, sourceValues);
        graph.traverse(visitor, primarySource);

        if (visitor.getReturnCode() != LDAPException.SUCCESS) return visitor.getReturnCode();

        engineContext.getEntryDataCache(entry.getParentDn(), entryDefinition).remove(entry.getRdn());
        engineContext.getEntryFilterCache(entry.getParentDn(), entryDefinition).invalidate();

        return LDAPException.SUCCESS;
    }

    public int modrdn(Entry entry, String newRdn) throws Exception {

        EntryDefinition entryDefinition = entry.getEntryDefinition();
        AttributeValues oldAttributeValues = entry.getAttributeValues();
        AttributeValues oldSourceValues = entry.getSourceValues();

        Row rdn = Entry.getRdn(newRdn);

        AttributeValues newAttributeValues = new AttributeValues(oldAttributeValues);
        Collection rdnAttributes = entryDefinition.getRdnAttributes();
        for (Iterator i=rdnAttributes.iterator(); i.hasNext(); ) {
            AttributeDefinition attributeDefinition = (AttributeDefinition)i.next();
            String name = attributeDefinition.getName();
            String newValue = (String)rdn.get(name);

            newAttributeValues.remove(name);
            newAttributeValues.add(name, newValue);
        }

        AttributeValues newSourceValues = new AttributeValues(oldSourceValues);
        Collection sources = entryDefinition.getSources();
        for (Iterator i=sources.iterator(); i.hasNext(); ) {
            Source source = (Source)i.next();

            AttributeValues output = new AttributeValues();
            engineContext.getTransformEngine().translate(source, newAttributeValues, output);
            newSourceValues.set(source.getName(), output);
        }

        log.debug(Formatter.displaySeparator(80));
        log.debug(Formatter.displayLine("MODRDN", 80));
        log.debug(Formatter.displayLine("Entry: "+entry.getDn(), 80));
        log.debug(Formatter.displayLine("New RDN: "+newRdn, 80));

        log.debug(Formatter.displayLine("Attribute values:", 80));
        for (Iterator iterator = newAttributeValues.getNames().iterator(); iterator.hasNext(); ) {
            String name = (String)iterator.next();
            Collection values = newAttributeValues.get(name);
            log.debug(Formatter.displayLine(" - "+name+": "+values, 80));
        }

        log.debug(Formatter.displayLine("Old source values:", 80));
        for (Iterator iterator = oldSourceValues.getNames().iterator(); iterator.hasNext(); ) {
            String name = (String)iterator.next();
            Collection values = oldSourceValues.get(name);
            log.debug(Formatter.displayLine(" - "+name+": "+values, 80));
        }

        log.debug(Formatter.displayLine("New source values:", 80));
        for (Iterator iterator = newSourceValues.getNames().iterator(); iterator.hasNext(); ) {
            String name = (String)iterator.next();
            Collection values = newSourceValues.get(name);
            log.debug(Formatter.displayLine(" - "+name+": "+values, 80));
        }

        log.debug(Formatter.displaySeparator(80));

        ModRdnGraphVisitor visitor = new ModRdnGraphVisitor(this, entryDefinition, oldSourceValues, newSourceValues);
        visitor.run();

        if (visitor.getReturnCode() != LDAPException.SUCCESS) return visitor.getReturnCode();

        engineContext.getEntryDataCache(entry.getParentDn(), entryDefinition).remove(entry.getRdn());
        engineContext.getEntryFilterCache(entry.getParentDn(), entryDefinition).invalidate();

        return LDAPException.SUCCESS;
    }

    public int modify(Entry entry, AttributeValues oldValues, AttributeValues newValues) throws Exception {

        EntryDefinition entryDefinition = entry.getEntryDefinition();
        AttributeValues oldSourceValues = entry.getSourceValues();

        AttributeValues newSourceValues = (AttributeValues)oldSourceValues.clone();
        Collection sources = entryDefinition.getSources();
        for (Iterator i=sources.iterator(); i.hasNext(); ) {
            Source source = (Source)i.next();

            AttributeValues output = new AttributeValues();
            engineContext.getTransformEngine().translate(source, newValues, output);
            newSourceValues.set(source.getName(), output);
        }

        log.debug(Formatter.displaySeparator(80));
        log.debug(Formatter.displayLine("MODIFY", 80));
        log.debug(Formatter.displayLine("Entry: "+entry.getDn(), 80));

        log.debug(Formatter.displayLine("Old source values:", 80));
        for (Iterator iterator = oldSourceValues.getNames().iterator(); iterator.hasNext(); ) {
            String name = (String)iterator.next();
            Collection values = oldSourceValues.get(name);
            log.debug(Formatter.displayLine(" - "+name+": "+values, 80));
        }

        log.debug(Formatter.displayLine("New source values:", 80));
        for (Iterator iterator = newSourceValues.getNames().iterator(); iterator.hasNext(); ) {
            String name = (String)iterator.next();
            Collection values = newSourceValues.get(name);
            log.debug(Formatter.displayLine(" - "+name+": "+values, 80));
        }

        log.debug(Formatter.displaySeparator(80));

        Graph graph = getGraph(entryDefinition);
        Source primarySource = getPrimarySource(entryDefinition);

        ModifyGraphVisitor visitor = new ModifyGraphVisitor(engineContext, entryDefinition, oldSourceValues, newSourceValues);
        graph.traverse(visitor, primarySource);

        if (visitor.getReturnCode() != LDAPException.SUCCESS) return visitor.getReturnCode();

        engineContext.getEntryDataCache(entry.getParentDn(), entryDefinition).remove(entry.getRdn());
        engineContext.getEntryFilterCache(entry.getParentDn(), entryDefinition).invalidate();

        return LDAPException.SUCCESS;
    }


    public Entry find(
            PenroseConnection connection,
            Collection path,
            EntryDefinition entryDefinition
            ) throws Exception {
/*
        AttributeValues attributeValues = entryDefinition.getAttributeValues(engineContext.newInterpreter());
        Entry entry = new Entry(entryDefinition.getDn(), entryDefinition, attributeValues);

        SearchResults entries = new SearchResults();
        entries.add(entry);
        entries.close();
*/
        SearchResults entries = new SearchResults();

        searchEngine.search(path, entryDefinition, null, entries);

        SearchResults results = new SearchResults();

        load(entryDefinition, entries, results);

        if (results.size() == 0) return null;

        return (Entry)results.next();
    }
    
    public SearchResults search(
            PenroseConnection connection,
            final Collection path,
            final EntryDefinition entryDefinition,
            final Filter filter,
            Collection attributeNames) throws Exception {

        final SearchResults entries = new SearchResults();

        if (false) {
            searchEngine.search(path, entryDefinition, filter, entries);

        } else {
            execute(new Runnable() {
                public void run() {
                    try {
                        searchEngine.search(path, entryDefinition, filter, entries);

                    } catch (Throwable e) {
                        e.printStackTrace(System.out);
                        entries.setReturnCode(LDAPException.OPERATIONS_ERROR);
                    }
                }
            });
        }

        SearchResults results = new SearchResults();

        Collection attributeDefinitions = entryDefinition.getAttributeDefinitions(attributeNames);
        log.debug("Attribute definitions: "+attributeDefinitions);

        // check if client only requests the dn to be returned
        if (attributeNames.contains("dn")
                && attributeDefinitions.isEmpty()
                && "(objectclass=*)".equals(filter.toString().toLowerCase())) {

            for (Iterator i=entries.iterator(); i.hasNext(); ) {
                Entry entry = (Entry)i.next();
                results.add(entry);
            }

            results.close();
            return results;
        }

        load(entryDefinition, entries, results);

        return results;
    }

    public void load(
            final EntryDefinition entryDefinition,
            final SearchResults entries,
            final SearchResults results)
            throws Exception {

        final SearchResults batches = new SearchResults();

        if (false) {
            createBatches(entryDefinition, entries, results, batches);

        } else {
            execute(new Runnable() {
                public void run() {
                    try {
                        createBatches(entryDefinition, entries, results, batches);

                    } catch (Throwable e) {
                        e.printStackTrace(System.out);
                        batches.setReturnCode(LDAPException.OPERATIONS_ERROR);
                    }
                }
            });
        }

        final SearchResults loadedBatches = new SearchResults();

        if (false) {
            loadEngine.load(entryDefinition, batches, loadedBatches);

        } else {
            execute(new Runnable() {
                public void run() {
                    try {
                        loadEngine.load(entryDefinition, batches, loadedBatches);

                    } catch (Throwable e) {
                        e.printStackTrace(System.out);
                        loadedBatches.setReturnCode(LDAPException.OPERATIONS_ERROR);
                    }
                }
            });
        }

        if (false) {
            mergeEngine.merge(entryDefinition, loadedBatches, results);

        } else {
            execute(new Runnable() {
                public void run() {
                    try {
                        mergeEngine.merge(entryDefinition, loadedBatches, results);

                    } catch (Throwable e) {
                        e.printStackTrace(System.out);
                        results.setReturnCode(LDAPException.OPERATIONS_ERROR);
                    }
                }
            });
        }
    }

    public void createBatches(
            EntryDefinition entryDefinition,
            SearchResults entries,
            SearchResults results,
            SearchResults batches
            ) throws Exception {

        try {
            Source primarySource = getPrimarySource(entryDefinition);

            Collection batch = new ArrayList();

            String s = entryDefinition.getParameter(EntryDefinition.BATCH_SIZE);
            int batchSize = s == null ? EntryDefinition.DEFAULT_BATCH_SIZE : Integer.parseInt(s);

            for (Iterator i=entries.iterator(); i.hasNext(); ) {
                Entry e = (Entry)i.next();
                String dn = e.getDn();
                AttributeValues sv = e.getSourceValues();

                Row rdn = Entry.getRdn(dn);
                String parentDn = Entry.getParentDn(dn);

                log.debug("Checking "+rdn+" in entry data cache for "+parentDn);
                Entry entry = (Entry)engineContext.getEntryDataCache(parentDn, entryDefinition).get(rdn);

                if (entry != null) {
                    log.debug(" - "+rdn+" has been loaded");
                    results.add(entry);
                    continue;
                }

                Row filter = createFilter(primarySource, entryDefinition, rdn);
                if (filter == null) continue;

                //if (filter.isEmpty()) filter.add(rdn);

                log.debug("- "+rdn+" has not been loaded, loading with key "+filter);
                Map m = new HashMap();
                m.put("dn", dn);
                m.put("sourceValues", sv);
                m.put("filter", filter);
                batch.add(m);

                if (batch.size() < batchSize) continue;

                batches.add(batch);
                batch = new ArrayList();
            }

            if (!batch.isEmpty()) batches.add(batch);

        } finally {
            batches.close();
        }
    }

    public String getStartingSourceName(EntryDefinition entryDefinition) throws Exception {

        log.debug("Searching the starting source for "+entryDefinition.getDn());

        Config config = engineContext.getConfig(entryDefinition.getDn());

        Collection relationships = entryDefinition.getRelationships();
        for (Iterator i=relationships.iterator(); i.hasNext(); ) {
            Relationship relationship = (Relationship)i.next();

            for (Iterator j=relationship.getOperands().iterator(); j.hasNext(); ) {
                String operand = j.next().toString();

                int index = operand.indexOf(".");
                if (index < 0) continue;

                String sourceName = operand.substring(0, index);
                Source source = entryDefinition.getSource(sourceName);
                Source effectiveSource = config.getEffectiveSource(entryDefinition, sourceName);

                if (source == null && effectiveSource != null) {
                    log.debug("Source "+sourceName+" is defined in parent entry");
                    return sourceName;
                }

            }
        }

        Iterator i = entryDefinition.getSources().iterator();
        if (!i.hasNext()) return null;

        Source source = (Source)i.next();
        log.debug("Source "+source.getName()+" is the first defined in entry");
        return source.getName();
    }

    public Relationship getConnectingRelationship(EntryDefinition entryDefinition) throws Exception {

        // log.debug("Searching the connecting relationship for "+entryDefinition.getDn());

        Config config = engineContext.getConfig(entryDefinition.getDn());

        Collection relationships = config.getEffectiveRelationships(entryDefinition);

        for (Iterator i=relationships.iterator(); i.hasNext(); ) {
            Relationship relationship = (Relationship)i.next();
            // log.debug(" - checking "+relationship);

            String lhs = relationship.getLhs();
            String rhs = relationship.getRhs();

            int lindex = lhs.indexOf(".");
            String lsourceName = lindex < 0 ? lhs : lhs.substring(0, lindex);
            Source lsource = entryDefinition.getSource(lsourceName);

            int rindex = rhs.indexOf(".");
            String rsourceName = rindex < 0 ? rhs : rhs.substring(0, rindex);
            Source rsource = entryDefinition.getSource(rsourceName);

            if (lsource == null && rsource != null || lsource != null && rsource == null) {
                return relationship;
            }
        }

        return null;
    }

    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    public void setSearchEngine(SearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    public MergeEngine getMergeEngine() {
        return mergeEngine;
    }

    public void setJoinEngine(MergeEngine mergeEngine) {
        this.mergeEngine = mergeEngine;
    }

    public EngineFilterTool getFilterTool() {
        return filterTool;
    }

    public void setFilterTool(EngineFilterTool filterTool) {
        this.filterTool = filterTool;
    }

    public Filter createFilter(Source source, Collection pks) throws Exception {

        Collection normalizedFilters = null;
        if (pks != null) {
            normalizedFilters = new TreeSet();
            for (Iterator i=pks.iterator(); i.hasNext(); ) {
                Row filter = (Row)i.next();

                Row f = new Row();
                for (Iterator j=filter.getNames().iterator(); j.hasNext(); ) {
                    String name = (String)j.next();
                    if (!name.startsWith(source.getName()+".")) continue;

                    String newName = name.substring(source.getName().length()+1);
                    f.set(newName, filter.get(name));
                }

                if (f.isEmpty()) continue;

                Row normalizedFilter = engineContext.getSchema().normalize(f);
                normalizedFilters.add(normalizedFilter);
            }
        }

        Filter filter = null;
        if (pks != null) {
            filter = engineContext.getFilterTool().createFilter(normalizedFilters);
        }

        return filter;
    }

    public Row createFilter(Source source, EntryDefinition entryDefinition, Row rdn) throws Exception {

        if (source == null) {
            return new Row();
        }

        Config config = engineContext.getConfig(entryDefinition.getDn());
        Collection fields = config.getSearchableFields(source);

        Interpreter interpreter = engineContext.newInterpreter();
        interpreter.set(rdn);

        Row filter = new Row();
        for (Iterator j=fields.iterator(); j.hasNext(); ) {
            Field field = (Field)j.next();
            String name = field.getName();

            Object value = interpreter.eval(field);
            if (value == null) continue;

            //log.debug("   ==> "+field.getName()+"="+value);
            //filter.set(source.getName()+"."+name, value);
            filter.set(name, value);
        }

        //if (filter.isEmpty()) return null;

        return filter;
    }

    public Filter generateFilter(Source source, Collection relationships, Collection rows) throws Exception {
        log.debug("Generating filters for source "+source.getName());

        Filter filter = null;
        for (Iterator i=rows.iterator(); i.hasNext(); ) {
            Row row = (Row)i.next();
            log.debug(" - "+row);

            Filter subFilter = null;

            for (Iterator j=relationships.iterator(); j.hasNext(); ) {
                Relationship relationship = (Relationship)j.next();

                String lhs = relationship.getLhs();
                String operator = relationship.getOperator();
                String rhs = relationship.getRhs();

                if (rhs.startsWith(source.getName()+".")) {
                    String exp = lhs;
                    lhs = rhs;
                    rhs = exp;
                }

                int index = lhs.indexOf(".");
                String name = lhs.substring(index+1);

                log.debug("   - "+rhs+" ==> ("+name+" "+operator+" ?)");
                Object value = row.get(rhs);
                if (value == null) continue;

                SimpleFilter sf = new SimpleFilter(name, operator, value.toString());
                log.debug("     --> "+sf);

                subFilter = engineContext.getFilterTool().appendAndFilter(subFilter, sf);
            }

            filter = engineContext.getFilterTool().appendOrFilter(filter, subFilter);
        }

        return filter;
    }

    public Filter generateFilter(Source toSource, Collection relationships, AttributeValues av) throws Exception {
        log.debug("Filters:");

        Filter filter = null;

        for (Iterator j=relationships.iterator(); j.hasNext(); ) {
            Relationship relationship = (Relationship)j.next();
            log.debug(" - "+relationship);

            String lhs = relationship.getLhs();
            String operator = relationship.getOperator();
            String rhs = relationship.getRhs();

            if (rhs.startsWith(toSource.getName()+".")) {
                String exp = lhs;
                lhs = rhs;
                rhs = exp;
            }

            int lindex = lhs.indexOf(".");
            String lsourceName = lhs.substring(0, lindex);
            String lname = lhs.substring(lindex+1);

            int rindex = rhs.indexOf(".");
            String rsourceName = rhs.substring(0, rindex);
            String rname = rhs.substring(rindex+1);

            log.debug("   converting "+rhs+" ==> ("+lname+" "+operator+" ?)");

            Collection v = av.get(rhs);
            log.debug("   - found "+v);
            if (v == null) continue;

            Filter orFilter = null;
            for (Iterator k=v.iterator(); k.hasNext(); ) {
                Object value = k.next();

                SimpleFilter sf = new SimpleFilter(lname, operator, value.toString());
                log.debug("   - "+sf);

                orFilter = engineContext.getFilterTool().appendOrFilter(orFilter, sf);
            }

            filter = engineContext.getFilterTool().appendAndFilter(filter, orFilter);
        }

        return filter;
    }

    public Collection computeDns(
            EntryDefinition entryDefinition,
            AttributeValues sourceValues)
            throws Exception {

        Interpreter interpreter = engineContext.newInterpreter();
        interpreter.set(sourceValues);

        Collection results = new ArrayList();

        results.add(computeDns(entryDefinition, interpreter));

        return results;
    }

    public String computeDns(EntryDefinition entryDefinition, Interpreter interpreter) throws Exception {

        Row rdn = computeRdn(entryDefinition, interpreter);

        Config config = engineContext.getConfig(entryDefinition.getDn());
        EntryDefinition parentDefinition = config.getParent(entryDefinition);

        String parentDn;
        if (parentDefinition != null) {
            parentDn = computeDns(parentDefinition, interpreter);
        } else {
            parentDn = entryDefinition.getParentDn();
        }

        return rdn +(parentDn == null ? "" : ","+parentDn);
    }

    public Row computeRdn(
            EntryDefinition entryDefinition,
            Interpreter interpreter)
            throws Exception {

        Row rdn = new Row();

        Collection rdnAttributes = entryDefinition.getRdnAttributes();
        for (Iterator i=rdnAttributes.iterator(); i.hasNext(); ) {
            AttributeDefinition attributeDefinition = (AttributeDefinition)i.next();
            String name = attributeDefinition.getName();

            Object value = interpreter.eval(attributeDefinition);
            if (value == null) return null;

            rdn.set(name, value);
        }

        return rdn;
    }

    public AttributeValues computeAttributeValues(
            EntryDefinition entryDefinition,
            AttributeValues sourceValues)
            throws Exception {

        AttributeValues attributeValues = new AttributeValues();

        Interpreter interpreter = engineContext.newInterpreter();
        interpreter.set(sourceValues);

        Collection attributeDefinitions = entryDefinition.getAttributeDefinitions();
        for (Iterator j=attributeDefinitions.iterator(); j.hasNext(); ) {
            AttributeDefinition attributeDefinition = (AttributeDefinition)j.next();

            Object value = interpreter.eval(attributeDefinition);
            if (value == null) continue;

            String name = attributeDefinition.getName();
            attributeValues.add(name, value);
        }

        return attributeValues;
    }

    public LoadEngine getLoadEngine() {
        return loadEngine;
    }

    public void setLoadEngine(LoadEngine loadEngine) {
        this.loadEngine = loadEngine;
    }

    public JoinEngine getJoinEngine() {
        return joinEngine;
    }

    public void setJoinEngine(JoinEngine joinEngine) {
        this.joinEngine = joinEngine;
    }

    public EngineConfig getEngineConfig() {
        return engineConfig;
    }

    public void setEngineConfig(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }
}

