package com.summitsystemsinc.opc.ua.jmx.types;

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
import com.digitalpetri.opcua.sdk.core.AccessLevel;
import com.digitalpetri.opcua.sdk.server.api.UaNamespace;
import com.digitalpetri.opcua.sdk.server.model.AttributeObserver;
import com.digitalpetri.opcua.sdk.server.model.UaNode;
import com.digitalpetri.opcua.sdk.server.model.UaVariableNode;
import com.digitalpetri.opcua.stack.core.Identifiers;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.QualifiedName;
import com.digitalpetri.opcua.stack.core.types.builtin.Variant;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import static com.digitalpetri.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Justin
 */
public abstract class AbstractTypeNodeFactory implements TypeNodeFactory {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTypeNodeFactory.class);

	public static void setNodeValue(
			ObjectName on,
			String attributeName,
			UaVariableNode node,
			MBeanServerConnection mbsc,
			boolean force)
			throws InstanceNotFoundException, MBeanException,
			AttributeNotFoundException, ReflectionException, IOException {
		final Object attributeValue = mbsc.getAttribute(on, attributeName);
		
		if (force) {
			forceNodeValue(node, attributeValue);
			//This is ugly... can we do something better?
		} else if (node.getValue() == null
				|| node.getValue().getValue() == null
				|| (node.getValue().getValue().getValue() == null && attributeValue != null)
				|| (node.getValue().getValue().getValue() != null && attributeValue == null)
				|| (node.getValue().getValue().getValue() == null && attributeValue == null)
				|| !node.getValue().getValue().getValue().equals(attributeValue)) {
			LOGGER.trace("Node \"{}\" change, updating to: {}", node.getNodeId(),attributeValue);
			forceNodeValue(node, attributeValue);
		}
	}

	private static void forceNodeValue(UaVariableNode node, final Object attributeValue) {
		node.setValue(new DataValue(new Variant(attributeValue)));
	}

	private UaNamespace namespace;
	private MBeanServerConnection mBeanServerConnection;
	private UShort namespaceIndex;
	private final Set<AttributeObserver> observers = new HashSet<>();
	private final Set<UnavailableNodeListener> unavailableNodeListeners
			= Collections.newSetFromMap(new WeakHashMap<UnavailableNodeListener, Boolean>());
	private final Set<UaNode> unavailableNodes = new HashSet<>();

	/**
	 * @return the mBeanServerConnection
	 */
	public MBeanServerConnection getMBeanServerConnection() {
		return mBeanServerConnection;
	}

	/**
	 * @param mBeanServerConnection the mBeanServerConnection to set
	 */
	@Override
	public void setMBeanServerConnection(MBeanServerConnection mBeanServerConnection) {
		this.mBeanServerConnection = mBeanServerConnection;
	}

	/**
	 * @return the namespaceIndex
	 */
	public UShort getNamespaceIndex() {
		return namespaceIndex;
	}

	/**
	 * @param namespaceIndex the namespaceIndex to set
	 */
	@Override
	public void setNamespaceIndex(UShort namespaceIndex) {
		this.namespaceIndex = namespaceIndex;
	}

	/**
	 * @return the namespace
	 */
	public UaNamespace getNamespace() {
		return namespace;
	}

	@Override
	public void removeUnavailableNodeListener(UnavailableNodeListener l) {
		this.unavailableNodeListeners.remove(l);
	}

	@Override
	public void addUnavailableNodeListener(UnavailableNodeListener l) {
		this.unavailableNodeListeners.add(l);
	}

	/**
	 * @param namespace the namespace to set
	 */
	@Override
	public void setNamespace(UaNamespace namespace) {
		this.namespace = namespace;
	}

	protected UaVariableNode buildNodeWithType(String path, ObjectName on, MBeanAttributeInfo info, NodeId type) {
		return buildNodeWithType(path, on, info, type, true);
	}

	private void notifyNodeAvailable(UaNode node) {
		if (unavailableNodes.contains(node)) {
			unavailableNodes.remove(node);
			unavailableNodeListeners.stream().forEach((unl) -> {
				unl.nodeAvailable(node);
			});
		}
	}

	private void notifyNodeUnavailable(UaNode node) {
		if (!unavailableNodes.contains(node)) {
			unavailableNodes.add(node);
			unavailableNodeListeners.stream().forEach((unl) -> {
				unl.nodeUnavailable(node);
			});
		}
	}

	protected UaVariableNode buildNodeWithType(String path, ObjectName on, MBeanAttributeInfo info, NodeId type, boolean registerForPolling) {
		String name = info.getName();

		UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNamespace())
				.setNodeId(new NodeId(getNamespaceIndex(), path + "/" + name))
				.setAccessLevel(ubyte(AccessLevel.getMask(
						info.isWritable() ? AccessLevel.READ_WRITE : AccessLevel.READ_ONLY)))
				.setBrowseName(new QualifiedName(getNamespaceIndex(), name))
				.setDisplayName(LocalizedText.english(name))
				.setDataType(type)
				.setTypeDefinition(Identifiers.BaseDataVariableType)
				.build();
		boolean unavailable = false;

		try {
			setNodeValue(on, info, node);
			notifyNodeAvailable(node);
		} catch (RuntimeMBeanException ex) {
			unavailable = true;
			node.setDataType(Identifiers.String);
			node.setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY)));
			forceNodeValue(node, "UNAVAILABLE");
			notifyNodeUnavailable(node);
		} catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException ex) {
			LOGGER.warn(ex.getMessage(), ex);
		}

		//TODO if r/w we need to listen for changes on the node.
		if (!unavailable && info.isWritable()) {
			final AttributeObserver listener = (UaNode node1, int attributeId, Object value) -> {
				Attribute attr = new Attribute(info.getName(), ((DataValue) value).getValue().getValue());
				try {
					getMBeanServerConnection().setAttribute(on, attr);
				} catch (InstanceNotFoundException | AttributeNotFoundException | InvalidAttributeValueException | MBeanException | ReflectionException | IOException ex) {
					LOGGER.warn(ex.getMessage(), ex);
				}
			};
			observers.add(listener);
			node.addAttributeObserver(listener);
		}

		return node;
	}

	private void setNodeValue(ObjectName on, MBeanAttributeInfo info, UaVariableNode node) throws InstanceNotFoundException, MBeanException, AttributeNotFoundException, ReflectionException, IOException {
		setNodeValue(on, info.getName(), node, mBeanServerConnection, false);
	}

}
