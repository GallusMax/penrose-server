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
package org.safehaus.penrose.ldap;

import org.safehaus.penrose.module.Module;
import org.safehaus.penrose.entry.Entry;
import org.safehaus.penrose.entry.DN;
import org.safehaus.penrose.mapping.EntryMapping;
import org.safehaus.penrose.cache.EntryCache;
import org.safehaus.penrose.cache.EntryCacheListener;
import org.safehaus.penrose.cache.EntryCacheEvent;
import org.safehaus.penrose.connector.ConnectionManager;
import org.safehaus.penrose.partition.PartitionManager;
import org.safehaus.penrose.partition.Partition;
import org.safehaus.penrose.handler.Handler;
import org.safehaus.penrose.util.EntryUtil;

import javax.naming.directory.*;
import java.util.Collection;

/**
 * @author Endi S. Dewata
 */
public class LDAPSyncModule extends Module implements EntryCacheListener {

    public final static String CONNECTION = "connection";

    public PartitionManager partitionManager;
    public ConnectionManager connectionManager;

    public String connectionName;

    public void init() throws Exception {

        connectionName = getParameter(CONNECTION);

        partitionManager = penrose.getPartitionManager();
        connectionManager = penrose.getConnectionManager();

        Handler handler = penrose.getHandler();
        EntryCache entryCache = handler.getEntryCache();

        entryCache.addListener(this);
    }

    public DirContext getConnection() throws Exception {
        LDAPClient client = (LDAPClient)connectionManager.openConnection(connectionName);
        return client.getContext();
    }

    public void cacheAdded(EntryCacheEvent event) throws Exception {

        Entry entry = (Entry)event.getSource();
        EntryMapping entryMapping = entry.getEntryMapping();

        if (!partition.contains(entryMapping)) return;

        DirContext ctx = null;

        try {
            ctx = getConnection();

            DN baseDn = entry.getDn();
            log.debug("Adding "+baseDn);

            SearchResult searchResult = EntryUtil.toSearchResult(entry);
            Attributes attributes = searchResult.getAttributes();
            ctx.createSubcontext(searchResult.getName(), attributes);

        } catch (Exception e) {
            log.error(e.getMessage());

        } finally {
            if (ctx != null) try { ctx.close(); } catch (Exception e) {}
        }
    }

    public void cacheRemoved(EntryCacheEvent event) throws Exception {

        DN baseDn = (DN)event.getSource();
        Partition partition = partitionManager.getPartition(baseDn);
        Collection entryMappings = partition.findEntryMappings(baseDn);

        if (entryMappings == null || entryMappings.isEmpty()) return;

        DirContext ctx = null;

        try {
            ctx = getConnection();

            log.debug("Removing "+baseDn);
            ctx.destroySubcontext(baseDn.toString());

        } catch (Exception e) {
            log.error(e.getMessage());

        } finally {
            if (ctx != null) try { ctx.close(); } catch (Exception e) {}
        }
    }
}
