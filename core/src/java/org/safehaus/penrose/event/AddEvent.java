/**
 * Copyright 2009 Red Hat, Inc.
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
package org.safehaus.penrose.event;

import org.safehaus.penrose.session.Session;
import org.safehaus.penrose.ldap.AddRequest;
import org.safehaus.penrose.ldap.AddResponse;
import org.safehaus.penrose.partition.Partition;

/**
 * @author Endi S. Dewata
 */
public class AddEvent extends Event {

    public final static int BEFORE_ADD = 0;
    public final static int AFTER_ADD  = 1;

    protected Session session;
    protected Partition partition;

    protected AddRequest request;
    protected AddResponse response;

    public AddEvent(Object source, int type, Session session, Partition partition, AddRequest request, AddResponse response) {
        super(source, type);
        this.session = session;
        this.partition = partition;
        this.request = request;
        this.response = response;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Partition getPartition() {
        return partition;
    }

    public void setPartition(Partition partition) {
        this.partition = partition;
    }

    public AddRequest getRequest() {
        return request;
    }

    public void setRequest(AddRequest request) {
        this.request = request;
    }

    public AddResponse getResponse() {
        return response;
    }

    public void setResponse(AddResponse response) {
        this.response = response;
    }

    public String toString() {
        return (type == BEFORE_ADD ? "Before" : "After")+"Add";
    }
}
