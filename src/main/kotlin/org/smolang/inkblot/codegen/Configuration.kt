package org.smolang.inkblot.codegen

import kotlinx.serialization.Serializable

@Serializable
data class ClassConfig(val anchor: String, val type: String, val query: String, val namespace: String? = null, val properties: Map<String, PropertyConfig>)

@Serializable
data class PropertyConfig(val sparql: String? = null, val type: String, val cardinality: String)