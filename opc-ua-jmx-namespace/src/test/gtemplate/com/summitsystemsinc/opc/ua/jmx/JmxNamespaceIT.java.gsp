package com.summitsystemsinc.opc.ua.jmx;

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
 import com.digitalpetri.opcua.sdk.client.OpcUaClient;
 import com.digitalpetri.opcua.sdk.client.api.config.OpcUaClientConfig;
 import com.digitalpetri.opcua.sdk.client.api.nodes.attached.UaVariableNode;
 import com.digitalpetri.opcua.sdk.server.OpcUaServer;
 import com.digitalpetri.opcua.sdk.server.api.config.OpcUaServerConfig;
 import com.digitalpetri.opcua.sdk.server.identity.UsernameIdentityValidator;
 import com.digitalpetri.opcua.stack.client.UaTcpStackClient;
 import com.digitalpetri.opcua.stack.core.Stack;
 import com.digitalpetri.opcua.stack.core.application.CertificateManager;
 import com.digitalpetri.opcua.stack.core.application.CertificateValidator;
 import com.digitalpetri.opcua.stack.core.application.DefaultCertificateManager;
 import com.digitalpetri.opcua.stack.core.application.DefaultCertificateValidator;
 import com.digitalpetri.opcua.stack.core.security.SecurityPolicy;
 import com.digitalpetri.opcua.stack.core.types.builtin.DataValue;
 import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
 import com.digitalpetri.opcua.stack.core.types.builtin.NodeId;
 import com.digitalpetri.opcua.stack.core.types.builtin.Variant;
 import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
 import static com.digitalpetri.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
 import com.digitalpetri.opcua.stack.core.types.enumerated.TimestampsToReturn;
 import com.digitalpetri.opcua.stack.core.types.structured.EndpointDescription;
 import com.digitalpetri.opcua.stack.core.types.structured.UserTokenPolicy;
 import static com.google.common.collect.Lists.newArrayList;
 import com.summitsystemsinc.opc.ua.jmx.types.DefaultTypes;
 import com.summitsystemsinc.opc.ua.jmx.types.TypeNodeFactory;
 import com.udojava.jmx.wrapper.JMXBean;
 import com.udojava.jmx.wrapper.JMXBeanAttribute;
 import com.udojava.jmx.wrapper.JMXBeanWrapper;
 import java.io.File;
 import java.lang.management.ManagementFactory;
 import java.util.Arrays;
 import java.util.List;
 import java.util.Random;
 import java.util.concurrent.ExecutionException;
 import javax.management.MBeanServer;
 import javax.management.ObjectName;
 import org.apache.commons.lang3.RandomStringUtils;
 import org.junit.After;
 import org.junit.AfterClass;
 import static org.junit.Assert.*;
 import org.junit.Before;
 import org.junit.BeforeClass;
 import org.junit.FixMethodOrder;
 import org.junit.Test;
 import org.junit.runners.MethodSorters;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

