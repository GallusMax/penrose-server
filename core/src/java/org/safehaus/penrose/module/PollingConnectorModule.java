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
package org.safehaus.penrose.module;

import org.safehaus.penrose.source.SourceConfig;
import org.safehaus.penrose.connection.ConnectionConfig;
import org.safehaus.penrose.session.PenroseSearchResults;
import org.safehaus.penrose.session.PenroseSearchControls;
import org.safehaus.penrose.session.PenroseSession;
import org.safehaus.penrose.mapping.*;
import org.safehaus.penrose.cache.EntryCacheManager;
import org.safehaus.penrose.cache.SourceCache;
import org.safehaus.penrose.cache.EntryCache;
import org.safehaus.penrose.interpreter.Interpreter;
import org.safehaus.penrose.engine.Engine;
import org.safehaus.penrose.connection.Connection;
import org.safehaus.penrose.connection.ConnectionManager;
import org.safehaus.penrose.source.Source;
import org.safehaus.penrose.source.SourceManager;
import org.safehaus.penrose.config.PenroseConfig;

import java.util.Collection;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Map;

/**
 * @author Endi S. Dewata
 */
public class PollingConnectorModule extends Module {

    public final static String INTERVAL   = "interval";

    public final static int DEFAULT_INTERVAL = 5; // seconds

    public int interval; // 1 second

    public SourceManager sourceManager;
    public Engine engine;

    public PollingConnectorRunnable runnable;

    public void init() throws Exception {

        log.debug("Initializing PollingConnectorModule");

        String s = getParameter(INTERVAL);
        interval = s == null ? DEFAULT_INTERVAL : Integer.parseInt(s);
        log.debug("Interval: "+interval);

        sourceManager = penrose.getSourceManager();
        engine = penrose.getEngine();
    }

