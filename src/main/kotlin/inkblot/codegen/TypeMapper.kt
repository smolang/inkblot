package inkblot.codegen

object TypeMapper {

    private val objectTypes = mutableMapOf<String, String>()

    fun registerClassType(rdfType: String, className: String) {
        objectTypes[rdfType] = className
    }

    fun isObjectType(rdfType: String) = objectTypes.containsKey(rdfType)

    fun xsdToKotlinType(xsd: String): String {
        return when(val short = xsd.removePrefix("xsd:").removePrefix("http://www.w3.org/2001/XMLSchema#")) {
            "string" -> "String"
            "boolean" -> "Boolean"
            "long" -> "Long"
            "int" -> "Int"
            "short" -> "Short"
            "byte" -> "Byte"
            "float" -> "Float"
            "double" -> "Double"
            "unsignedLong" -> "ULong"
            "unsignedInt" -> "UInt"
            "unsignedShort" -> "UShort"
            "unsignedByte" -> "UByte"
            else -> {
                if(objectTypes.containsKey(xsd))
                    objectTypes[xsd]!!
                else {
                    println("WARNING: Rendering unsupported xsd type '$short' as String")
                    "String"
                }
            }
        }
    }

    fun literalToType(litExpr: String, type: String, nullable: Boolean = false): String {
        return when(type) {
            "Boolean" -> "$litExpr<?>boolean"
            "Long" -> "$litExpr<?>long"
            "Int" -> "$litExpr<?>int"
            "Short" -> "$litExpr<?>short"
            "Byte" -> "$litExpr<?>byte"
            "Float" -> "$litExpr<?>float"
            "Double" -> "$litExpr<?>double"
            // For unsigned types, we parse them as a larger type then convert to unsigned since direct unsigned access is unsupported
            "ULong" -> "$litExpr<?>string<?>toULong()"
            "UInt" -> "$litExpr<?>long<?>toUInt()"
            "UShort" -> "$litExpr<?>int<?>toUShort()"
            "UByte" -> "$litExpr<?>short<?>toUByte()"
            else -> "$litExpr<?>string"
        }.replace("<?>", if(nullable) "?." else ".")
    }
}