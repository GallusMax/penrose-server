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
package org.safehaus.penrose.cache;

import org.safehaus.penrose.mapping.*;
import java.util.*;

/**
 * @author Endi S. Dewata
 */
public class EntrySourceCache extends EntryDataCache {

    Map dataMap = new TreeMap();
    Map expirationMap = new LinkedHashMap();

    public void init() throws Exception {
        super.init();

        String s = entryDefinition.getParameter(EntryDefinition.SOURCE_CACHE_SIZE);
        if (s != null) size = Integer.parseInt(s);

        s = entryDefinition.getParameter(EntryDefinition.SOURCE_CACHE_EXPIRATION);
        if (s != null) expiration = Integer.parseInt(s);
    }

    public Object get(Object key) throws Exception {

        //log.debug("Getting entry cache ("+dataMap.size()+"): "+rdn);

        Object object = dataMap.get(key);
        Date date = (Date)expirationMap.get(key);

        if (date == null || date.getTime() <= System.currentTimeMillis()) {
            dataMap.remove(key);
            expirationMap.remove(key);
            return null;
        }

        return object;
    }

    public Map getExpired() throws Exception {
        Map results = new TreeMap();
        return results;
    }

    public void put(Object key, Object object) throws Exception {
        if (size == 0) return;

        while (dataMap.get(key) == null && dataMap.size() >= size) {
            //log.debug("Trimming entry cache ("+dataMap.size()+").");
            Object k = expirationMap.keySet().iterator().next();
            dataMap.remove(k);
            expirationMap.remove(k);
        }

        //log.debug("Storing entry cache ("+dataMap.size()+"): "+key);
        dataMap.put(key, object);
        expirationMap.put(key, new Date(System.currentTimeMillis() + expiration * 60 * 1000));
    }

    public void remove(Object key) throws Exception {

        //log.debug("Removing entry cache ("+dataMap.size()+"): "+key);
        dataMap.remove(key);
        expirationMap.remove(key);
    }
}
