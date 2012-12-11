/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2000-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
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

package com.sun.messaging.jmq.jmsclient;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.MessageNotWriteableException;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.MessageFormatRuntimeException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;

import com.sun.messaging.AdministeredObject;
import com.sun.messaging.jmq.jmsclient.resources.ClientResources;
import com.sun.messaging.jms.MQInvalidDestinationRuntimeException;
import com.sun.messaging.jms.MQMessageNotWriteableRuntimeException;
import com.sun.messaging.jms.MQMessageFormatRuntimeException;
import com.sun.messaging.jms.MQRuntimeException;

public class JMSProducerImpl implements JMSProducer, Traceable {
	
	JMSContextImpl contextImpl;
	
	// message delivery options
	boolean disableMessageID=false;
	boolean disableMessageTimestamp=false;
	int deliveryMode=DeliveryMode.PERSISTENT;
	int priority=Message.DEFAULT_PRIORITY;
	long timeToLive=Message.DEFAULT_TIME_TO_LIVE;
	long deliveryDelay=Message.DEFAULT_DELIVERY_DELAY;
	
	// message headers
	String jmsCorrelationID=null;
	byte[] jmsCorrelationIDAsBytes=null;
	String jmsType=null;
	Destination jmsReplyTo=null;
	
	// message properties
	Hashtable<String,Object> properties = new Hashtable<String,Object>();

	// CompletionListener (if set, send is async)
	private CompletionListener completionListener=null;

	public JMSProducerImpl(JMSContextImpl contextImpl) {
		this.contextImpl=contextImpl;
	}

	@Override
	public void dump(PrintStream ps) {
		ps.println ("------ JMSProducerImpl dump start ------");
		ps.println("deliveryMode="+deliveryMode);
		ps.println("priority="+priority);
		ps.println("timeToLive="+timeToLive);
		ps.println("deliveryDelay="+deliveryDelay);
		ps.println("disableMessageID="+disableMessageID);
		ps.println("disableMessageTimestamp="+disableMessageTimestamp);	
		ps.println ("------ JMSProducerImpl dump end ------");
	}

