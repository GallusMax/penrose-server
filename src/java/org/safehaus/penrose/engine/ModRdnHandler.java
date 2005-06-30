/**
 * Copyright (c) 1998-2005, Verge Lab., LLC.
 * All rights reserved.
 */
package org.safehaus.penrose.engine;

import org.safehaus.penrose.PenroseConnection;

/**
 * @author Endi S. Dewata
 */
public abstract class ModRdnHandler {

    public abstract void init(Engine engine) throws Exception;
    public abstract int modrdn(PenroseConnection connection, String dn, String newRdn) throws Exception;

}
