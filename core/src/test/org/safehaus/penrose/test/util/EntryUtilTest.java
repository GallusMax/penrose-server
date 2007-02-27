package org.safehaus.penrose.test.util;

import junit.framework.TestCase;
import org.safehaus.penrose.util.EntryUtil;
import org.safehaus.penrose.entry.RDN;
import org.safehaus.penrose.entry.RDNBuilder;

import java.util.Collection;
import java.util.List;

/**
 * @author Endi S. Dewata
 */
public class EntryUtilTest extends TestCase {

    public void testGetRdn() {
        String dn = "cn=James Bond,ou=Users,dc=Example,dc=com";
        RDN rdn = EntryUtil.getRdn(dn);

        assertEquals(rdn.getNames().size(), 1);

        String cn = (String)rdn.getNames().iterator().next();
        assertEquals(cn, "cn");

        String value = (String)rdn.get(cn);
        assertEquals(value, "James Bond");
    }

    public void testGetParentDn() {
        String dn = "cn=James Bond,ou=Users,dc=Example,dc=com";
        String parentDn = EntryUtil.getParentDn(dn);

        assertEquals(parentDn, "ou=Users,dc=Example,dc=com");
    }

    public void testRdnWriter() {
        RDNBuilder rb = new RDNBuilder();
        rb.set("cn", "Bond, James");
        rb.set("description", "Secret, Agent");
        RDN rdn = rb.toRdn();

        assertEquals(EntryUtil.toString(rdn), "cn=Bond\\, James+description=Secret\\, Agent");
    }

    public void testParsingSimpleDn() {
        String dn = "cn=James Bond,ou=Users,dc=Example,dc=com";
        List rdns = EntryUtil.parseDn(dn);

        assertEquals(rdns.size(), 4);

        RDN rdn = (RDN)rdns.get(0);
        Collection names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("cn"));
        assertEquals(rdn.get("cn"), "James Bond");

        rdn = (RDN)rdns.get(1);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("ou"));
        assertEquals(rdn.get("ou"), "Users");

        rdn = (RDN)rdns.get(2);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("dc"));
        assertEquals(rdn.get("dc"), "Example");

        rdn = (RDN)rdns.get(3);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("dc"));
        assertEquals(rdn.get("dc"), "com");

    }

    public void testParsingDnWithEscapedCharacters() {
        String dn = "cn=Bond\\, James,ou=Agents\\, Secret,dc=Example,dc=com";
        List rdns = EntryUtil.parseDn(dn);

        assertEquals(rdns.size(), 4);

        RDN rdn = (RDN)rdns.get(0);
        Collection names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("cn"));
        assertEquals(rdn.get("cn"), "Bond, James");

        rdn = (RDN)rdns.get(1);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("ou"));
        assertEquals(rdn.get("ou"), "Agents, Secret");

        rdn = (RDN)rdns.get(2);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("dc"));
        assertEquals(rdn.get("dc"), "Example");

        rdn = (RDN)rdns.get(3);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("dc"));
        assertEquals(rdn.get("dc"), "com");

    }

    public void testParsingCompositeRdn() {
        String dn = "cn=James Bond+uid=jbond+displayName=007,ou=Users+description=Secret Agents,dc=Example,dc=com";
        List rdns = EntryUtil.parseDn(dn);

        assertEquals(rdns.size(), 4);

        RDN rdn = (RDN)rdns.get(0);
        Collection names = rdn.getNames();
        assertEquals(names.size(), 3);
        assertTrue(names.contains("cn"));
        assertEquals(rdn.get("cn"), "James Bond");
        assertTrue(names.contains("uid"));
        assertEquals(rdn.get("uid"), "jbond");
        assertTrue(names.contains("displayName"));
        assertEquals(rdn.get("displayName"), "007");

        rdn = (RDN)rdns.get(1);
        names = rdn.getNames();
        assertEquals(names.size(), 2);
        assertTrue(names.contains("ou"));
        assertEquals(rdn.get("ou"), "Users");
        assertTrue(names.contains("description"));
        assertEquals(rdn.get("description"), "Secret Agents");

        rdn = (RDN)rdns.get(2);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("dc"));
        assertEquals(rdn.get("dc"), "Example");

        rdn = (RDN)rdns.get(3);
        names = rdn.getNames();
        assertEquals(names.size(), 1);
        assertTrue(names.contains("dc"));
        assertEquals(rdn.get("dc"), "com");
    }
}
