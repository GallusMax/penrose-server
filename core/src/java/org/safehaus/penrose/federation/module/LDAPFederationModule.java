package org.safehaus.penrose.federation.module;

import org.safehaus.penrose.federation.FederationRepositoryConfig;

import java.util.Collection;

/**
 * @author Endi Sukma Dewata
 */
public class LDAPFederationModule extends FederationModule {

    public Collection<String> getRepositoryNames() throws Exception {
        return getRepositoryNames("LDAP");
    }

    public Collection<FederationRepositoryConfig> getRepositories() throws Exception {
        return getRepositories("LDAP");
    }
}