	@Override
	public JMSProducer send(Destination destination, Message message) {
		contextImpl.checkNotClosed();
		checkMessage(message);
		configureMessageProducer();
		configureMessage(message);
		try {
			if (completionListener==null){
				contextImpl.getMessageProducer().send(destination,message);
			} else {
				contextImpl.getMessageProducer().send(destination,message,completionListener);
			}
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		return this;
	}

	@Override
	public JMSProducer send(Destination destination, String payload) {
		contextImpl.checkNotClosed();
		configureMessageProducer();
		TextMessage textMessage;
		if (payload==null){
			textMessage = contextImpl.createTextMessage();
		} else {
			textMessage = contextImpl.createTextMessage(payload);
		}
		configureMessage(textMessage);
		try {
			if (completionListener==null){
				contextImpl.getMessageProducer().send(destination,textMessage);
			} else {
				contextImpl.getMessageProducer().send(destination,textMessage,completionListener);
			}
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}	
		return this;
	}

	@Override
	public JMSProducer send(Destination destination, Map<String, Object> payload) {
		contextImpl.checkNotClosed();
		configureMessageProducer();
		MapMessage mapMessage = contextImpl.createMapMessage();
		configureMessage(mapMessage);
		if (payload!=null){
			try {
				for (Iterator<Entry<String, Object>> entryIter = payload.entrySet().iterator(); entryIter.hasNext();) {
					Entry<String, Object> thisEntry = entryIter.next();
					mapMessage.setObject((String) thisEntry.getKey(), thisEntry.getValue());
				}
			} catch (MessageNotWriteableException e) {
				throw new MQMessageNotWriteableRuntimeException(e);
			} catch (MessageFormatException e) {
				throw new MQMessageFormatRuntimeException(e);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}			
		}
		try {
			if (completionListener==null){
				contextImpl.getMessageProducer().send(destination,mapMessage);
			} else {
				contextImpl.getMessageProducer().send(destination,mapMessage,completionListener);
			}
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}	
		return this;
	}

	@Override
	public JMSProducer send(Destination destination, byte[] payload) {
		contextImpl.checkNotClosed();
		configureMessageProducer();
		BytesMessage bytesMessage = contextImpl.createBytesMessage();
		configureMessage(bytesMessage);
		if (payload!=null){
			try {
				bytesMessage.writeBytes(payload);
			} catch (MessageNotWriteableException e) {
				throw new MQMessageNotWriteableRuntimeException(e);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}
		try {
			if (completionListener==null){
				contextImpl.getMessageProducer().send(destination,bytesMessage);
			} else {
				contextImpl.getMessageProducer().send(destination,bytesMessage,completionListener);
			}
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}	
		return this;
	}


	@Override
	public JMSProducer send(Destination destination, Serializable payload) {
		contextImpl.checkNotClosed();
		configureMessageProducer();
		ObjectMessage objectMessage = contextImpl.createObjectMessage(payload);
		configureMessage(objectMessage);
		try {
			if (completionListener==null){
				contextImpl.getMessageProducer().send(destination,objectMessage);
			} else {
				contextImpl.getMessageProducer().send(destination,objectMessage,completionListener);				
			}
		} catch (InvalidDestinationException e) {
			throw new MQInvalidDestinationRuntimeException(e);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}	
		return this;
	}
	
	/**
	 * Configure the MessageProducer prior to sending a message
	 * 
	 * Note that although the MessageProducer is associated with the JMSContext
	 * and so may be used by other JMSConsumer objects for that JMSContext
	 * only one thread is allowed to send a message at a time so this code
	 * does not need to be threadsafe. 
	 */
	private void configureMessageProducer() {
		MessageProducer messageProducer = contextImpl.getMessageProducer();
		try {
			messageProducer.setPriority(priority);
			messageProducer.setDeliveryDelay(deliveryDelay);
			messageProducer.setDeliveryMode(deliveryMode);
			messageProducer.setTimeToLive(timeToLive);
			messageProducer.setDisableMessageID(disableMessageID);
			messageProducer.setDisableMessageTimestamp(disableMessageTimestamp);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
	}
	
	/**
	 * Check that the specified message is valid
	 * @param message
	 */
	private void checkMessage(Message message) {
		if (message==null){
			// "Message is null"
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_MESSAGE_IS_NULL);
			throw new MessageFormatRuntimeException(errorString,ClientResources.X_MESSAGE_IS_NULL);
		}
	}
	
	/**
	 * Configure the specified Message before sending it
	 * 
	 * Set the specified message headers and properties
	 * 
	 * @param message
	 */
	private void configureMessage(Message message) {
		if (jmsCorrelationID!=null){
			try {
				message.setJMSCorrelationID(jmsCorrelationID);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}
		if (jmsCorrelationIDAsBytes!=null){
			try {
				message.setJMSCorrelationIDAsBytes(jmsCorrelationIDAsBytes);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}		
		if (jmsType!=null){
			try {
				message.setJMSType(jmsType);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}	
		if (jmsReplyTo!=null){
			try {
				message.setJMSReplyTo(jmsReplyTo);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}
		
		for (Iterator<Entry<String, Object>> iterator = properties.entrySet().iterator(); iterator.hasNext();) {
			Entry<String, Object> thisEntry = iterator.next();
			try {
				message.setObjectProperty(thisEntry.getKey(), thisEntry.getValue());
			} catch (MessageNotWriteableException e) {
				throw new MQMessageNotWriteableRuntimeException(e);
			} catch (MessageFormatException e) {
				throw new MQMessageFormatRuntimeException(e);
			} catch (JMSException e) {
				throw new MQRuntimeException(e);
			}
		}
	}

	@Override
	public JMSProducer setDisableMessageID(boolean disableMessageID) {
		contextImpl.checkNotClosed();
		this.disableMessageID=disableMessageID;
		return this;
	}

	@Override
	public boolean getDisableMessageID() {
		contextImpl.checkNotClosed();
		return disableMessageID;
	}

	@Override
	public JMSProducer setDisableMessageTimestamp(boolean disableMessageTimestamp) {
		contextImpl.checkNotClosed();
		this.disableMessageTimestamp=disableMessageTimestamp;
		return this;
	}

	@Override
	public boolean getDisableMessageTimestamp() {
		contextImpl.checkNotClosed();
		return disableMessageTimestamp;
	}

	@Override
	public JMSProducer setDeliveryMode(int deliveryMode) {
		contextImpl.checkNotClosed();
		this.deliveryMode=deliveryMode;
		return this;
	}

	@Override
	public int getDeliveryMode() {
		contextImpl.checkNotClosed();
		return deliveryMode;
	}

	@Override
	public JMSProducer setPriority(int priority) {
		contextImpl.checkNotClosed();
		this.priority=priority;
		return this;
	}

	@Override
	public int getPriority() {
		contextImpl.checkNotClosed();
		return priority;
	}

	@Override
	public JMSProducer setTimeToLive(long timeToLive) {
		contextImpl.checkNotClosed();
		this.timeToLive=timeToLive;
		return this;
	}

	@Override
	public long getTimeToLive() {
		contextImpl.checkNotClosed();
		return timeToLive;
	}

	@Override
	public JMSProducer setDeliveryDelay(long deliveryDelay) {
		contextImpl.checkNotClosed();
		this.deliveryDelay=deliveryDelay;
		return this;
	}

	@Override
	public long getDeliveryDelay() {
		contextImpl.checkNotClosed();
		return deliveryDelay;
	}

	@Override
	public JMSProducer setAsync(CompletionListener completionListener) {
		// this method is not permitted in the Java EE web or EJB containers
		if (contextImpl.getContainerType()==ContainerType.JavaEE_Web_or_EJB){
			// "This method may not be called in a Java EE web or EJB container"
            String errorString = AdministeredObject.cr.getKString(ClientResources.X_FORBIDDEN_IN_JAVAEE_WEB_EJB);
            JMSRuntimeException jmsre = new com.sun.messaging.jms.MQRuntimeException(errorString,ClientResources.X_FORBIDDEN_IN_JAVAEE_WEB_EJB);
			ExceptionHandler.throwJMSRuntimeException(jmsre);
		}
		
		contextImpl.checkNotClosed();
		this.completionListener = completionListener;
		return this;
	}

	@Override
	public CompletionListener getAsync() {
		contextImpl.checkNotClosed();
		return completionListener;
	}

	@Override
	public JMSProducer setProperty(String name, boolean value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,value);
		return this;
	}

	private void checkAndSetProperty(String name, Object value) {
		// Verify that the specified property name is not null and is not empty
		MessageImpl.checkPropertyNameSet(name);

		// Verify that the specified value is a valid message property value
		try {
			MessageImpl.checkValidPropertyValue(name, value);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}

		// Verify that the specified property name is allowed
		try {
			MessageImpl.checkValidPropertyName(name);
		} catch (JMSException e) {
			throw new MQRuntimeException(e);
		}
		
		// all OK, now set the property
		properties.put(name, value);
	}

	@Override
	public JMSProducer setProperty(String name, byte value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,new Byte(value));
		return this;
	}

	@Override
	public JMSProducer setProperty(String name, short value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,new Short(value));
		return this;
	}

	@Override
	public JMSProducer setProperty(String name, int value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,new Integer(value));
		return this;
	}

	@Override
	public JMSProducer setProperty(String name, long value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,new Long(value));
		return this;
	}

	@Override
	public JMSProducer setProperty(String name, float value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,new Float(value));
		return this;
	}

