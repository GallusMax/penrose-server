/**
 * Copyright (c) 2000-2005, Identyx Corporation.
 * All rights reserved.
 */
package org.safehaus.penrose.engine;

import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.filter.Filter;
import org.safehaus.penrose.config.Config;
import org.safehaus.penrose.graph.Graph;
import org.safehaus.penrose.interpreter.Interpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.TreeSet;
import java.util.Iterator;

/**
 * @author Endi S. Dewata
 */
public class SearchEngine {

    Logger log = LoggerFactory.getLogger(getClass());

    private Engine engine;
    private EngineContext engineContext;

    public SearchEngine(Engine engine) {
        this.engine = engine;
        this.engineContext = engine.getEngineContext();
    }

    public Collection search(Entry parent, EntryDefinition entryDefinition, Filter filter) throws Exception {
        AttributeValues allValues = new AttributeValues();
        engine.getFieldValues("parent", parent, allValues);

        Collection newRows = engineContext.getTransformEngine().convert(allValues);

        Config config = engineContext.getConfig(entryDefinition.getDn());

        Graph graph = engine.getGraph(entryDefinition);
        Source primarySource = engine.getPrimarySource(entryDefinition);
        String startingSourceName = engine.getStartingSourceName(entryDefinition);
        Source startingSource = config.getEffectiveSource(entryDefinition, startingSourceName);

        Collection rows = new TreeSet();

        SearchGraphVisitor visitor = new SearchGraphVisitor(engineContext, entryDefinition, newRows, primarySource);
        graph.traverse(visitor, startingSource);
        rows.addAll(visitor.getKeys());

        return computeRdns(entryDefinition, rows);
    }

    public Collection computeRdns(EntryDefinition entryDefinition, Collection rows) throws Exception {
        Source primarySource = engine.getPrimarySource(entryDefinition);

        //log.debug("Search results:");
        TreeSet rdns = new TreeSet();

        for (Iterator j=rows.iterator(); j.hasNext(); ) {
            Row row = (Row)j.next();
            //log.debug(" - "+row);

            Interpreter interpreter = engineContext.newInterpreter();
            for (Iterator k=row.getNames().iterator(); k.hasNext(); ) {
                String name = (String)k.next();
                Object value = row.get(name);
                interpreter.set(primarySource.getName()+"."+name, value);
            }

            Collection rdnAttributes = entryDefinition.getRdnAttributes();

            Row rdn = new Row();
            boolean valid = true;

            for (Iterator k=rdnAttributes.iterator(); k.hasNext(); ) {
                AttributeDefinition attr = (AttributeDefinition)k.next();
                String name = attr.getName();
                String expression = attr.getExpression().getScript();
                Object value = interpreter.eval(expression);

                if (value == null) {
                    valid = false;
                    break;
                }

                rdn.set(name, value);
            }

            if (!valid) continue;

            Row nrdn = engineContext.getSchema().normalize(rdn);
            //log.debug(" - RDN: "+nrdn);

            rdns.add(nrdn);
        }

        return rdns;
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public EngineContext getEngineContext() {
        return engineContext;
    }

    public void setEngineContext(EngineContext engineContext) {
        this.engineContext = engineContext;
    }
}