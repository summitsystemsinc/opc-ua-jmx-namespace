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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default type converters.
 * Generated from the following:
 *<%
	for(def type : types){ %>
 * Primitive: ${type[1]}	Prefix: ${type[0]}	NodeID: ${type[2]}<%
 }
 %>
 */
public class DefaultTypes {
	public static final Set<TypeNodeFactory> DEFAULT_TYPE_FACTORIES;
	public static final Map<String, TypeNodeFactory> DEFAULT_TYPE_FACTORIES_CACHE;

	static{
		DEFAULT_TYPE_FACTORIES_CACHE = new HashMap<>();
		Set<TypeNodeFactory> types = new HashSet<>();
		<% for(def type : types){ %>
			AbstractTypeNodeFactory _${type[0]}Factory = new ${type[0]}TypeFactory();
			types.add(_${type[0]}Factory);
			for(String s : _${type[0]}Factory.getSupportedTypes()){
				DEFAULT_TYPE_FACTORIES_CACHE.put(s,_${type[0]}Factory);
			}
		<% } %>
		DEFAULT_TYPE_FACTORIES = Collections.unmodifiableSet(types);
	}

	<% for(def type : types){ %>
	/**
	* <p>
	* Class for handling JMX/Java type <pre>$type[1]</pre>
	* </p>
	* <p>
	* This class marshals the JMX Attribute to
	* {@link Identifiers.$type[2]}
	* </p>
	*
	* @since 1.0.0
	* @author Justin Smith (generated)
	*/
	public static class ${type[0]}TypeFactory extends AbstractTypeNodeFactory{

		private static final Logger LOGGER = LoggerFactory.getLogger(${type[0]}TypeFactory.class);

		<% def multiple = type[1] instanceof List %>
		<%
			if(multiple){
				boolean first = true;
		%>
			public static final String[] JMX_TYPES = new String[]{
				<%
				 	for(String s : type[1]){
						out << (first ? "\"$s\"" : ",\"$s\"")
						first = false
					}
				%>
			};
		<% }else{ %>
		public static final String JMX_TYPE = "${type[1]}";
		<% } %>

		/**
		 * @return Array of strings indicating what JMX attribute types this supports.
		 */
		@Override
		public String[] getSupportedTypes() {
			<% if(multiple){ %>
				return JMX_TYPES;
			<% }else{ %>
			return new String[]{JMX_TYPE};
			<% } %>
		}

		/**
		 * @param path OPC-UA Node path.
		 * @param on JMX object name, for looking up the object.
		 * @param info JMX MBean Attribute info, to identify  which attribute to use
		 * @return A UAVariableNode, of type <pre>$type[2]</pre>
		 */
		@Override
		public UaVariableNode buildNode(String path, ObjectName on, MBeanAttributeInfo info) {
			return buildNodeWithType(path, on, info, Identifiers.${type[2]});
		}
	}
	<% } %>
}
