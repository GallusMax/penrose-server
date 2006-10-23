package org.safehaus.penrose.client;

import javax.management.ObjectName;
import javax.management.MBeanServerConnection;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Endi S. Dewata
 */
public class ServiceManagerClient {

    public final static String NAME = "Penrose:name=ServiceManager";

    PenroseClient client;
    ObjectName objectName;

    public ServiceManagerClient(PenroseClient client) throws Exception {
        this.client = client;
        objectName = new ObjectName(ServiceManagerClient.NAME);
    }

    public PenroseClient getClient() {
        return client;
    }

    public void setClient(PenroseClient client) {
        this.client = client;
    }

    public Collection getServiceNames() throws Exception {
        MBeanServerConnection connection = client.getConnection();
        return (Collection)connection.getAttribute(objectName, "ServiceNames");
    }

    public String getStatus(String name) throws Exception {
        MBeanServerConnection connection = client.getConnection();
        return (String)connection.invoke(
                objectName,
                "getStatus",
                new Object[] { name },
                new String[] { String.class.getName() }
        );
    }

    public ServiceClient getService(String name) throws Exception {
        return new ServiceClient(client, name);
    }

    public void start() throws Exception {
        MBeanServerConnection connection = client.getConnection();
        connection.invoke(
                objectName,
                "start",
                new Object[] { },
                new String[] { }
        );
    }

    public void start(String name) throws Exception {
        MBeanServerConnection connection = client.getConnection();
        connection.invoke(
                objectName,
                "start",
                new Object[] { name },
                new String[] { String.class.getName() }
        );
    }

    public void stop() throws Exception {
        MBeanServerConnection connection = client.getConnection();
        connection.invoke(
                objectName,
                "stop",
                new Object[] { },
                new String[] { }
        );
    }

    public void stop(String name) throws Exception {
        MBeanServerConnection connection = client.getConnection();
        connection.invoke(
                objectName,
                "stop",
                new Object[] { name },
                new String[] { String.class.getName() }
        );
    }

    public void restart() throws Exception {
        MBeanServerConnection connection = client.getConnection();
        connection.invoke(
                objectName,
                "restart",
                new Object[] { },
                new String[] { }
        );
    }

    public void printServices() throws Exception {
        for (Iterator i=getServiceNames().iterator(); i.hasNext(); ) {
            String name = (String)i.next();
            String status = getStatus(name);

            StringBuffer padding = new StringBuffer();
            for (int j=0; j<20-name.length(); j++) padding.append(" ");

            System.out.println(name +padding+"["+status+"]");
        }
    }
}