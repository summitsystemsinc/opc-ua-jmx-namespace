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
import com.digitalpetri.opcua.sdk.server.model.UaVariableNode;
import java.util.Objects;
import javax.management.ObjectName;

/**
 *
 * @author Justin
 */
public class JmxPolledAttribute {

	private final ObjectName objectName;
	private final String attributeName;
	private final UaVariableNode node;

	public JmxPolledAttribute(ObjectName on, String attributeName, UaVariableNode node) {
		this.objectName = on;
		this.attributeName = attributeName;
		this.node = node;
	}

	/**
	 * @return the objectName
	 */
	public ObjectName getObjectName() {
		return objectName;
	}

	/**
	 * @return the attributeName
	 */
	public String getAttributeName() {
		return attributeName;
	}

	/**
	 * @return the node
	 */
	public UaVariableNode getNode() {
		return node;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 29 * hash + Objects.hashCode(this.objectName);
		hash = 29 * hash + Objects.hashCode(this.attributeName);
		hash = 29 * hash + Objects.hashCode(this.node);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final JmxPolledAttribute other = (JmxPolledAttribute) obj;
		if (!Objects.equals(this.attributeName, other.attributeName)) {
			return false;
		}
		if (!Objects.equals(this.objectName, other.objectName)) {
			return false;
		}
		return Objects.equals(this.node, other.node);
	}

}
