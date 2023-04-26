package org.smolang.inkblot.codegen

object TypeMapper {

    private val classNames = mutableSetOf<String>()
    private val classNameToTypeURI = mutableMapOf<String, String>()

    fun registerClassType(rdfType: String, className: String) {
        classNames.add(className)
        classNameToTypeURI[className] = rdfType
    }

    fun isObjectType(className: String) = classNames.contains(className)

    fun typeUriFor(className: String) = classNameToTypeURI[className]

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
            "integer" -> "BigInteger"
            "rational" -> "BigDecimal"
            "nonPositiveInteger" -> "BigInteger"
            "negativeInteger" -> "BigInteger"
            "nonNegativeInteger" -> "BigInteger"
            "positiveInteger" -> "BigInteger"
            "inkblot:rawObjectReference" -> "String"
            else -> {
                if(classNames.contains(xsd))
                    xsd
                // a type we do not fully support but know to be an xsd type and can provide string access to
                else if(short != xsd) {
                    println("WARNING: Rendering unsupported literal type '$short' as String")
                    "String"
                }
                else
                    throw Exception("Unsupported non-XSD type: '$xsd'")
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
            "BigInteger" -> "BigInteger($litExpr.string)"
            "BigDecimal" -> "BigDecimal($litExpr.string)"
            else -> "$litExpr<?>string"
        }.replace("<?>", if(nullable) "?." else ".")
    }

    fun valueToLiteral(valueExpr: String, xsd: String): String {
        return when(xsd.removePrefix("xsd:").removePrefix("http://www.w3.org/2001/XMLSchema#")) {
            "string", "boolean", "long", "int", "short", "byte", "float", "double", "integer", "rational" -> "ResourceFactory.createTypedLiteral($valueExpr)"
            "unsignedLong" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDunsignedLong)"
            "unsignedInt" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDunsignedInt)"
            "unsignedShort" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDunsignedShort)"
            "unsignedByte" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDunsignedByte)"
            "nonPositiveInteger" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDnonPositiveInteger)"
            "negativeInteger" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDnegativeInteger)"
            "nonNegativeInteger" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDnonNegativeInteger)"
            "positiveInteger" -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSDpositiveInteger)"
            "inkblot:rawObjectReference" -> "ResourceFactory.createResource($valueExpr)"
            else -> "ResourceFactory.createTypedLiteral($valueExpr.toString(), XSDDatatype.XSD$xsd)"
        }
    }
}