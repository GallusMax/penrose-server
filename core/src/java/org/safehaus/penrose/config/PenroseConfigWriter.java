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
package org.safehaus.penrose.config;

import java.util.Iterator;
import java.util.Collection;
import java.io.FileWriter;
import java.io.Writer;
import java.io.File;

import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.DefaultElement;
import org.dom4j.tree.DefaultText;
import org.safehaus.penrose.adapter.AdapterConfig;
import org.safehaus.penrose.cache.CacheConfig;
import org.safehaus.penrose.interpreter.InterpreterConfig;
import org.safehaus.penrose.engine.EngineConfig;
import org.safehaus.penrose.connector.ConnectorConfig;
import org.safehaus.penrose.schema.SchemaConfig;
import org.safehaus.penrose.partition.PartitionConfig;
import org.safehaus.penrose.user.UserConfig;
import org.safehaus.penrose.session.SessionConfig;
import org.safehaus.penrose.service.ServiceConfig;
import org.safehaus.penrose.Penrose;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author Endi S. Dewata
 */
public class PenroseConfigWriter {

    Logger log = LoggerFactory.getLogger(getClass());

    File file;

    public PenroseConfigWriter(String filename) throws Exception {
        file = new File(filename);
    }

    /**
     * Store configuration into xml file.
     *
     * @throws Exception
     */
    public void write(PenroseConfig penroseConfig) throws Exception {

        file.getParentFile().mkdirs();
        Writer writer = new FileWriter(file);

        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setTrimText(false);

        XMLWriter xmlWriter = new XMLWriter(writer, format);
        xmlWriter.startDocument();

        xmlWriter.startDTD(
                "server",
                "-//Penrose/DTD Server "+Penrose.SPECIFICATION_VERSION+"//EN",
                "http://penrose.safehaus.org/dtd/server.dtd"
        );

        xmlWriter.write(toElement(penroseConfig));
        xmlWriter.close();

        writer.close();
    }

