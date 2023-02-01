package inkblot.codegen

import kotlinx.serialization.Serializable

@Serializable
data class ClassConfig(val anchor: String, val query: String, val properties: Map<String, PropertyConfig>)

@Serializable
data class PropertyConfig(val sparql: String? = null, val datatype: String, val multiplicity: String)