/**
 *
 * @author Justin Smith
 */
 @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class JmxNamespaceIT {

	private static final Logger LOGGER = LoggerFactory.getLogger(JmxNamespaceIT.class);

	private static final int BIND_PORT = 21212;
	private static final String ENDPOINT_URL = "opc.tcp://localhost:" + BIND_PORT;
	private static OpcUaServer server;

	private static final ExampleMBean exampleMBean = new ExampleMBean();

	private OpcUaClient client;

	private static final Random RANDOM = new Random();
	private static final int REFRESH_SPEED = 200;

	@BeforeClass
	public static void setUpClass() throws Exception {
		LOGGER.info("Register JMXBean");
		JMXBeanWrapper wrapper = new JMXBeanWrapper(exampleMBean);
		MBeanServer localMBeanServer = ManagementFactory.getPlatformMBeanServer();
		ObjectName on = new ObjectName(ExampleMBean.class.getPackage().getName() + ":type=" + ExampleMBean.class.getSimpleName());
		localMBeanServer.registerMBean(wrapper, on);

		LOGGER.info("Setting up OPC-UA server.");

		UsernameIdentityValidator identityValidator = new UsernameIdentityValidator(
				true, // allow anonymous access
				authenticationChallenge
				-> "user".equals(authenticationChallenge.getUsername())
				&& "password".equals(authenticationChallenge.getPassword())
		);

		List<UserTokenPolicy> userTokenPolicies = newArrayList(
				OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS,
				OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME
		);

		CertificateManager certificateManager = new DefaultCertificateManager();
		CertificateValidator certificateValidator = new DefaultCertificateValidator(new File("./security"));

		OpcUaServerConfig config = OpcUaServerConfig.builder()
				.setApplicationName(LocalizedText.english("summit opc-ua test-server"))
				.setApplicationUri("urn:summit:opcua:testServer")
				.setCertificateManager(certificateManager)
				.setCertificateValidator(certificateValidator)
				.setIdentityValidator(identityValidator)
				.setUserTokenPolicies(userTokenPolicies)
				.setProductUri("urn:summit:opcua:sdk")
				.setBindPort(BIND_PORT)
				.setServerName("")
				.build();

		server = new OpcUaServer(config);

		UShort idx = server.getNamespaceManager().registerUri(JmxNamespace.DEFAULT_NAMESPACE_URI);
		JmxNamespace nx = new JmxNamespace(server, idx,REFRESH_SPEED);
		nx.startRefresh();
		LOGGER.info(String.format("Sleeping for %s milliseconds to start refresh thread.",REFRESH_SPEED));
		Thread.sleep(REFRESH_SPEED);

		server.getNamespaceManager().addNamespace(nx);

		server.startup();
	}

	@AfterClass
	public static void tearDownClass() {
		server.shutdown();
		Stack.releaseSharedResources();
	}

	public JmxNamespaceIT() {
	}

	@Before
	public void setUp() throws Exception {

		EndpointDescription[] endpoints = UaTcpStackClient.getEndpoints(ENDPOINT_URL).get();

		EndpointDescription endpoint = Arrays.stream(endpoints)
                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getSecurityPolicyUri()))
                .findFirst().orElseThrow(() -> new Exception("no desired endpoints returned"));

		OpcUaClientConfig config = OpcUaClientConfig.builder()
				.setApplicationName(LocalizedText.english("digitalpetri opc-ua client"))
				.setApplicationUri("urn:digitalpetri:opcua:client")
				.setEndpoint(endpoint)
				.setRequestTimeout(uint(5000))
				.build();

				client = new OpcUaClient(config);
				client.connect().get();
	}

	@After
	public void tearDown() throws Exception {
		client.disconnect().get();
	}
	<%

		def nodeId = { String prim ->
			def retVal = '';
			retVal += "String nodePath = \"ns=2;s=com/summitsystemsinc/opc/ua/jmx/ExampleMBean/my_${prim}\";\n";
			retVal += "NodeId my_${prim}Node = NodeId.parse(nodePath);\n"
			retVal;
		};

		def node = { String prim ->
			def retVal = '';
			retVal += nodeId(prim);
			retVal += "UaVariableNode node = client.getAddressSpace().getVariableNode(my_${prim}Node);\n"
			retVal
		}

		def dv = { String prim ->
			def retVal = ''
			retVal += node(prim)
			retVal += "DataValue dv = node.readValue().get();\n"
			retVal
		}
	%>
<% for(def prim : prims.keySet()) { %>
	@Test
	public void test1ConverterExists_${prim}() {
		LOGGER.info("Testing type converter exists for {}","${prim}");
		TypeNodeFactory f =  DefaultTypes.DEFAULT_TYPE_FACTORIES_CACHE.get("${prim}");
		assertNotNull("Type converter missing for '${prim}'", f);
	}

	@Test
	public void test2NodeIdExists_${prim}() throws InterruptedException, ExecutionException{
		LOGGER.info("Testing type opc-ua support exists for {}.","${prim}");

		${nodeId(prim)}
		assertNotNull("Node for \\"${prim}\\" does not exist.", my_${prim}Node);
	}

	@Test
	public void test3NodeExists_${prim}() throws InterruptedException, ExecutionException{
		LOGGER.info("Testing that only one value exists for path for ${prim}");
		${node(prim)}

		assertNotNull("Node for ${prim} was null.", node);
	}

	@Test
	public void test4DataValue_${prim}() throws InterruptedException, ExecutionException{
		LOGGER.info("Test data values for ${prim}");

		${dv(prim)}
		assertNotNull("DataValue Variant was null.", dv.getValue());

		//Test variant data against the mbean.
		Variant v = dv.getValue();
		LOGGER.info("DV: {}",dv);
		assertEquals(String.format("Value for ${prim} incorrect.",exampleMBean.my_${prim},v.getValue()),exampleMBean.my_${prim},v.getValue());
	}
<% } %>

	@JMXBean(description = "Example MXBean")
	public static class ExampleMBean {
		private static final Random RANDOM = new Random();

<% for(def prim : prims.keySet()) { %>
		private ${prim} my_${prim} = ${prims[prim]["rand"]};
<% } %>
<% for(def prim : prims.keySet()) { %>

		@JMXBeanAttribute
		public $prim getMy_${prim}(){
			return my_$prim;
		}

		@JMXBeanAttribute
		public void setMy_${prim}(${prim} newVal){
			my_${prim} = newVal;
		}
<% } %>
	}
}
