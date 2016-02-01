package com.summit.opc.ua.jmx;

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
import com.digitalpetri.opcua.sdk.core.Reference;
import com.digitalpetri.opcua.sdk.server.OpcUaServer;
import com.digitalpetri.opcua.sdk.server.api.DataItem;
import com.digitalpetri.opcua.sdk.server.api.MethodInvocationHandler;
import com.digitalpetri.opcua.sdk.server.api.MonitoredItem;
import com.digitalpetri.opcua.sdk.server.api.UaNamespace;
import com.digitalpetri.opcua.sdk.server.model.UaMethodNode;
import com.digitalpetri.opcua.sdk.server.model.UaNode;
import com.digitalpetri.opcua.sdk.server.util.SubscriptionModel;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.StatusCodes;
import com.digitalpetri.opcua.stack.core.UaException;
import com.digitalpetri.opcua.stack.core.types.builtin.ExpandedNodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.StatusCode;
import com.digitalpetri.opcua.stack.core.types.builtin.Variant;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import com.digitalpetri.opcua.stack.core.types.enumerated.TimestampsToReturn;
import com.digitalpetri.opcua.stack.core.types.structured.ReadValueId;
import com.digitalpetri.opcua.stack.core.types.structured.WriteValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OPC-UA Namespace for OPC-UA Server at:
 * <a href="https://github.com/digitalpetri/ua-server-sdk">
 * https://github.com/digitalpetri/ua-server-sdk</a>
 *
 * <p>
 * A lot of the base code was copied from the
 * <a href="https://github.com/digitalpetri/ua-server-sdk/blob/master/ctt-namespace/src/main/java/com/digitalpetri/opcua/server/ctt/CttNamespace.java">CttNamespace
 * Example</a>
 * </p>
 *
 * @author Justin
 */
public class JmxNamespace implements UaNamespace {

	private static final Logger LOGGER = LoggerFactory.getLogger(JmxNamespace.class);

	public static final String DEFAULT_NAMESPACE_URI = "urn:opcua:jmx";
	private final String namespaceUri;
	private final OpcUaServer server;
	private final UShort namespaceIndex;
	private final SubscriptionModel subscriptionModel;
	private final Map<NodeId, UaNode> nodes = Maps.newConcurrentMap();

	public JmxNamespace(OpcUaServer server, UShort namespaceIndex) {
		this(server, namespaceIndex, DEFAULT_NAMESPACE_URI);
	}

	public JmxNamespace(OpcUaServer server, UShort namespaceIndex, String namespaceUri) {
		this.server = server;
		this.namespaceIndex = namespaceIndex;
		this.namespaceUri = namespaceUri;
		this.subscriptionModel = new SubscriptionModel(server, this);
	}

	@Override
	public void addNode(UaNode node) {
		nodes.put(node.getNodeId(), node);
	}

	@Override
	public Optional<UaNode> getNode(NodeId nodeId) {
		return Optional.ofNullable(nodes.get(nodeId));
	}

	@Override
	public Optional<UaNode> getNode(ExpandedNodeId nodeId) {
		return nodeId.local().flatMap(this::getNode);
	}

	@Override
	public Optional<UaNode> removeNode(NodeId nodeId) {
		return Optional.ofNullable(nodes.remove(nodeId));
	}

	@Override
	public UShort getNamespaceIndex() {
		return namespaceIndex;
	}

	@Override
	public String getNamespaceUri() {
		return this.namespaceUri;
	}

	@Override
	public void read(ReadContext context, Double maxAge, TimestampsToReturn timestamps, List<ReadValueId> readValueIds) {
		List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

		readValueIds.parallelStream().forEach((id) -> {
			UaNode node = nodes.get(id.getNodeId());

			if (node != null) {
				DataValue value = node.readAttribute(
						id.getAttributeId().intValue(),
						timestamps,
						id.getIndexRange());

				if (LOGGER.isTraceEnabled()) {
					Variant variant = value.getValue();
					Object o = variant != null ? variant.getValue() : null;
					LOGGER.trace("Read value={} from attributeId={} of {}",
							o, id.getAttributeId(), id.getNodeId());
				}

				results.add(value);
			} else {
				results.add(new DataValue(new StatusCode(StatusCodes.Bad_NodeIdUnknown)));
			}
		});

		context.complete(results);
	}

	@Override
	public void write(WriteContext context, List<WriteValue> writeValues) {
		List<StatusCode> results = Lists.newArrayListWithCapacity(writeValues.size());

		for (WriteValue writeValue : writeValues) {
			try {
				UaNode node = Optional.ofNullable(nodes.get(writeValue.getNodeId()))
						.orElseThrow(() -> new UaException(StatusCodes.Bad_NodeIdUnknown));

				node.writeAttribute(
						server.getNamespaceManager(),
						writeValue.getAttributeId().intValue(),
						writeValue.getValue(),
						writeValue.getIndexRange());

				if (LOGGER.isTraceEnabled()) {
					Variant variant = writeValue.getValue().getValue();
					Object o = variant != null ? variant.getValue() : null;
					LOGGER.trace("Wrote value={} to attributeId={} of {}",
							o, writeValue.getAttributeId(), writeValue.getNodeId());
				}

				results.add(StatusCode.GOOD);
			} catch (UaException e) {
				results.add(e.getStatusCode());
			}
		}
		context.complete(results);
	}

	@Override
	public void onDataItemsCreated(List<DataItem> dataItems) {
		subscriptionModel.onDataItemsCreated(dataItems);
	}

	@Override
	public void onDataItemsModified(List<DataItem> dataItems) {
		subscriptionModel.onDataItemsModified(dataItems);
	}

	@Override
	public void onDataItemsDeleted(List<DataItem> dataItems) {
		subscriptionModel.onDataItemsDeleted(dataItems);
	}

	@Override
	public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
		subscriptionModel.onMonitoringModeChanged(monitoredItems);
	}

	@Override
	public Optional<MethodInvocationHandler> getInvocationHandler(NodeId methodId) {
		UaNode node = nodes.get(methodId);

		if (node instanceof UaMethodNode) {
			return ((UaMethodNode) node).getInvocationHandler();
		} else {
			return Optional.empty();
		}
	}

	@Override
	public CompletableFuture<List<Reference>> getReferences(NodeId nodeId) {
		UaNode node = nodes.get(nodeId);

		if (node != null) {
			return CompletableFuture.completedFuture(node.getReferences());
		} else {
			CompletableFuture<List<Reference>> f = new CompletableFuture<>();
			f.completeExceptionally(new UaException(StatusCodes.Bad_NodeIdUnknown));
			return f;
		}
	}
}
