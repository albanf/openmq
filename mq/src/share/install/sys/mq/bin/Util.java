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

import java.util.*;
import java.io.*;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.lang.reflect.InvocationTargetException;

import com.sun.enterprise.registration.*;

public class Util  {
    private static String versionPropFileName = "/com/sun/messaging/jmq/version.properties";
    private static String comVersionPropFileName = "/com/sun/messaging/jmq/brand_version.properties";
    private static String stRelPathPropName = "mq.install.servicetag.registry.relpath";

    private static Properties loadProps() {
	Properties props = new Properties();

	try  {
	    InputStream is = Object.class.getResourceAsStream(versionPropFileName);
	    if (is == null) {
	        System.err.println("Cannot load file: " + versionPropFileName);
	    }
	    props.load(is);

            // now, overload version which may fail
            try {
	        InputStream isc = Object.class.getResourceAsStream(comVersionPropFileName);
	        if (isc != null) {
	            props.load(isc);
	        }
            } catch (IOException ex) {
                // nothing to do
            }

	    /* 
	    System.out.println("Properties loaded: ");
	    props.list(System.out);
	    */
	} catch (Exception e)  {
	    System.err.println("Caught exception when loading propfile: " + e);
	    e.printStackTrace();
	}
	
	return (props);
    }

    public static Properties getServiceTagProps(String installHome) {
        Properties versionProps = loadProps();
        Properties data = new Properties();
        data.put(ServiceTag.PRODUCT_NAME, versionProps.get("imq.product.name.short"));
        data.put(ServiceTag.PRODUCT_VERSION, versionProps.get("imq.product.version"));
        data.put(ServiceTag.PRODUCT_URN, versionProps.get("imq.product.urn"));
        data.put(ServiceTag.PRODUCT_PARENT_URN, "Unknown");
        data.put(ServiceTag.PRODUCT_PARENT, "Unknown");
        data.put(ServiceTag.PRODUCT_DEFINED_INST_ID,  installHome);

        data.put(ServiceTag.PRODUCT_VENDOR, versionProps.get("imq.product.companyname"));
        data.put(ServiceTag.CONTAINER, "Global");
        data.put(ServiceTag.SOURCE, "Unknown");
        return data;
    }

    /* 
     * Writes product info to local service tags registry file 
     */
    public static void writeLocalRegistry(String regFilePath, String installHome)
		    throws RegistrationException  {
        Properties data = getServiceTagProps(installHome);
	File regFile = null;

	regFile = new File(regFilePath);

	// Delete the local registry file, just before adding tags to it.
	if (regFile != null)  {
	    if (regFile.exists())  {
	        regFile.delete();
	    } else  {
	        /*
	         * Create parent dir if it doesn't exist
	         */
	        if (!regFile.getParentFile().exists())  {
	            regFile.getParentFile().mkdirs();
	        }
	    }
	}

        ServiceTag st = new ServiceTag(data);
        RepositoryManager rm =
                new RepositoryManager(regFile);

        rm.add(st);
    }

    /*
     * Register product with existing account
     */
    public static void registerProductExistingAcct(String username, String password, String regFilePath)
					throws RegistrationException, UnknownHostException,
						ConnectException {
        registerProductExistingAcct(username, password, regFilePath, null, -1);
    }

    /*
     * Register product with existing account
     */
    public static void registerProductExistingAcct(String username, String password, String regFilePath,
					String proxyHost, int proxyPort)
					throws RegistrationException, UnknownHostException,
						ConnectException {
	SysnetRegistrationService rs = null;
	File regFile = null;
	SOAccount account = null;

	regFile = new File(regFilePath);
        rs = new SysnetRegistrationService(regFile, proxyHost, proxyPort);
    
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(RegistrationAccount.USERID, username);
        map.put(RegistrationAccount.PASSWORD, password);
        account = new SOAccount(map);

	/*
	 * Validate account
	 */
        rs.isRegistrationAccountValid(account);

	/*
	 * Register product - send data to Sun Connection DB
	 */
	rs.register(account);

	/*
	 * Sync global service tag registry with local service tag registry.
	 */
	transfer(regFile);
    }


    /*
     * Create a new account and register the product with it.
     */
    public static void registerProductNewAcct(String email, String password,
				String firstName, String lastName, 
				String company,
				String city, String state, String country,
				String regFilePath)
				    throws RegistrationException, ConnectException,
					UnknownHostException  {
        registerProductNewAcct(email, password, firstName, lastName, company,
				city, state, country, regFilePath,
				null, -1);
    }

    public static void registerProductNewAcct(String email, String password,
				String firstName, String lastName, 
				String company,
				String city, String state, String country,
				String regFilePath,
				String proxyHost, int proxyPort)
				    throws RegistrationException, ConnectException,
					UnknownHostException  {
	SysnetRegistrationService rs = null;
	File regFile = null;
	SOAccount account = null;

	regFile = new File(regFilePath);
        rs = new SysnetRegistrationService(regFile, proxyHost, proxyPort);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put(RegistrationAccount.EMAIL, email);
        map.put(RegistrationAccount.PASSWORD, password);
        map.put(RegistrationAccount.USERID, email);
        map.put(RegistrationAccount.FIRSTNAME, firstName);
        map.put(RegistrationAccount.LASTNAME, lastName);
	/*
        map.put("city", city);
        map.put("state", state);
	*/
        map.put(RegistrationAccount.COUNTRY, country);
        map.put("company", company);
        account = new SOAccount(map);

        rs.createRegistrationAccount(account);

	/*
	 * Register product - send data to Sun Connection DB
	 */
	rs.register(account);

	/*
	 * Sync global service tag registry with local service tag registry.
	 */
	transfer(regFile);
    }


    /* Transfers service tag information from local registry (in 'regFile') to
     * global service tag registry on host machine.
     */
    public static void transfer(File regFile) 
			throws RegistrationException  {
	SysnetRegistrationService rs;

	if (!regFile.exists())  {
	    /*
	     * XXX need to figure out how to handle the case where the file does
	     * not exist - corner case.
	     */
	    return;
	}

	rs = new SysnetRegistrationService(regFile);
	rs.transferEligibleServiceTagsToSysNet();
    }

    public static String getServiceTagRegistryPath(String installHome)  {
	String stRelPath = System.getProperty(stRelPathPropName);
	String regFilePath = null;

	if (stRelPath == null)  {
	    stRelPath = "etc/imq/registry/servicetag.xml";
	}

        if (installHome.endsWith(File.separator))  {
            regFilePath = installHome + stRelPath;
        } else  {
            regFilePath = installHome + File.separator + stRelPath;
        }
	
	return (regFilePath);
    }
}
