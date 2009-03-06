package org.safehaus.penrose.directory;

import org.safehaus.penrose.ldap.DN;
import org.safehaus.penrose.partition.Partition;
import org.safehaus.penrose.partition.PartitionConfig;
import org.safehaus.penrose.partition.PartitionContext;
import org.safehaus.penrose.partition.PartitionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Endi Sukma Dewata
 */
public class Directory implements Cloneable {

    public Logger log = LoggerFactory.getLogger(getClass());
    boolean debug = log.isDebugEnabled();

    public final static Collection<Entry> EMPTY_ENTRIES = new ArrayList<Entry>();

    protected Partition partition;

    protected DirectoryConfig directoryConfig;

    protected Collection<Entry> rootEntries = new ArrayList<Entry>();
    protected Map<String,Entry> entries = new LinkedHashMap<String,Entry>();

    public Directory(Partition partition) throws Exception {
        this.partition = partition;

        PartitionConfig partitionConfig = partition.getPartitionConfig();
        directoryConfig = partitionConfig.getDirectoryConfig();
    }

    public void init() throws Exception {

        if (debug) log.debug("Root entries: "+directoryConfig.getRootNames());

        for (EntryConfig entryConfig : directoryConfig.getEntryConfigs()) {
            if (!entryConfig.isEnabled()) continue;

            try {
                createEntry(entryConfig);
            } catch (Exception e) {
                log.error("Failed creating entry "+entryConfig.getName()+" ("+entryConfig.getDn()+") in partition "+partition.getName()+".", e);
            }
        }
    }

    public void destroy() throws Exception {

        for (String entryName : getRootNames()) {
            try {
                destroy(entryName);
            } catch (Exception e) {
                log.error("Failed removing entry "+entryName+" in partition "+partition.getName()+".", e);
            }
        }
    }

    public void destroy(String entryName) throws Exception {

        Entry entry = getEntry(entryName);
        if (entry == null) throw new Exception("Entry "+entryName+" not found.");

        Collection<Entry> children = new ArrayList<Entry>();
        children.addAll(entry.getChildren());

        for (Entry child : children) {
            if (child.getPartition() != partition) continue;
            destroy(child.getName());
        }

        removeEntry(entryName);

        entry.destroy();
    }

    public Entry createEntry(EntryConfig entryConfig) throws Exception {

        if (debug) log.debug("Creating entry \""+ entryConfig.getDn()+"\".");

        EntryContext entryContext = new EntryContext();
        entryContext.setDirectory(this);

        Entry parent = findParent(entryConfig);
        entryContext.setParent(parent);

        PartitionContext partitionContext = partition.getPartitionContext();
        ClassLoader cl = partitionContext.getClassLoader();

        String className = entryConfig.getEntryClass();
        if (className == null) className = Entry.class.getName();

        Class clazz = cl.loadClass(className);

        if (debug) log.debug("Creating "+className+".");
        Entry entry = (Entry)clazz.newInstance();
        entry.init(entryConfig, entryContext);

        addEntry(entry);

        return entry;
    }

    public void addEntry(Entry entry) throws Exception {
        entries.put(entry.getName(), entry);

        Entry parent = entry.getParent();
        if (parent == null) {
            rootEntries.add(entry);
        }
    }

    public Entry removeEntry(String entryName) throws Exception {

        if (debug) log.debug("Removing entry \""+entryName+"\".");

        Entry entry = entries.remove(entryName);
        if (entry == null) {
            if (debug) log.debug("Entry \""+entryName+"\" not found.");
            return null;
        }

        DN dn = entry.getDn();
        if (debug) log.debug("Removing entry \""+dn+"\".");

        Entry parent = entry.getParent();
        if (parent == null) {
            if (debug) log.debug("Removing root entry \""+dn+"\".");
            rootEntries.remove(entry);

        } else {
            if (debug) log.debug("Detaching from \""+parent.getDn()+"\".");
            parent.removeChild(entry);
        }

        return entry;
    }

    public Entry findParent(EntryConfig entryConfig) throws Exception {

        if (debug) log.debug("Searching parent of \""+entryConfig.getDn()+"\".");

        String parentName = entryConfig.getParentName();

        if (parentName != null) {
            if (debug) log.debug("Searching parent: "+parentName);
            Entry parent = getEntry(parentName);

            if (parent != null) {
                if (debug) log.debug("Found parent \""+parent.getDn()+"\".");
                return parent;
            }
        }

        DN parentDn = entryConfig.getParentDn();

        if (entryConfig.isAttached() && !parentDn.isEmpty()) {

            if (debug) log.debug("Searching parent DN: "+parentDn);
            Entry parent = getEntry(parentDn);

            if (parent != null) {
                if (debug) log.debug("Found parent \""+parent.getDn()+"\".");
                return parent;
            }

            if (debug) log.debug("Searching parent DN in other partitions.");
            PartitionContext partitionContext = partition.getPartitionContext();
            PartitionManager partitionManager = partitionContext.getPartitionManager();
            Collection<String> depends = partition.getDepends();

            for (String depend : depends) {
                Partition p = partitionManager.getPartition(depend);

                Collection<Entry> parents = p.findEntries(parentDn);
                if (parents.isEmpty()) continue;

                parent = parents.iterator().next();
                if (debug) log.debug("Found parent \""+parent.getDn()+"\" in partition "+p.getName()+".");
                return parent;
            }

            Partition p = partitionManager.getPartition(PartitionConfig.ROOT);
            if (p != partition) {
                Collection<Entry> parents = p.findEntries(parentDn);
                if (!parents.isEmpty()) {
                    parent = parents.iterator().next();
                    if (debug) log.debug("Found parent \""+parent.getDn()+"\" in partition "+p.getName()+".");
                    return parent;
                }
            }
        }

        if (debug) log.debug("Parent not found.");
        return null;
    }

