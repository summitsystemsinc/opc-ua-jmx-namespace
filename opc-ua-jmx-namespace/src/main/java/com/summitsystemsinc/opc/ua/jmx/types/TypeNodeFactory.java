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
import com.digitalpetri.opcua.sdk.server.api.UaNamespace;
import com.digitalpetri.opcua.sdk.server.model.UaNode;
import com.digitalpetri.opcua.sdk.server.model.UaVariableNode;
import com.digitalpetri.opcua.stack.core.types.builtin.unsigned.UShort;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 *
 * @author Justin
 */
public interface TypeNodeFactory {

	String[] getSupportedTypes();

	UaVariableNode buildNode(String path, ObjectName on, MBeanAttributeInfo info);

	void setMBeanServerConnection(MBeanServerConnection mbsc);

	void setNamespace(UaNamespace namespace);

	void setNamespaceIndex(UShort namespaceIndex);

	void addUnavailableNodeListener(UnavailableNodeListener l);

	void removeUnavailableNodeListener(UnavailableNodeListener l);

	public static interface UnavailableNodeListener {

		void nodeUnavailable(UaNode node);

		void nodeAvailable(UaNode node);
	}
}
