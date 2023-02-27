package net.rec0de.inkblot.codegen

class ChangeNodeGenerator(private val synthesizer: AbstractQuerySynthesizer) {
    fun changeCN(anchorUriVar: String, sparqlVariable: String, oldValueExpr: String, newValueExpr: String): String {
        return if(synthesizer.isSimple(sparqlVariable))
                "val cn = CommonPropertyChange($anchorUriVar, \"${synthesizer.simplePathUri(sparqlVariable)}\", $oldValueExpr, $newValueExpr)"
            else {
                """
                    val template = ParameterizedSparqlString("${escape(synthesizer.changeUpdate(sparqlVariable))}")
                    template.setIri("anchor", $anchorUriVar)
                    template.setParam("o", $oldValueExpr)
                    template.setParam("n", $newValueExpr)
                    val cn = ComplexPropertyRemove(template.asUpdate())
                """.trimIndent()
        }

    }

    // ChangeNode generation code for removing values from non-functional properties (or un-setting a functional property)
    fun removeCN(anchorUriVar: String, sparqlVariable: String, valueExpr: String): String {
        return if(synthesizer.isSimple(sparqlVariable))
            "val cn = CommonPropertyRemove($anchorUriVar, \"${synthesizer.simplePathUri(sparqlVariable)}\", $valueExpr)"
        else {
            """
                val template = ParameterizedSparqlString("${escape(synthesizer.removeUpdate(sparqlVariable))}")
                template.setIri("anchor", $anchorUriVar)
                template.setParam("o", $valueExpr)
                val cn = ComplexPropertyRemove(template.asUpdate())
            """.trimIndent()
        }
    }

    // ChangeNode generation code for adding values to non-functional properties
    fun addCN(anchorUriVar: String, sparqlVariable: String, valueExpr: String): String {
        return if(synthesizer.isSimple(sparqlVariable))
            "val cn = CommonPropertyAdd($anchorUriVar, \"${synthesizer.simplePathUri(sparqlVariable)}\", $valueExpr)"
        else {
            """
                val template = ParameterizedSparqlString("${escape(synthesizer.addUpdate(sparqlVariable))}")
                template.setIri("anchor", $anchorUriVar)
                template.setParam("n", $valueExpr)
                val cn = ComplexPropertyAdd(template.asUpdate())
            """.trimIndent()
        }
    }
}