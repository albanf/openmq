/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2000-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/*
 * @(#)ServiceFactory.java	1.12 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service;

import java.util.*;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.log.Logger;

/**
 * A service handler is an abstract class which
 * handles creating services and managing their
 * resources (e.g. updating them when properties
 * change)
 */

public abstract class ServiceFactory implements ConfigListener
{

    public static final String SERVICE_PREFIX = Globals.IMQ + ".";
    public static final String SERVICE_PROTOCOLTYPE_SUFFIX = ".protocoltype";
    public static final String SERVICE_HANDLER_SUFFIX = ".handler_name";
    public static final String SERVICE_THREADPOOL_MODEL_SUFFIX = ".threadpool_model";
    public static final String WEBSOCKET_HANDLER_NAME = "websocket";

    protected static boolean DEBUG = false;

    protected final Logger logger = Globals.getLogger();
    protected final BrokerResources br = Globals.getBrokerResources();

    private String factoryHandlerName = null;

    ConnectionManager conmgr = null;

    /**
     * subclass should add code entry here if override 
     * instance method enforceServiceHandler()
     */
    public static void enforceServiceHandler(
        String service, BrokerConfig config, ServiceManager sm)
        throws BrokerException {
        if (sm == null) {
            return;
        }
        if (service.equals(MQAddress.DEFAULT_WS_SERVICE) ||
            service.equals(MQAddress.DEFAULT_WSS_SERVICE)) {
            String prototype = config.getProperty(
                       SERVICE_PREFIX+service+SERVICE_PROTOCOLTYPE_SUFFIX);
            if (prototype != null && 
                !prototype.equalsIgnoreCase("ws") &&
                !prototype.equalsIgnoreCase("wss")) {
                throw new BrokerException(
                Globals.getBrokerResources().getKString(
                BrokerResources.X_PROTOCOLTYPE_NO_SUPPORT,
                prototype, service));
            }
        }

        ServiceFactory sf = null;
        try {
            sf = sm.createServiceFactory(WEBSOCKET_HANDLER_NAME, true);
        } catch (ClassNotFoundException e) {
        } catch (Exception e) {
            throw new BrokerException(e.getMessage(), e);
        }
        if (sf != null) {
            sf.enforceServiceHandler(service, config);
        }
    }

    /**
     */
    public void enforceServiceHandler(String service, BrokerConfig config)
    throws BrokerException {
    }

    /**
     */
    public static boolean isDefaultStandardServiceName(String name) {
        if (name.equals("jms") || name.equals("ssljms") ||
            name.equals("admin") || name.equals("ssladmin") ||
            name.equals("httpjms") || name.equals("httpsjms")) {
            return true;
        }
        return false;
    }

    protected final void setFactoryHandlerName(String handlerName) {
        factoryHandlerName = handlerName;
    }

    public final String getFactoryHandlerName() {
        return factoryHandlerName;
    }

    /**
     * @param handlerName for the ServiceFactory
     * @throws IllegalAccessException if the handlerName not supported
     */
    protected abstract void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException;

    public void setConnectionManager(ConnectionManager conmgr)
    {
        this.conmgr = conmgr;
    }


    public abstract Service createService(String instancename, int servicetype)
        throws BrokerException;

    public abstract void updateService(Service s)
        throws BrokerException;

    public abstract void startMonitoringService(Service s)
        throws BrokerException;

    public abstract void stopMonitoringService(Service s)   
        throws BrokerException;

    public abstract void validate(String name, String value)
        throws PropertyUpdateException;

    public abstract boolean update(String name, String value);
 
    public ConnectionManager getConnectionManager()
    {
        return conmgr;
    }

}
