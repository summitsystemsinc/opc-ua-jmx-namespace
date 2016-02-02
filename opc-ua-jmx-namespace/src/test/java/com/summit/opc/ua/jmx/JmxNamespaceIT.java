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
import com.digitalpetri.opcua.sdk.server.OpcUaServer;
import com.digitalpetri.opcua.sdk.server.api.config.OpcUaServerConfig;
import com.digitalpetri.opcua.sdk.server.identity.UsernameIdentityValidator;
import com.digitalpetri.opcua.stack.core.Stack;
import com.digitalpetri.opcua.stack.core.application.CertificateManager;
import com.digitalpetri.opcua.stack.core.application.CertificateValidator;
import com.digitalpetri.opcua.stack.core.application.DefaultCertificateManager;
import com.digitalpetri.opcua.stack.core.application.DefaultCertificateValidator;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import com.digitalpetri.opcua.stack.core.types.structured.UserTokenPolicy;
import static com.google.common.collect.Lists.newArrayList;
import java.io.File;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Justin
 */
public class JmxNamespaceIT {

	@BeforeClass
	public static void setUpClass() {
	}

	@AfterClass
	public static void tearDownClass() {
	}
	private OpcUaServer server;

	public JmxNamespaceIT() {
	}

	@Before
	public void setUp() {
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
				.setServerName("")
				.build();

		server = new OpcUaServer(config);

		UShort idx = server.getNamespaceManager().registerUri(JmxNamespace.DEFAULT_NAMESPACE_URI);
		JmxNamespace nx = new JmxNamespace(server, idx);
		// register a CttNamespace so we have some nodes to play with
		server.getNamespaceManager().addNamespace(nx);

		server.startup();
	}

	@After
	public void tearDown() {
		server.shutdown();
		Stack.releaseSharedResources();
	}

	@Test
	public void testNamespaceCreation() {

	}
}