	@Override
	public JMSProducer setProperty(String name, double value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,new Double(value));
		return this;
	}

	@Override
	public JMSProducer setProperty(String name, String value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,value);
		return this;
	}

	@Override
	public JMSProducer setProperty(String name, Object value) {
		contextImpl.checkNotClosed();
		checkAndSetProperty(name,value);
		return this;
	}

	@Override
	public JMSProducer clearProperties() {
		contextImpl.checkNotClosed();
		properties.clear();
		return this;
	}

	@Override
	public boolean propertyExists(String name) {
		contextImpl.checkNotClosed();
		MessageImpl.checkPropertyNameSet(name);
		return properties.containsKey(name);		
	}

	@Override
	public boolean getBooleanProperty(String name) {
		contextImpl.checkNotClosed();
		MessageImpl.checkPropertyNameSet(name);
	    try {
			return ValueConvert.toBoolean(properties.get(name));
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public byte getByteProperty(String name) {
		contextImpl.checkNotClosed();
		MessageImpl.checkPropertyNameSet(name);
		try {
			return ValueConvert.toByte(properties.get(name));
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public short getShortProperty(String name) {
		contextImpl.checkNotClosed();
		MessageImpl.checkPropertyNameSet(name);
		
    	Object obj = properties.get(name);
	    try {
			return ValueConvert.toShort(obj);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public int getIntProperty(String name) {
		contextImpl.checkNotClosed();
		MessageImpl.checkPropertyNameSet(name);
		
    	Object obj = properties.get(name);
	    try {
			return ValueConvert.toInt(obj);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public long getLongProperty(String name) {
    	Object obj = properties.get(name);
	    try {
			return ValueConvert.toLong(obj);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public float getFloatProperty(String name) {
    	Object obj = properties.get(name);
	    try {
			return ValueConvert.toFloat(obj);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public double getDoubleProperty(String name) {
    	Object obj = properties.get(name);
	    try {
			return ValueConvert.toDouble(obj);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public String getStringProperty(String name) {
    	Object obj = properties.get(name);
	    try {
			return ValueConvert.toString(obj);
		} catch (MessageFormatException e) {
			throw new MQMessageFormatRuntimeException(e);
		}
	}

	@Override
	public Object getObjectProperty(String name) {
		contextImpl.checkNotClosed(); 
    	Object obj = properties.get(name);
    	return obj;
	}

	@Override
	public Enumeration<String> getPropertyNames() {
		contextImpl.checkNotClosed();
		return properties.keys();
	}

	@Override
	public JMSProducer setJMSCorrelationIDAsBytes(byte[] correlationID) {
		contextImpl.checkNotClosed();
		jmsCorrelationIDAsBytes=correlationID;
		jmsCorrelationID=null;
		return this;
	}

	@Override
	public byte[] getJMSCorrelationIDAsBytes() {
		contextImpl.checkNotClosed();
		return jmsCorrelationIDAsBytes;
	}

	@Override
	public JMSProducer setJMSCorrelationID(String correlationID) {
		contextImpl.checkNotClosed();
		jmsCorrelationID=correlationID;
		jmsCorrelationIDAsBytes=null;
		return this;
	}

	@Override
	public String getJMSCorrelationID() {
		contextImpl.checkNotClosed();
		return jmsCorrelationID;
	}

	@Override
	public JMSProducer setJMSType(String type) {
		contextImpl.checkNotClosed();
		this.jmsType=type;
		return this;
	}

	@Override
	public String getJMSType() {
		contextImpl.checkNotClosed();
		return jmsType;
	}

	@Override
	public JMSProducer setJMSReplyTo(Destination replyTo) {
		contextImpl.checkNotClosed();
		this.jmsReplyTo=replyTo;
		return this;
	}

	@Override
	public Destination getJMSReplyTo() {
		contextImpl.checkNotClosed();
		return jmsReplyTo;
	}
	
}
