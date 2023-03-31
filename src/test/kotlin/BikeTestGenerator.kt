import net.rec0de.inkblot.codegen.ClassConfig
import net.rec0de.inkblot.codegen.QuerySynthesizer
import net.rec0de.inkblot.codegen.SemanticObjectGenerator
import net.rec0de.inkblot.codegen.TypeMapper
import net.rec0de.inkblot.reasoning.VariablePathAnalysis
import net.rec0de.inkblot.reasoning.VariableProperties
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.jena.query.ParameterizedSparqlString
import java.io.File
import kotlin.io.path.Path
import kotlin.test.Test

internal class BikeTestGenerator {

    @Test
    fun generateExample() {
        val jsonCfg = File("src/test/resources/bike-example.json")
        val cfg: Map<String, ClassConfig> = Json.decodeFromString(jsonCfg.readText())
        cfg.forEach { (className, classConfig) -> TypeMapper.registerClassType(classConfig.type, className) }

        cfg.forEach { (className, classConfig) ->
            val variableInfo = classConfig.properties.map { (propName, propConfig) ->
                val nullable = propConfig.cardinality != "!"
                val functional = propConfig.cardinality != "*"
                val objectReference = TypeMapper.isObjectType(propConfig.type)
                val sparql = propConfig.sparql ?: propName
                val props = VariableProperties(
                    sparql,
                    propName,
                    nullable,
                    functional,
                    TypeMapper.xsdToKotlinType(propConfig.type),
                    propConfig.type,
                    objectReference
                )
                Pair(props.sparqlName, props)
            }.toMap()

            val classNamespace = classConfig.namespace ?: "http://rec0de.net/ns/inkblotTest#"
            val query = ParameterizedSparqlString(classConfig.query).asQuery()
            val paths = VariablePathAnalysis(query, classConfig.anchor)
            val synthesizer = QuerySynthesizer(classConfig.anchor, classConfig.type, variableInfo, paths, mutableMapOf())

            val backendOptions = listOf("pkg=gen")
            val generator =
                SemanticObjectGenerator(className, query, classConfig.anchor, classNamespace, variableInfo, synthesizer)
            generator.generateToFilesInPath(Path("src/test/kotlin/gen"), backendOptions)

            // 'activate' test executor to run on next test invocation
            val target = File("src/test/kotlin/ExecuteExample.kt")
            if(!target.exists())
                File("src/test/kotlin/ExecuteExample.ktx").copyTo(target)
        }
    }
}