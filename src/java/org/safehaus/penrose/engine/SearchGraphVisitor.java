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

import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.filter.Filter;
import org.safehaus.penrose.filter.AndFilter;
import org.safehaus.penrose.config.Config;
import org.safehaus.penrose.graph.GraphVisitor;
import org.safehaus.penrose.graph.Graph;
import org.safehaus.penrose.graph.GraphIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class SearchGraphVisitor extends GraphVisitor {

    Logger log = LoggerFactory.getLogger(getClass());

    private Config config;
    private Graph graph;
    private Engine engine;
    private EngineContext engineContext;
    private EntryDefinition entryDefinition;
    private Filter searchFilter;
    private Source primarySource;

    private Stack stack = new Stack();
    private Set keys = new HashSet();

    public SearchGraphVisitor(
            Config config,
            Graph graph,
            Engine engine,
            EntryDefinition entryDefinition,
            Filter initialFilter,
            Filter searchFilter,
            Source primarySource) throws Exception {

        this.config = config;
        this.graph = graph;
        this.engine = engine;
        this.engineContext = engine.getEngineContext();
        this.entryDefinition = entryDefinition;
        this.searchFilter = searchFilter;
        this.primarySource = primarySource;

        stack.push(initialFilter);
    }

    public void visitNode(GraphIterator graphIterator, Object node) throws Exception {

        Source source = (Source)node;
        Filter filter = (Filter)stack.peek();
        log.debug("Searching "+source.getName()+" for: "+filter);

        if (entryDefinition.getSource(source.getName()) == null) {
            log.debug("Source "+source.getName()+" is not defined in entry "+entryDefinition.getDn());
            return;
        }

        Filter f = engine.getFilterTool().toSourceFilter(null, entryDefinition, source, searchFilter);
        log.debug("Search filter: "+f);

        filter = engineContext.getFilterTool().appendAndFilter(filter, f);

        String s = source.getParameter(Source.FILTER);
        if (s != null) {
            Filter sourceFilter = engineContext.getFilterTool().parseFilter(s);
            filter = engineContext.getFilterTool().appendAndFilter(filter, sourceFilter);
        }

        Map map = engine.getEngineContext().getSyncService().search(source, filter);
        if (map.size() == 0) return;

        log.debug("Records:");
        Collection results = new ArrayList();
        for (Iterator i=map.values().iterator(); i.hasNext(); ) {
            AttributeValues av = (AttributeValues)i.next();
            Collection list = engine.getEngineContext().getTransformEngine().convert(av);
            for (Iterator j=list.iterator(); j.hasNext(); ) {
                Row row = (Row)j.next();
                Row newRow = new Row();
                for (Iterator k=row.getNames().iterator(); k.hasNext(); ) {
                    String name = (String)k.next();
                    Object value = row.get(name);
                    if (value == null) continue;
                    newRow.set(source.getName()+"."+name, value);
                }
                log.debug(" - "+newRow);
                results.add(newRow);
            }
        }

        if (source == primarySource) {
            log.debug("Source "+source.getName()+" is the primary source of entry "+entryDefinition.getDn());
            keys.addAll(results);
            return;
        }

        stack.push(results);

        graphIterator.traverseEdges(node);

        stack.pop();
    }

    public void visitEdge(GraphIterator graphIterator, Object node1, Object node2, Object object) throws Exception {

        Relationship relationship = (Relationship)object;
        log.debug("Relationship "+relationship);

        Source fromSource = (Source)node1;
        Source toSource = (Source)node2;

        if (entryDefinition.getSource(toSource.getName()) == null) {
            log.debug("Source "+toSource.getName()+" is not defined in entry "+entryDefinition.getDn());
            return;
        }

        Collection rows = (Collection)stack.peek();

        Collection relationships = new ArrayList();
        relationships.add(relationship);

        Filter filter = engine.generateFilter(toSource, relationships, rows);
        if (filter == null) return;

        stack.push(filter);

        graphIterator.traverse(node2);

        stack.pop();
    }

    public EntryDefinition getEntryDefinition() {
        return entryDefinition;
    }

    public void setEntryDefinition(EntryDefinition entryDefinition) {
        this.entryDefinition = entryDefinition;
    }

    public Stack getStack() {
        return stack;
    }

    public void setStack(Stack stack) {
        this.stack = stack;
    }

    public Set getKeys() {
        return keys;
    }

    public void setKeys(Set keys) {
        this.keys = keys;
    }

    public Source getPrimarySource() {
        return primarySource;
    }

    public void setPrimarySource(Source primarySource) {
        this.primarySource = primarySource;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }
}
