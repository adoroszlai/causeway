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
package org.apache.causeway.client.kroviz.to

import org.apache.causeway.client.kroviz.to.bs.CollectionBs
import org.apache.causeway.client.kroviz.to.bs.PropertyBs
import org.apache.causeway.client.kroviz.to.bs.RowBs
import org.apache.causeway.client.kroviz.to.bs.XmlLayout
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.asList

/**
 * For the Wicket Viewer the following layout is used:
 * * rows[0] (head) contains the object title and actions
 * * rows[1] contains data, tabs, collections, etc.
 * * there may be N other rows as well
 * Please note, that rows may be children of Tab as well (recursive)
 */
class GridBs(document: Document) : XmlLayout(), TransferObject {
    var rows = ArrayList<RowBs>()

    init {
        val root = document.firstChild!!
        val kids = root.childNodes
        val rowNodes = kids.asList()
        val rowList = rowNodes.filter { it.nodeName == "$nsBs:row" }
        for (n: Node in rowList) {
            val row = RowBs(n)
            rows.add(row)
        }
    }

    fun getPropertyList(): List<PropertyBs> {
        val list = mutableListOf<PropertyBs>()
        rows.forEach { r ->
            list.addAll(r.getPropertyList())
        }
        return list
    }

    fun getCollectionList(): List<CollectionBs> {
        val list = mutableListOf<CollectionBs>()
        rows.forEach { r ->
            r.colList.forEach { cl ->
                list.addAll(cl.collectionList)
            }
        }
        return list
    }

}
