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

#set( $types = [
	["String","java.lang.String","String"],
	["Integer","int","Int32"],
	["Long","long","Int64"],
	["Double","double","Double"],
	["Boolean","boolean","Boolean"]
])

import com.digitalpetri.opcua.sdk.server.model.UaVariableNode;
import com.digitalpetri.opcua.stack.core.Identifiers;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultTypes {
	public static final Set<TypeNodeFactory> DEFAULT_TYPE_FACTORIES;
	static{
		Set<TypeNodeFactory> types = new HashSet<>();
		#foreach ($type in $types)
		types.add(new $type[0]TypeFactory());
		#end
		DEFAULT_TYPE_FACTORIES = Collections.unmodifiableSet(types);
	}
	
	#foreach ($type in $types)
	public static class $type[0]TypeFactory extends AbstractTypeNodeFactory{

		public static final String JMX_TYPE = "$type[1]";

		@Override
		public String[] getSupportedTypes() {
			return new String[]{JMX_TYPE};
		}

		@Override
		public UaVariableNode buildNode(String path, ObjectName on, MBeanAttributeInfo info) {
			return buildNodeWithType(path, on, info, Identifiers.$type[2]);
		}
	}
	#end
}
