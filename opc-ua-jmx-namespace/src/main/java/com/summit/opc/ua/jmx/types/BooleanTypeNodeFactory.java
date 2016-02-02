package com.summit.opc.ua.jmx.types;

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
import com.digitalpetri.opcua.stack.core.Identifiers;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;

/**
 *
 * @author Justin
 */
public class BooleanTypeNodeFactory extends AbstractTypeNodeFactory implements TypeNodeFactory {

	public static final String BOOLEAN_TYPE = "boolean";

	@Override
	public UaVariableNode buildNode(String path, ObjectName on, MBeanAttributeInfo info) {
		final UaVariableNode retVal = buildNodeWithType(path, on, info, Identifiers.Boolean);
		return retVal;
	}

	@Override
	public String[] getSupportedTypes() {
		return new String[]{BOOLEAN_TYPE};
	}
}