    public Element toElement(PenroseConfig penroseConfig) {
        Element element = new DefaultElement("server");

        for (Iterator i = penroseConfig.getSystemPropertyNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = penroseConfig.getSystemProperty(name);

            Element parameter = new DefaultElement("system-property");

            Element paramName = new DefaultElement("property-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("property-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }

        for (Iterator i = penroseConfig.getServiceConfigs().iterator(); i.hasNext();) {
            ServiceConfig serviceConfig = (ServiceConfig)i.next();
            element.add(toElement(serviceConfig));
        }

        for (Iterator i=penroseConfig.getSchemaConfigs().iterator(); i.hasNext(); ) {
            SchemaConfig schemaConfig = (SchemaConfig)i.next();

            Element schema = new DefaultElement("schema");
            if (schemaConfig.getName() != null) schema.addAttribute("name", schemaConfig.getName());
            if (schemaConfig.getPath() != null) schema.addAttribute("path", schemaConfig.getPath());

            element.add(schema);
        }

        Collection interpreterConfigs = penroseConfig.getInterpreterConfigs();
        for (Iterator i=interpreterConfigs.iterator(); i.hasNext(); ) {
            InterpreterConfig interpreterConfig = (InterpreterConfig)i.next();
            element.add(toElement(interpreterConfig));
        }

        if (penroseConfig.getEntryCacheConfig() != null) {
            Element entryCache = new DefaultElement("entry-cache");
            addElements(entryCache, penroseConfig.getEntryCacheConfig());
            element.add(entryCache);
        }

        if (penroseConfig.getSessionConfig() != null) {
            SessionConfig sessionConfig = penroseConfig.getSessionConfig();
            element.add(toElement(sessionConfig));
        }

        Collection engineConfigs = penroseConfig.getEngineConfigs();
        for (Iterator i=engineConfigs.iterator(); i.hasNext(); ) {
            EngineConfig engineConfig = (EngineConfig)i.next();
            element.add(toElement(engineConfig));
        }

        if (penroseConfig.getConnectorConfig() != null) {
            ConnectorConfig connectorConfig = penroseConfig.getConnectorConfig();
            element.add(toElement(connectorConfig));
        }

        for (Iterator i = penroseConfig.getAdapterConfigs().iterator(); i.hasNext();) {
            AdapterConfig adapterConfig = (AdapterConfig)i.next();
            element.add(toElement(adapterConfig));
        }

        for (Iterator i=penroseConfig.getPartitionConfigs().iterator(); i.hasNext(); ) {
            PartitionConfig partitionConfig = (PartitionConfig)i.next();
            element.add(toElement(partitionConfig));
        }

        UserConfig rootUserConfig = penroseConfig.getRootUserConfig();
        if (rootUserConfig != null ) {
            Element rootElement = new DefaultElement("root");

            if (rootUserConfig.getDn() != null) {
                Element rootDn = new DefaultElement("root-dn");
                rootDn.add(new DefaultText(rootUserConfig.getDn().toString()));
                rootElement.add(rootDn);
            }

            if (rootUserConfig.getPassword() != null) {
                Element rootPassword = new DefaultElement("root-password");
                rootPassword.add(new DefaultText(rootUserConfig.getPassword()));
                rootElement.add(rootPassword);
            }

            element.add(rootElement);
        }

        return element;
    }

    public Element toElement(ServiceConfig serviceConfig) {

        Element element = new DefaultElement("service");
        element.addAttribute("name", serviceConfig.getName());
        if (!serviceConfig.isEnabled()) element.addAttribute("enabled", "false");

        Element adapterClass = new DefaultElement("service-class");
        adapterClass.add(new DefaultText(serviceConfig.getServiceClass()));
        element.add(adapterClass);

        if (serviceConfig.getDescription() != null && !"".equals(serviceConfig.getDescription())) {
            Element description = new DefaultElement("description");
            description.add(new DefaultText(serviceConfig.getDescription()));
            element.add(description);
        }

        for (Iterator i = serviceConfig.getParameterNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = (String)serviceConfig.getParameter(name);

            Element parameter = new DefaultElement("parameter");

            Element paramName = new DefaultElement("param-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("param-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }

        return element;
    }

    public Element toElement(AdapterConfig adapterConfig) {
        Element element = new DefaultElement("adapter");
        element.addAttribute("name", adapterConfig.getName());

        Element adapterClass = new DefaultElement("adapter-class");
        adapterClass.add(new DefaultText(adapterConfig.getAdapterClass()));
        element.add(adapterClass);

        if (adapterConfig.getDescription() != null && !"".equals(adapterConfig.getDescription())) {
            Element description = new DefaultElement("description");
            description.add(new DefaultText(adapterConfig.getDescription()));
            element.add(description);
        }

        for (Iterator i = adapterConfig.getParameterNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = (String)adapterConfig.getParameter(name);

            Element parameter = new DefaultElement("parameter");

            Element paramName = new DefaultElement("param-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("param-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }

        return element;
    }

    public Element toElement(InterpreterConfig interpreterConfig) {
        Element element = new DefaultElement("interpreter");
/*
        Element interpreterName = new DefaultElement("interpreter-name");
        interpreterName.add(new DefaultText(interpreterConfig.getName()));
        element.add(interpreterName);
*/
        Element interpreterClass = new DefaultElement("interpreter-class");
        interpreterClass.add(new DefaultText(interpreterConfig.getInterpreterClass()));
        element.add(interpreterClass);

        if (interpreterConfig.getDescription() != null && !"".equals(interpreterConfig.getDescription())) {
            Element description = new DefaultElement("description");
            description.add(new DefaultText(interpreterConfig.getDescription()));
            element.add(description);
        }

        for (Iterator i = interpreterConfig.getParameterNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = (String)interpreterConfig.getParameter(name);

            Element parameter = new DefaultElement("parameter");

            Element paramName = new DefaultElement("param-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("param-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }

        return element;
    }

    public Element toElement(SessionConfig sessionConfig) {
        Element element = new DefaultElement("session");

        if (sessionConfig.getDescription() != null && !"".equals(sessionConfig.getDescription())) {
            Element description = new DefaultElement("description");
            description.add(new DefaultText(sessionConfig.getDescription()));
            element.add(description);
        }

        for (Iterator i = sessionConfig.getParameterNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = (String)sessionConfig.getParameter(name);

            Element parameter = new DefaultElement("parameter");

            Element paramName = new DefaultElement("param-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("param-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }

        return element;
    }

    public Element toElement(EngineConfig engineConfig) {
        Element element = new DefaultElement("engine");

        Element engineName = new DefaultElement("engine-name");
        engineName.add(new DefaultText(engineConfig.getName()));
        element.add(engineName);

        Element engineClass = new DefaultElement("engine-class");
        engineClass.add(new DefaultText(engineConfig.getEngineClass()));
        element.add(engineClass);

        if (engineConfig.getDescription() != null && !"".equals(engineConfig.getDescription())) {
            Element description = new DefaultElement("description");
            description.add(new DefaultText(engineConfig.getDescription()));
            element.add(description);
        }

        for (Iterator i = engineConfig.getParameterNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = (String)engineConfig.getParameter(name);

            Element parameter = new DefaultElement("parameter");

            Element paramName = new DefaultElement("param-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("param-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }

        return element;
    }

    public Element toElement(PartitionConfig partitionConfig)  {
        Element element = new DefaultElement("partition");

        if (partitionConfig.getName() != null) element.addAttribute("name", partitionConfig.getName());
        if (partitionConfig.getPath() != null) element.addAttribute("path", partitionConfig.getPath());

        String s = partitionConfig.getHandlerName();
        if (s != null && !"".equals(s)) {
            Element handlerName = new DefaultElement("handler-name");
            handlerName.add(new DefaultText(s));
            element.add(handlerName);
        }

        s = partitionConfig.getEngineName();
        if (s != null && !"".equals(s)) {
            Element engineName = new DefaultElement("engine-name");
            engineName.add(new DefaultText(s));
            element.add(engineName);
        }

        return element;
    }
    
    public void addElements(Element element, CacheConfig cacheConfig) {
/*
        Element cacheName = new DefaultElement("cache-name");
        cacheName.add(new DefaultText(cacheConfig.getName()));
        element.add(cacheName);
*/
        if (cacheConfig.getCacheClass() != null && !"".equals(cacheConfig.getCacheClass())) {
            Element cacheClass = new DefaultElement("cache-class");
            cacheClass.add(new DefaultText(cacheConfig.getCacheClass()));
            element.add(cacheClass);
        }

        if (cacheConfig.getDescription() != null && !"".equals(cacheConfig.getDescription())) {
            Element description = new DefaultElement("description");
            description.add(new DefaultText(cacheConfig.getDescription()));
            element.add(description);
        }

        for (Iterator i = cacheConfig.getParameterNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = (String)cacheConfig.getParameter(name);

            Element parameter = new DefaultElement("parameter");

            Element paramName = new DefaultElement("param-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("param-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }
    }

    public Element toElement(ConnectorConfig connectorConfig) {
        Element element = new DefaultElement("connector");
/*
        Element cacheName = new DefaultElement("connector-name");
        cacheName.add(new DefaultText(connectorConfig.getName()));
        element.add(cacheName);

        if (connectorConfig.getConnectorClass() != null && !"".equals(connectorConfig.getConnectorClass())) {
            Element cacheClass = new DefaultElement("connector-class");
            cacheClass.add(new DefaultText(connectorConfig.getConnectorClass()));
            element.add(cacheClass);
        }
*/
        if (connectorConfig.getDescription() != null && !"".equals(connectorConfig.getDescription())) {
            Element description = new DefaultElement("description");
            description.add(new DefaultText(connectorConfig.getDescription()));
            element.add(description);
        }

        for (Iterator i = connectorConfig.getParameterNames().iterator(); i.hasNext();) {
            String name = (String)i.next();
            String value = (String)connectorConfig.getParameter(name);

            Element parameter = new DefaultElement("parameter");

            Element paramName = new DefaultElement("param-name");
            paramName.add(new DefaultText(name));
            parameter.add(paramName);

            Element paramValue = new DefaultElement("param-value");
            paramValue.add(new DefaultText(value));
            parameter.add(paramValue);

            element.add(parameter);
        }

        return element;
    }

}
