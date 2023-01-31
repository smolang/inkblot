package inkblot.codegen

class ChangeNodeGenerator(private val synthesizer: QuerySynthesizer) {
    fun changeCN(anchorUriVar: String, v: String, oldValueExpr: String, newValueExpr: String): String {
        return if(synthesizer.isSimple(v))
                "val cn = CommonPropertyChange($anchorUriVar,  \"${synthesizer.simplePathUri(v)}\", $oldValueExpr, $newValueExpr)"
            else {
                """
                    val template = ParameterizedSparqlString("${synthesizer.changeUpdate(v)}")
                    template.setIri("anchor", $anchorUriVar)
                    template.setParam("o", $newValueExpr)
                    template.setParam("n", $newValueExpr)
                    val cn = ComplexPropertyRemove(template.asUpdate())
                """.trimIndent()
        }

    }

    // ChangeNode generation code for removing values from non-functional properties (or un-setting a functional property)
    fun removeCN(anchorUriVar: String, v: String, valueExpr: String): String {
        return if(synthesizer.isSimple(v))
            "val cn = CommonPropertyRemove($anchorUriVar, \"${synthesizer.simplePathUri(v)}\", $valueExpr)"
        else {
            """
                val template = ParameterizedSparqlString("${synthesizer.removeUpdate(v)}")
                template.setIri("anchor", $anchorUriVar)
                template.setParam("o", $valueExpr)
                val cn = ComplexPropertyRemove(template.asUpdate())
            """.trimIndent()
        }
    }

    // ChangeNode generation code for adding values to non-functional properties
    fun addCN(anchorUriVar: String, v: String, valueExpr: String): String {
        return if(synthesizer.isSimple(v))
            "val cn = CommonPropertyAdd($anchorUriVar, \"${synthesizer.simplePathUri(v)}\", $valueExpr)"
        else {
            """
                val template = ParameterizedSparqlString("${synthesizer.addUpdate(v)}")
                template.setIri("anchor", $anchorUriVar)
                template.setParam("o", $valueExpr)
                val cn = ComplexPropertyAdd(template.asUpdate())
            """.trimIndent()
        }
    }
}