package inkblot.codegen

import kotlinx.serialization.Serializable

@Serializable
data class Configuration(val classes: Map<String, ClassConfig>)

@Serializable
data class ClassConfig(val anchor: String, val query: String, val properties: Map<String, PropertyConfig>)

@Serializable
data class PropertyConfig(val sparql: String, val datatype: String, val multiplicity: String)