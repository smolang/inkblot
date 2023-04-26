import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.smolang.inkblot.reasoning.VariablePathAnalysis
import org.smolang.inkblot.reasoning.VariableProperties
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.jena.query.ParameterizedSparqlString
import org.smolang.inkblot.codegen.*
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists

class Inkblt : CliktCommand() {
    override fun run() = Unit
}

class Generate: CliktCommand(help="Generate library classes from a configuration file") {
    private val config: String by argument(help="JSON file containing SPARQL queries and options")
    private val outPath: String by argument(help="location where generated files should be placed")
    private val queryOverrideFile: String? by option("--use-queries", help="use SPARQL queries from an inkblot-override JSON file instead of generated queries")
    private val backend by option("--backend", help="code generation backend to use - defaults to kotlin and only supports kotlin").default("kotlin")
    private val options by option("--options", help="backend-specific options").default("")
    private val pkgOpt by option("-p", "--package", help="package identifier to use for generated files")
    private val genWrappers by option("-w", "--wrappers", help="generate empty wrappers for classes").flag()
    private val namespace by option(help="default namespace to use for new entities").default("http://rec0de.net/ns/inkblot#")

    override fun run() {
        org.apache.jena.query.ARQ.init()

        val path = Path(outPath)

        if(!path.exists())
            throw Exception("Output location '$path' does not exist")

        val backendOptions = options.split(",").toMutableList()

        // lifting backend options into neat top-level options with help/documentation
        // because are we ever _really_ adding another backend?
        if(genWrappers)
            backendOptions.add("wrappers")
        if(pkgOpt != null)
            backendOptions.add("pkg=$pkgOpt")

        val queryMaps: MutableMap<String, Map<String, String>> = if(queryOverrideFile == null)
            mutableMapOf()
        else
            Json.decodeFromString(File(queryOverrideFile!!).readText())

        val jsonCfg = File(config)
        val cfg: Map<String, ClassConfig> = Json.decodeFromString(jsonCfg.readText())
        cfg.forEach { (className, classConfig) -> TypeMapper.registerClassType(classConfig.type, className) }

        cfg.forEach { (className, classConfig) ->
            val variableInfo = classConfig.properties.map { (propName, propConfig) ->
                val nullable = propConfig.cardinality != "!"
                val functional = propConfig.cardinality != "*"
                val objectReference = TypeMapper.isObjectType(propConfig.type)
                val sparql = propConfig.sparql ?: propName
                val props = VariableProperties(sparql, propName, nullable, functional, TypeMapper.xsdToKotlinType(propConfig.type), propConfig.type, objectReference)
                Pair(props.sparqlName, props)
            }.toMap()

            val classNamespace = classConfig.namespace ?: namespace
            val query = ParameterizedSparqlString(classConfig.query).asQuery()
            val paths = VariablePathAnalysis(query, classConfig.anchor)
            // use override query lists with synthesizer if we have override info for that class, otherwise use empty mapping
            val synthesizer = QuerySynthesizer(classConfig.anchor, classConfig.type, variableInfo, paths, queryMaps.getOrDefault(className, emptyMap()).toMutableMap())

            when(backend) {
                "kotlin" -> {
                    val generator = SemanticObjectGenerator(
                        className, query, classConfig.anchor, classNamespace, variableInfo, synthesizer
                    )
                    generator.generateToFilesInPath(path, backendOptions)
                }
                else -> throw Exception("Unknown code generation backend '$backend'")
            }

            // SHACL constraints
            ShaclGenerator.generateToFilesInPath(path, classConfig.type, className, variableInfo.values, paths)

            // Collect generated SPARQL to create override file
            queryMaps[className] = synthesizer.effectiveQueries
        }

        val encoder = Json { prettyPrint = true }
        val json = encoder.encodeToString(queryMaps)
        val queryOverrideFile = File(path.toFile(), "inkblot-query-override.json")
        queryOverrideFile.writeText(json)
        println("Generated file 'inkblot-query-override.json'")
    }
}

class Configure: CliktCommand(help="Generate a placeholder configuration file from a list of SPARQL queries") {
    private val queryList: String by argument(help="file containing SPARQL select statements (one per line)")
    private val output: String by argument(help="location of generated JSON configuration")
    private val override by option("-f", help="overwrite existing config file").flag("--force")
    private val forbiddenMagic by option(help="use dark magic to guess sensible configuration values").flag()
    private val endpoint by option(help="endpoint to use for dark magic analysis")

    override fun run() {
        val queryFile = File(queryList)
        if(!queryFile.exists())
            throw Exception("Query input file '${queryFile.path}' does not exist")
        val queries = queryFile.readLines()

        val configFile = File(output)
        if(configFile.exists() && !override)
            throw Exception("Output location '${configFile.path}' already exists. Use -f to overwrite")

        org.apache.jena.query.ARQ.init()

        val cfg = queries.associate { query ->
            val queryObj = ParameterizedSparqlString(query).asQuery()
            ConfigGenerator.configForClass(queryObj, forbiddenMagic, endpoint)
        }

        val encoder = Json { prettyPrint = true }
        val json = encoder.encodeToString(cfg)
        configFile.writeText(json)
    }
}

fun main(args: Array<String>) = Inkblt().subcommands(Generate(), Configure()).main(args)
