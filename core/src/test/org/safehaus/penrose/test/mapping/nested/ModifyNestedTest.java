package org.safehaus.penrose.test.mapping.nested;

import org.safehaus.penrose.session.Session;

import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.DirContext;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;

/**
 * @author Endi S. Dewata
 */
public class ModifyNestedTest extends NestedTestCase {

    public ModifyNestedTest() throws Exception {
    }

    public void testModifyingEntry() throws Exception {

        executeUpdate("insert into groups values ('group1', 'desc1')");
        executeUpdate("insert into groups values ('group2', 'desc2')");
        
        executeUpdate("insert into members values ('member', 'group1', 'Member1')");
        executeUpdate("insert into members values ('member', 'group2', 'Member2')");

        Session session = penrose.newSession();
        session.bind(penroseConfig.getRootDn(), penroseConfig.getRootPassword());

        Collection modifications = new ArrayList();

        Attribute attribute = new BasicAttribute("cn");
        attribute.add("New Member2");
        ModificationItem mi = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attribute);
        modifications.add(mi);

        session.modify("uid=member,cn=group2,"+baseDn, modifications);

        session.close();

        Collection results = executeQuery("select * from groups");
        assertEquals(results.size(), 2);

        results = executeQuery("select * from members");
        assertEquals(results.size(), 2);

        Iterator i = results.iterator();

        Map row = (Map)i.next();
        assertEquals(row.get("USERNAME"), "member");
        assertEquals(row.get("GROUPNAME"), "group1");
        assertEquals(row.get("NAME"), "Member1");

        row = (Map)i.next();
        assertEquals(row.get("USERNAME"), "member");
        assertEquals(row.get("GROUPNAME"), "group2");
        assertEquals(row.get("NAME"), "New Member2");
    }
}
