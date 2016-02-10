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
import com.digitalpetri.opcua.sdk.server.model.UaFolderNode;
import com.digitalpetri.opcua.sdk.server.model.UaMethodNode;
import com.digitalpetri.opcua.sdk.server.model.UaNode;
import com.digitalpetri.opcua.sdk.server.model.UaObjectNode;
import com.digitalpetri.opcua.sdk.server.model.UaVariableNode;
import com.digitalpetri.opcua.sdk.server.util.SubscriptionModel;
import com.digitalpetri.opcua.stack.core.Identifiers;
import com.digitalpetri.opcua.stack.core.StatusCodes;
import com.digitalpetri.opcua.stack.core.UaException;
import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
import com.digitalpetri.opcua.stack.core.types.builtin.ExpandedNodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
import com.digitalpetri.opcua.stack.core.types.builtin.QualifiedName;
import com.digitalpetri.opcua.stack.core.types.builtin.StatusCode;
import com.digitalpetri.opcua.stack.core.types.builtin.Variant;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import com.digitalpetri.opcua.stack.core.types.enumerated.NodeClass;
import com.digitalpetri.opcua.stack.core.types.enumerated.TimestampsToReturn;
import com.digitalpetri.opcua.stack.core.types.structured.ReadValueId;
import com.digitalpetri.opcua.stack.core.types.structured.WriteValue;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.PeekingIterator;
import com.summit.opc.ua.jmx.types.DefaultTypes;
import com.summit.opc.ua.jmx.types.TypeNodeFactory;
import com.udojava.jmx.wrapper.JMXBeanWrapper;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
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
	private final UaFolderNode rootFolder;

	private final MBeanServerConnection mBeanServer;

	private final JmxAttributeRefreshWorker refreshWorker;

	private Set<String> unsupportedTypes = new TreeSet<>();
	private Map<String, TypeNodeFactory> typesToFactories = new HashMap<>();

	public JmxNamespace(OpcUaServer server, UShort namespaceIndex, int refreshMs) {
		this(server,
				namespaceIndex,
				DEFAULT_NAMESPACE_URI,
				ManagementFactory.getPlatformMBeanServer(),
				DefaultTypes.DEFAULT_TYPE_FACTORIES,
				refreshMs);
	}

	public JmxNamespace(OpcUaServer server, UShort namespaceIndex) {
		this(server, namespaceIndex, DEFAULT_NAMESPACE_URI,
				ManagementFactory.getPlatformMBeanServer());
	}

	public JmxNamespace(OpcUaServer server,
			UShort namespaceIndex,
			String namespaceUri,
			MBeanServerConnection mBeanServerConnection) {
		this(server, namespaceIndex, DEFAULT_NAMESPACE_URI,
				ManagementFactory.getPlatformMBeanServer(),
				DefaultTypes.DEFAULT_TYPE_FACTORIES);
	}

	public JmxNamespace(OpcUaServer server,
			UShort namespaceIndex,
			String namespaceUri,
			MBeanServerConnection mBeanServerConnection,
			Set<TypeNodeFactory> supportedTypes) {
		this(server,
				namespaceIndex,
				namespaceUri,
				mBeanServerConnection,
				supportedTypes,
				JmxAttributeRefreshWorker.DEFAULT_REFRESH_MS);
	}

	public JmxNamespace(OpcUaServer server,
			UShort namespaceIndex,
			String namespaceUri,
			MBeanServerConnection mBeanServerConnection,
			Set<TypeNodeFactory> supportedTypes, int refreshRate) {
		this.server = server;
		this.namespaceIndex = namespaceIndex;
		this.namespaceUri = namespaceUri;
		this.mBeanServer = mBeanServerConnection;
		this.refreshWorker = new JmxAttributeRefreshWorker(refreshRate, mBeanServer);
		MBeanServer localMBeanServer = ManagementFactory.getPlatformMBeanServer();
		try {
			ObjectName on = new ObjectName(refreshWorker.getClass().getPackage().getName() + ":type=" + refreshWorker.getClass().getSimpleName());
			localMBeanServer.registerMBean(new JMXBeanWrapper(this.refreshWorker), on);

		} catch (MalformedObjectNameException |
				IntrospectionException |
				InstanceAlreadyExistsException |
				MBeanRegistrationException |
				NotCompliantMBeanException ex) {
			LOGGER.warn(ex.getMessage(), ex);
		}

		supportedTypes.stream().forEach((tnf) -> {
			tnf.setMBeanServerConnection(mBeanServer);
			tnf.setNamespace(this);
			tnf.setNamespaceIndex(namespaceIndex);
			tnf.addUnavailableNodeListener(refreshWorker);
			for (String s : tnf.getSupportedTypes()) {
				typesToFactories.put(s, tnf);
			}
		});

		NodeId jmxNodeId = new NodeId(namespaceIndex, getRootName());
		rootFolder = new UaFolderNode(
				this,
				jmxNodeId,
				new QualifiedName(namespaceIndex, getRootName()),
				LocalizedText.english(getRootName()));
		rootFolder.setDescription(Optional.of(LocalizedText.english(getRootDescription())));
		nodes.put(jmxNodeId, rootFolder);

		try {
			server.getUaNamespace().addReference(Identifiers.ObjectsFolder,
					Identifiers.Organizes,
					true,
					jmxNodeId.expanded(),
					NodeClass.Object);
		} catch (UaException e) {
			LOGGER.error("Error adding reference to Connections folder.", e);
		}
		this.subscriptionModel = new SubscriptionModel(server, this);

		populateMBeans();

		unsupportedTypes.stream().forEach((s) -> {
			LOGGER.info("Unsupported Type: {}", s);
		});
	}

	public void startRefresh() {
		refreshWorker.start();
	}

	public void stopRefresh() {
		refreshWorker.stop();
	}

	public boolean isTypeSupported(String type) {
		return typesToFactories.containsKey(type);
	}

	//TODO whitelist/blacklist
	private void populateMBeans() {
		try {
			Set<ObjectName> ons = new TreeSet<>(mBeanServer.queryNames(null, null));
			final AtomicInteger count = new AtomicInteger(1);
			Map<String, UaNode> folders = new HashMap<>();
			ons.stream().forEach((on) -> {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("{}. Object name : {}", count.get(), on);
					LOGGER.debug("{}. Object domain : {}", count.get(), on.getDomain());
					LOGGER.debug("{}. Object cn : {}", count.get(), on.getCanonicalKeyPropertyListString());
				}
				Map<String, String> cnProperties = keyPropertiesToMap(on.getCanonicalKeyPropertyListString());
				StringBuilder pathBuilder = new StringBuilder(on.getDomain().replace('.', '/'));
				pathBuilder.append("/");
				pathBuilder.append(cnProperties.get("type"));
				if (cnProperties.containsKey("name")) {
					pathBuilder.append("/");
					pathBuilder.append(cnProperties.get("name"));
				}
				String path = pathBuilder.toString();
				LOGGER.debug("{}. OPC-UA Path: {}", count.get(), path);
				if (!folders.containsKey(path)) {
					folders.put(path, addFoldersToRoot(path));
				}
				UaNode folder = folders.get(path);

				registerAttributeNodes(path, folder, on);

				count.incrementAndGet();
			});
		} catch (IOException ex) {
			LOGGER.warn(ex.getMessage(), ex);
		}
	}

	private void registerAttributeNodes(String path, UaNode folder, ObjectName on) {
		try {
			final MBeanAttributeInfo[] attributes = mBeanServer.getMBeanInfo(on).getAttributes();
			for (MBeanAttributeInfo info : attributes) {
				StringBuilder readWrite = new StringBuilder();
				readWrite.append(info.isReadable() ? "r" : " ");
				readWrite.append(info.isWritable() ? "w" : " ");
				final String type = info.getType();
				final boolean typeSupported = isTypeSupported(type);
				LOGGER.debug("\t{} [{}] {}{}",
						info.getName(),
						readWrite, type,
						typeSupported ? "*" : "");
				if (typeSupported) {
					UaVariableNode node = typesToFactories.get(type).buildNode(path, on, info);
					folder.addReference(new Reference(
							folder.getNodeId(),
							Identifiers.Organizes,
							node.getNodeId().expanded(),
							node.getNodeClass(),
							true
					));

					LOGGER.debug("Added reference: {} -> {}", folder.getNodeId(), node.getNodeId());
					addNode(node);
					refreshWorker.addPolledAttribute(
							new JmxPolledAttribute(on,
									info.getName(), node));
				} else {
					unsupportedTypes.add(type);
				}
			}
		} catch (InstanceNotFoundException | IntrospectionException | ReflectionException | IOException ex) {
			LOGGER.warn(ex.getMessage(), ex);
		}
	}

	private Map<String, String> keyPropertiesToMap(String keyPropertiesString) {
		Map<String, String> retVal = new HashMap<>();

		String[] keyStrings = keyPropertiesString.split(",");
		for (String s : keyStrings) {
			String[] keyVal = s.split("=");
			retVal.put(keyVal[0], keyVal[1]);
		}

		return retVal;
	}

	@Override
	public void addNode(UaNode node) {
		nodes.put(node.getNodeId(), node);
	}

	@Override
	public Optional<UaNode> getNode(NodeId nodeId
	) {
		return Optional.ofNullable(nodes.get(nodeId));
	}

	@Override
	public Optional<UaNode> getNode(ExpandedNodeId nodeId
	) {
		return nodeId.local().flatMap(this::getNode);
	}

	@Override
	public Optional<UaNode> removeNode(NodeId nodeId
	) {
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

	protected UaObjectNode addFoldersToRoot(String path) {
		return addFolders(rootFolder, path);
	}

	protected UaObjectNode addFolders(UaNode root, String path) {
		if (path.startsWith("/")) {
			path = path.substring(1, path.length());
		}
		String[] elements = path.split("/");

		LinkedList<UaObjectNode> folderNodes = processPathElements(
				Lists.newArrayList(elements),
				Lists.newArrayList(),
				Lists.newLinkedList()
		);

		UaObjectNode firstNode = folderNodes.getFirst();

		if (!nodes.containsKey(firstNode.getNodeId())) {
			nodes.put(firstNode.getNodeId(), firstNode);

			nodes.get(root.getNodeId()).addReference(new Reference(
					root.getNodeId(),
					Identifiers.Organizes,
					firstNode.getNodeId().expanded(),
					firstNode.getNodeClass(),
					true
			));

			LOGGER.debug("Added reference: {} -> {}", root.getNodeId(), firstNode.getNodeId());
		}

		PeekingIterator<UaObjectNode> iterator = Iterators.peekingIterator(folderNodes.iterator());

		while (iterator.hasNext()) {
			UaObjectNode node = iterator.next();

			nodes.putIfAbsent(node.getNodeId(), node);

			if (iterator.hasNext()) {
				UaObjectNode next = iterator.peek();

				if (!nodes.containsKey(next.getNodeId())) {
					nodes.put(next.getNodeId(), next);

					nodes.get(node.getNodeId()).addReference(new Reference(
							node.getNodeId(),
							Identifiers.Organizes,
							next.getNodeId().expanded(),
							next.getNodeClass(),
							true
					));

					LOGGER.debug("Added reference: {} -> {}", node.getNodeId(), next.getNodeId());
				}
			}
		}

		return folderNodes.getLast();
	}

	private LinkedList<UaObjectNode> processPathElements(List<String> elements, List<String> path, LinkedList<UaObjectNode> nodes) {
		if (elements.size() == 1) {
			String name = elements.get(0);
			String prefix = String.join("/", path) + "/";
			if (!prefix.startsWith("/")) {
				prefix = "/" + prefix;
			}

			UaObjectNode node = UaObjectNode.builder(this)
					.setNodeId(new NodeId(namespaceIndex, prefix + name))
					.setBrowseName(new QualifiedName(namespaceIndex, name))
					.setDisplayName(LocalizedText.english(name))
					.setTypeDefinition(Identifiers.FolderType)
					.build();

			nodes.add(node);

			return nodes;
		} else {
			String name = elements.get(0);
			String prefix = String.join("/", path) + "/";
			if (!prefix.startsWith("/")) {
				prefix = "/" + prefix;
			}

			UaObjectNode node = UaObjectNode.builder(this)
					.setNodeId(new NodeId(namespaceIndex, prefix + name))
					.setBrowseName(new QualifiedName(namespaceIndex, name))
					.setDisplayName(LocalizedText.english(name))
					.setTypeDefinition(Identifiers.FolderType)
					.build();

			nodes.add(node);
			path.add(name);

			return processPathElements(elements.subList(1, elements.size()), path, nodes);
		}
	}

	@Override
	public void read(ReadContext context, Double maxAge, TimestampsToReturn timestamps, List<ReadValueId> readValueIds
	) {
		List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

		readValueIds.stream().forEach((id) -> {
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
	public void write(WriteContext context, List<WriteValue> writeValues
	) {
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
	public void onDataItemsCreated(List<DataItem> dataItems
	) {
		subscriptionModel.onDataItemsCreated(dataItems);
	}

	@Override
	public void onDataItemsModified(List<DataItem> dataItems
	) {
		subscriptionModel.onDataItemsModified(dataItems);
	}

	@Override
	public void onDataItemsDeleted(List<DataItem> dataItems
	) {
		subscriptionModel.onDataItemsDeleted(dataItems);
	}

	@Override
	public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems
	) {
		subscriptionModel.onMonitoringModeChanged(monitoredItems);
	}

	@Override
	public Optional<MethodInvocationHandler> getInvocationHandler(NodeId methodId
	) {
		UaNode node = nodes.get(methodId);

		if (node instanceof UaMethodNode) {
			return ((UaMethodNode) node).getInvocationHandler();
		} else {
			return Optional.empty();
		}
	}

	@Override
	public CompletableFuture<List<Reference>> getReferences(NodeId nodeId
	) {
		UaNode node = nodes.get(nodeId);

		if (node != null) {
			return CompletableFuture.completedFuture(node.getReferences());
		} else {
			CompletableFuture<List<Reference>> f = new CompletableFuture<>();
			f.completeExceptionally(new UaException(StatusCodes.Bad_NodeIdUnknown));
			return f;
		}
	}

	private String getRootName() {
		return "jmx";
	}

	private String getRootDescription() {
		return "JMX Root node.";
	}

	/**
	 * @return the typesToFactories
	 */
	protected Map<String, TypeNodeFactory> getTypesToFactories() {
		return typesToFactories;
	}

	/**
	 * @param typesToFactories the typesToFactories to set
	 */
	protected void setTypesToFactories(Map<String, TypeNodeFactory> typesToFactories) {
		this.typesToFactories = typesToFactories;
	}
}