    public void start() throws Exception {
        runnable = new PollingConnectorRunnable(this);

        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void stop() throws Exception {
        runnable.stop();
    }

    public void process() throws Exception {

        //log.debug("Refreshing cache ...");

        Collection sourceDefinitions = partition.getSourceConfigs();
        for (Iterator i=sourceDefinitions.iterator(); i.hasNext(); ) {
            SourceConfig sourceConfig = (SourceConfig)i.next();
/*
            String s = sourceConfig.getParameter(SourceConfig.AUTO_REFRESH);
            boolean autoRefresh = s == null ? SourceConfig.DEFAULT_AUTO_REFRESH : new Boolean(s).booleanValue();

            if (!autoRefresh) continue;
*/
            pollChanges(sourceConfig);
        }
    }

    public void reloadExpired(SourceConfig sourceConfig) throws Exception {

        Source source = sourceManager.getSource(partition.getName(), sourceConfig.getName());
        SourceCache sourceCache = source.getSourceCache();
        Map map = sourceCache.getExpired();

        log.debug("Reloading expired caches...");

        for (Iterator i=map.keySet().iterator(); i.hasNext(); ) {
            Row pk = (Row)i.next();
            AttributeValues av = (AttributeValues)map.get(pk);
            log.debug(" - "+pk+": "+av);
        }

        PenroseSearchControls sc = new PenroseSearchControls();
        PenroseSearchResults list = new PenroseSearchResults();
        source.retrieve(map.keySet(), sc, list);
        list.close();
    }

    public void pollChanges(SourceConfig sourceConfig) throws Exception {

        Source source = sourceManager.getSource(partition.getName(), sourceConfig.getName());
        SourceCache sourceCache = source.getSourceCache();
        int lastChangeNumber = sourceCache.getLastChangeNumber();

        ConnectionManager connectionManager = penrose.getConnectionManager();
        Connection connection = connectionManager.getConnection(partition, sourceConfig.getConnectionName());
        PenroseSearchResults sr = connection.getChanges(sourceConfig, lastChangeNumber);
        if (!sr.hasNext()) return;

        ConnectionConfig connectionConfig = connection.getConnectionConfig();
        String user = connectionConfig.getParameter("user");

        //CacheConfig cacheConfig = penroseConfig.getSourceCacheConfig();
        //String user = cacheConfig.getParameter("user");

        Collection entryMappings = partition.getEntryMappings(sourceConfig);

        Collection pks = new HashSet();

        log.debug("Synchronizing changes in "+sourceConfig.getName()+":");
        while (sr.hasNext()) {
            Row pk = (Row)sr.next();

            Number changeNumber = (Number)pk.remove("changeNumber");
            Object changeTime = pk.remove("changeTime");
            String changeAction = (String)pk.remove("changeAction");
            String changeUser = (String)pk.remove("changeUser");

            log.debug(" - "+pk+": "+changeAction+" ("+changeTime+")");

            lastChangeNumber = changeNumber.intValue();

            if (user != null && user.equals(changeUser)) {
                log.debug("Skip changes made by "+user);
                continue;
            }

            pks.add(pk);

            log.debug("Removing source cache "+pk);
            sourceCache.remove(pk);

            for (Iterator i=entryMappings.iterator(); i.hasNext(); ) {
                EntryMapping entryMapping = (EntryMapping)i.next();
                log.debug("Removing entries in "+entryMapping.getDn()+" with "+pk);
                remove(entryMapping, sourceConfig, pk);
            }
        }

        log.debug("Reloading data for "+pks);
        PenroseSearchControls sc = new PenroseSearchControls();
        PenroseSearchResults list = new PenroseSearchResults();
        source.retrieve(pks, sc, list);
        list.close();
        while (list.hasNext()) list.next();

        log.debug("Creating entries with "+pks);
        for (Iterator i=pks.iterator(); i.hasNext(); ) {
            Row pk = (Row)i.next();

            for (Iterator j=entryMappings.iterator(); j.hasNext(); ) {
                EntryMapping entryMapping = (EntryMapping)j.next();
                add(entryMapping, sourceConfig, pk);
            }
        }

        log.debug("Invalidating entry cache");
        EntryCacheManager entryCacheManager = penrose.getEntryCacheManager();
        entryCacheManager.invalidate(partition);

        sourceCache.setLastChangeNumber(lastChangeNumber);
    }

    public void add(
            EntryMapping entryMapping,
            SourceConfig sourceConfig,
            Row pk
    ) throws Exception {

        log.debug("Adding entry cache for "+entryMapping.getDn());

        SourceMapping sourceMapping = engine.getPartitionManager().getPrimarySource(partition, entryMapping);
        log.debug("Primary source: "+sourceMapping.getName()+" ("+sourceMapping.getSourceName()+")");

        Source source = sourceManager.getSource(partition.getName(), sourceConfig.getName());
        SourceCache sourceCache = source.getSourceCache();
        AttributeValues sv = (AttributeValues)sourceCache.get(pk);

        AttributeValues sourceValues = new AttributeValues();
        sourceValues.set(sourceMapping.getName(), sv);

        log.debug("Source values:");
        for (Iterator i=sourceValues.getNames().iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            Collection values = sourceValues.get(name);
            log.debug(" - "+name+": "+values);
        }

        Interpreter interpreter = engine.getInterpreterManager().newInstance();
        Collection dns = engine.computeDns(partition, interpreter, entryMapping, sourceValues);

        PenroseSession session = penrose.newSession();

        PenroseConfig penroseConfig = penrose.getPenroseConfig();
        session.setBindDn(penroseConfig.getRootDn());

        PenroseSearchControls sc = new PenroseSearchControls();

        for (Iterator i=dns.iterator(); i.hasNext(); ) {
            String dn = (String)i.next();

            PenroseSearchResults sr = new PenroseSearchResults();
            session.search(dn, "(objectClass=*)", sc, sr);
            while (sr.hasNext()) sr.next();
        }
    }

    public void remove(
            EntryMapping entryMapping,
            SourceConfig sourceConfig,
            Row pk
    ) throws Exception {

        log.debug("Removing entry cache for "+entryMapping.getDn());

        SourceMapping sourceMapping = engine.getPartitionManager().getPrimarySource(partition, entryMapping);
        log.debug("Primary source: "+sourceMapping.getName()+" ("+sourceMapping.getSourceName()+")");

        Source source = sourceManager.getSource(partition.getName(), sourceConfig.getName());
        SourceCache sourceCache = source.getSourceCache();
        AttributeValues sv = (AttributeValues)sourceCache.get(pk);

        AttributeValues sourceValues = new AttributeValues();
        sourceValues.set(sourceMapping.getName(), sv);

        log.debug("Source values:");
        for (Iterator i=sourceValues.getNames().iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            Collection values = sourceValues.get(name);
            log.debug(" - "+name+": "+values);
        }

        Interpreter interpreter = engine.getInterpreterManager().newInstance();
        Collection dns = engine.computeDns(partition, interpreter, entryMapping, sourceValues);

        EntryCacheManager entryCacheManager = penrose.getEntryCacheManager();

        for (Iterator i=dns.iterator(); i.hasNext(); ) {
            String dn = (String)i.next();
            entryCacheManager.remove(partition, entryMapping, dn);
        }
    }
}
