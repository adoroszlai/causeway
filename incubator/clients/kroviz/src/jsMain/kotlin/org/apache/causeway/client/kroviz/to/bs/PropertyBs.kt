/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.causeway.client.kroviz.to.bs

import org.apache.causeway.client.kroviz.to.Link
import org.w3c.dom.Node
import org.w3c.dom.asList

//IMPROVE class differs in many aspects from org.ro.to.Property - to be refactored?
class PropertyBs(node: Node) : XmlLayout() {
    var id: String
    var named = ""
    var link: Link? = null
    var hidden: String = "" // USE ENUM Where? = null
    var typicalLength: Int = 0
    var multiLine: Int = 1
    var describedAs: String? = null
    lateinit var action: ActionBs

    init {
        // TODO improve casting, in PropertyDetails some extra check have to be performed
        val dn = node.asDynamic()
        hidden = dn.getAttribute("hidden").unsafeCast<String>()
        id = dn.getAttribute("id").unsafeCast<String>()
        typicalLength = dn.getAttribute("typicalLength").unsafeCast<Int>()
        multiLine = dn.getAttribute("multiLine").unsafeCast<Int>()
        describedAs = dn.getAttribute("describedAs").unsafeCast<String>()

        val nodeList = node.childNodes.asList()
        val namedList = nodeList.filter { it.nodeName == "$nsCpt:named" }
        if (namedList.isNotEmpty()) {
            val n = namedList.first()
            named = n.textContent as String
        }

        val actList = nodeList.filter { it.nodeName == "$nsCpt:action" }
        if (actList.isNotEmpty()) {
            val n = actList.first()
            action = ActionBs(n)
        }

        val linkList = nodeList.filter { it.nodeName == "$nsCpt:link" }
        if (linkList.isNotEmpty()) {
            val n = linkList.first()
            val bs3l = LinkBs(n)
            link = Link(bs3l.rel, bs3l.method, bs3l.href, bs3l.type)
        }
    }

}