    public Entry getEntry(String entryName) {
        return entries.get(entryName);
    }

    public Entry getEntry(DN dn) throws Exception {
        Collection<Entry> entries = getEntries(dn);
        if (entries.isEmpty()) return null;
        return entries.iterator().next();
    }

    public Collection<Entry> getEntries() {
        Collection<Entry> results = new ArrayList<Entry>();
        results.addAll(entries.values());
        return results;
    }
    
    public Collection<Entry> getEntries(Collection<String> names) {

        if (names == null) return EMPTY_ENTRIES;

        Collection<Entry> results = new ArrayList<Entry>();
        for (String name : names) {
            Entry entry = entries.get(name);
            results.add(entry);
        }
        return results;
    }

    public Collection<Entry> getEntries(DN dn) throws Exception {

        if (dn == null) return EMPTY_ENTRIES;

        Collection<String> ids = directoryConfig.getEntryNamesByDn(dn);
        return getEntries(ids);
    }

    public Collection<Entry> findEntries(DN dn) throws Exception {

        if (debug) log.debug("Searching for \""+dn+"\" in \""+partition.getName()+"\":");

        Collection<Entry> results = new ArrayList<Entry>();

        for (Entry entry : rootEntries) {
            if (debug) log.debug(" - Suffix: "+entry.getDn());

            Collection<Entry> list = entry.findEntries(dn);
            for (Entry e : list) {
                if (debug) log.debug("   - Found "+e.getName()+": "+e.getDn());
                results.add(e);
            }
        }

        return results;
    }
/*
    public Collection<Entry> findEntries(DN dn) throws Exception {
        if (dn == null) return EMPTY_ENTRIES;
        //log.debug("Finding entries \""+dn+"\" in partition "+getName());

        // search for static mappings
        Collection<Entry> results = entriesByDn.get(dn.getNormalizedDn());
        if (results != null) {
            //log.debug("Found "+results.size()+" mapping(s).");
            return results;
        }

        // can't find exact match -> search for parent mappings

        DN parentDn = dn.getParentDn();

        results = new ArrayList<Entry>();
        Collection<Entry> list;

        // if dn has no parent, check against root entries
        if (parentDn.isEmpty()) {
            //log.debug("Check root mappings");
            list = rootEntries;

        } else {
            if (debug) log.debug("Search parents for \""+parentDn+"\".");
            Collection<Entry> parents = findEntries(parentDn);

            // if no parent mappings found, the entry doesn't exist in this partition
            if (parents == null || parents.isEmpty()) {
            	if (debug) log.debug("Entry \""+parentDn+"\" not found.");
                return EMPTY_ENTRIES;
            }

            list = new ArrayList<Entry>();

            // for each parent mapping found
            for (Entry parent : parents) {
                if (debug) log.debug("Found parent \"" + parent.getDn()+"\".");

                String handlerName = parent.getHandlerName();
                if ("PROXY".equals(handlerName)) { // if parent is proxy, include it in results
                    results.add(parent);

                } else { // otherwise check for matching siblings
                    Collection<Entry> children = parent.getChildren();
                    list.addAll(children);
                }
            }
        }

        // check against each mapping in the list
        for (Entry entry : list) {

            if (debug) {
                log.debug("Checking DN pattern:");
                log.debug(" - " + dn);
                log.debug(" - " + entry.getDn());
            }
            if (!dn.matches(entry.getDn())) continue;

            if (debug) log.debug("Found " + entry.getDn());
            results.add(entry);
        }

        return results;
    }
*/
    public DirectoryConfig getDirectoryConfig() {
        return directoryConfig;
    }

    public void setDirectoryConfig(DirectoryConfig directoryConfig) {
        this.directoryConfig = directoryConfig;
    }

    public Partition getPartition() {
        return partition;
    }

    public String getRootName() {
        return directoryConfig.getRootName();
    }

    public Collection<String> getRootNames() {
        Collection<String> entryNames = new ArrayList<String>();
        for (Entry entry : rootEntries) {
            entryNames.add(entry.getName());
        }
        return entryNames;
    }

    public Entry getRootEntry() {
        return getEntry(getRootName());
    }

    public Collection<Entry> getRootEntries() {
        return rootEntries;
    }

    public DN getSuffix() {
        return directoryConfig.getSuffix();
    }

    public Collection<DN> getSuffixes() {
        return directoryConfig.getSuffixes();
    }

    public Collection<Entry> getEntriesBySourceName(String sourceName) throws Exception {
        Collection<String> ids = directoryConfig.getEntryNamesBySource(sourceName);
        return getEntries(ids);
    }
}
