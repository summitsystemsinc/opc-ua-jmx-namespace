package com.summit.opc.jmx.namespace.example;

/*
 * #%L
 * OPC-UA :: Namespace :: JMX :: Example
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
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.digitalpetri.opcua.sdk.server.OpcUaServer;
import com.digitalpetri.opcua.sdk.server.api.config.OpcUaServerConfig;
import com.digitalpetri.opcua.sdk.server.identity.UsernameIdentityValidator;
import com.digitalpetri.opcua.server.ctt.CttNamespace;
import com.digitalpetri.opcua.stack.core.Stack;
import com.digitalpetri.opcua.stack.core.application.CertificateManager;
import com.digitalpetri.opcua.stack.core.application.CertificateValidator;
import com.digitalpetri.opcua.stack.core.application.DefaultCertificateManager;
import com.digitalpetri.opcua.stack.core.application.DefaultCertificateValidator;
import com.digitalpetri.opcua.stack.core.types.builtin.LocalizedText;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import com.digitalpetri.opcua.stack.core.types.structured.UserTokenPolicy;
import static com.google.common.collect.Lists.newArrayList;
import com.summit.opc.ua.jmx.JmxNamespace;

/**
 *
 * @author Justin
 */
public class ServerExample {

	public static void main(String[] args) throws ExecutionException, InterruptedException {
		ServerExample serverExample = new ServerExample();
		serverExample.startup();

		serverExample.shutdownFuture().get();
	}

	private final OpcUaServer server;

	public ServerExample() {
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

		UShort jmxId = server.getNamespaceManager().registerUri(JmxNamespace.DEFAULT_NAMESPACE_URI);
		jmxNamespace = new JmxNamespace(server, jmxId);
		server.getNamespaceManager().addNamespace(jmxNamespace);

		// register a CttNamespace so we have some nodes to play with
		server.getNamespaceManager().registerAndAdd(
				CttNamespace.NAMESPACE_URI,
				cttIdx -> new CttNamespace(server, cttIdx));
	}
	private final JmxNamespace jmxNamespace;

	public void startup() {
		server.startup();
		jmxNamespace.startRefresh();
	}

	private CompletableFuture<Void> shutdownFuture() {
		CompletableFuture<Void> future = new CompletableFuture<>();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			server.shutdown();
			Stack.releaseSharedResources();
			future.complete(null);
		}));

		return future;
	}
}
