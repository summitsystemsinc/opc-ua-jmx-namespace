package com.summit.opc.ua.jmx;

import com.digitalpetri.opcua.sdk.server.model.UaNode;
import com.summit.opc.ua.jmx.types.AbstractTypeNodeFactory;
import com.summit.opc.ua.jmx.types.TypeNodeFactory;
import com.udojava.jmx.wrapper.JMXBean;
import com.udojava.jmx.wrapper.JMXBeanAttribute;
import com.udojava.jmx.wrapper.JMXBeanOperation;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ReflectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * #%L
 * OPC-UA :: Namespace :: JMX
 * %%
 * Copyright (C) 2016 Summit Management Systems, Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
/**
 *
 * @author Justin
 */
@JMXBean(description = "JMX Attribute refresh worker.  "
		+ "Updates JMX values to OPC nodes")
public class JmxAttributeRefreshWorker implements Runnable,
		TypeNodeFactory.UnavailableNodeListener {

	private static final Logger LOGGER
			= LoggerFactory.getLogger(JmxAttributeRefreshWorker.class);

	public static final int DEFAULT_REFRESH_MS = 1000;

	private static final ScheduledExecutorService SCHEDULED_THREAD
			= Executors.newSingleThreadScheduledExecutor((Runnable r) -> {
				Thread t = new Thread(r, "JMX-UA Refresh");
				t.setDaemon(true);
				return t;
			});

	/**
	 * JMX reported these values were unavailable.
	 */
	private final Set<UaNode> unavailableNodes = new HashSet<>();

	private final Set<JmxPolledAttribute> polledAttributes;
	private MBeanServerConnection mBeanServerConnection;
	private int refreshMs;
	private ScheduledFuture future;

	public JmxAttributeRefreshWorker() {
		this(DEFAULT_REFRESH_MS);
	}

	public JmxAttributeRefreshWorker(int refreshMs) {
		this.polledAttributes = new HashSet<>();
		this.refreshMs = refreshMs;
	}

	public JmxAttributeRefreshWorker(int refreshMs, MBeanServerConnection mbsc) {
		this(refreshMs);
		this.mBeanServerConnection = mbsc;
	}

	public void addPolledAttribute(JmxPolledAttribute polledAttribute) {
		this.polledAttributes.add(polledAttribute);
	}

	public void removePolledAttribute(JmxPolledAttribute polledAttribute) {
		this.polledAttributes.remove(polledAttribute);
	}

	@JMXBeanOperation(name = "Start Refresh", description = "Start the "
			+ "refresh thread. Only works if not running.")
	public void start() {
		if (future == null || future.isDone() || future.isCancelled()) {
			LOGGER.info("Starting JMX-UA Refresh task with {}ms delay.", getRefreshMs());
			future = SCHEDULED_THREAD.scheduleWithFixedDelay(this,
					0, getRefreshMs(),
					TimeUnit.MILLISECONDS);
		}
	}

	@JMXBeanAttribute(description = "Number of JMX attributes that threw a Java"
			+ "Exception when we tried to read them.")
	public int getNumberUnavailableNodes() {
		return unavailableNodes.size();
	}

	@JMXBeanAttribute(description = "Names/Paths of nodes that threw a Java"
			+ "Exception when we tried to read them.")
	public String[] getUnavailableNodeNames() {
		Set<String> retVal = new TreeSet<>();

		unavailableNodes.stream().forEach((n) -> {
			retVal.add(n.getNodeId().getIdentifier().toString());
		});

		return retVal.toArray(new String[retVal.size()]);
	}

	@JMXBeanOperation(name = "Stop Refresh", description = "Stop the "
			+ "refresh thread.")
	public void stop() {
		if (future != null) {
			LOGGER.info("Stopping JMX-UA task.");
			future.cancel(true);
		}
	}

	@Override
	public void run() {
		LOGGER.debug("Refreshing {} JMX Attributes to nodes with {} unavailable.", 
				polledAttributes.size(), unavailableNodes.size());
		long startTime = System.currentTimeMillis();

		Iterator<JmxPolledAttribute> iter = polledAttributes.iterator();
		while (iter.hasNext()) {
			JmxPolledAttribute jpa = iter.next();
			if (!unavailableNodes.contains(jpa.getNode())) {
				try {
					AbstractTypeNodeFactory.setNodeValue(jpa.getObjectName(), jpa.getAttributeName(), jpa.getNode(), mBeanServerConnection, false);
				} catch (InstanceNotFoundException | 
						MBeanException | 
						AttributeNotFoundException | 
						ReflectionException | 
						IOException ex) {
					LOGGER.warn(ex.getMessage(), ex);
				}
			}
		}

		long endTime = System.currentTimeMillis();
		LOGGER.debug("Refreshing complete.  Took {}ms", endTime - startTime);
	}

	/**
	 * @return the refreshMs
	 */
	@JMXBeanAttribute
	public int getRefreshMs() {
		return refreshMs;
	}

	/**
	 * @param refreshMs the refreshMs to set
	 */
	@JMXBeanAttribute(description
			= "Interval this refresh worker is triggered, in milliseconds.")
	public void setRefreshMs(int refreshMs) {
		stop();
		this.refreshMs = refreshMs;
		start();
	}

	/**
	 * @return the mBeanServerConnection
	 */
	public MBeanServerConnection getmBeanServerConnection() {
		return mBeanServerConnection;
	}

	/**
	 * @param mBeanServerConnection the mBeanServerConnection to set
	 */
	public void setmBeanServerConnection(MBeanServerConnection mBeanServerConnection) {
		this.mBeanServerConnection = mBeanServerConnection;
	}

	@Override
	public void nodeUnavailable(UaNode node) {
		unavailableNodes.add(node);
	}

	@Override
	public void nodeAvailable(UaNode node) {
		unavailableNodes.remove(node);
	}
}
