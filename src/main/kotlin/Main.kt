import bikes.AppSpaceBike
import bikes.Bell
import bikes.Bike
import bikes.Wheel
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import inkblot.codegen.ClassConfig
import inkblot.codegen.ConfigGenerator
import inkblot.codegen.SemanticObjectGenerator
import inkblot.reasoning.ParameterAnalysis
import inkblot.reasoning.VariableProperties
import inkblot.runtime.Inkblot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.jena.query.ParameterizedSparqlString
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists

class Inkblt : CliktCommand() {
    override fun run() = Unit
}

class Analyze: CliktCommand(help="Analyze a SPARQL query") {
    private val query: String by argument(help="SPARQL select query to analyze")
    private val anchor by option(help="anchor variable of query")
    override fun run() {
        org.apache.jena.query.ARQ.init()

        val q = ParameterizedSparqlString(query)
        q.setNsPrefix("bk", "http://rec0de.net/ns/bike#")

        val queryObj = q.asQuery()
        val anchorOrDefault = if(anchor == null) queryObj.resultVars.first() else anchor!!

        ParameterAnalysis.deriveTypes(q.asQuery(), anchorOrDefault)
    }
}

class Generate: CliktCommand(help="Generate semantic object for statement") {
    private val config: String by argument(help="JSON file containing SPARQL queries and options")
    private val outPath: String by argument(help="location where generated files should be placed")
    private val pkg by option("-p", "--package", help="package identifier for generated files")
    private val namespace by option(help="default namespace to use for new entities").default("http://rec0de.net/ns/inkblot#")

    override fun run() {
        org.apache.jena.query.ARQ.init()

        val path = Path(outPath)

        if(!path.exists())
            throw Exception("Output location '$path' does not exist")

        val packageId = if(pkg != null)
                pkg!!
            // we might be able to guess the correct package name based on the output location
            else if(path.contains(Path("kotlin"))) {
                println("Generating to source directory")
                val parts = path.toList().map { it.toString() }
                val kotlinIdx = parts.lastIndexOf("kotlin")
                parts.subList(kotlinIdx+1, parts.size).joinToString(".")
            }
            else
                "gen"

        val jsonCfg = File(config)
        val cfg: Map<String, ClassConfig> = Json.decodeFromString(jsonCfg.readText())
        val classNames = cfg.keys.toSet()

        cfg.forEach { (className, classConfig) ->
            val variableInfo = classConfig.properties.map { (propName, propConfig) ->
                val nullable = propConfig.multiplicity == "?"
                val functional = propConfig.multiplicity != "*"
                val objectReference = classNames.contains(propConfig.datatype)
                Pair(propConfig.sparql ?: propName, VariableProperties(propName, nullable, functional, propConfig.datatype, objectReference))
            }.toMap()

            val classNamespace = classConfig.namespace ?: namespace
            val query = ParameterizedSparqlString(classConfig.query).asQuery()
            val generator = SemanticObjectGenerator(className, packageId, query, classConfig.anchor, classNamespace, variableInfo)

            val destination = File(path.toFile(), "$className.kt")
            destination.writeText(generator.gen())
        }
    }
}

class Configure: CliktCommand(help="Generate semantic object for statement") {
    private val output: String by argument(help="location of generated JSON configuration")
    private val queries: List<String> by argument(help="SPARQL select statements").multiple()
    private val override by option("-f", help="overwrite existing config file").flag("--force")

    override fun run() {
        val configFile = File(output)

        if(configFile.exists() && !override)
            throw Exception("Output location '${configFile.path}' already exists. Use -f to overwrite")

        org.apache.jena.query.ARQ.init()

        val cfg = queries.map { query ->
            val queryObj = ParameterizedSparqlString(query).asQuery()
            ConfigGenerator.configForClass(queryObj)
        }.toMap()

        val encoder = Json { prettyPrint = true }
        val json = encoder.encodeToString(cfg)
        configFile.writeText(json)
    }
}

class Playground: CliktCommand(help="Execute playground environment") {
    override fun run() {
        org.apache.jena.query.ARQ.init()

        val bikes = Bike.loadAll()

        println("Loading bikes from data store")

        bikes.forEach { bike ->
            println("Bike: ${bike.uri}, ${bike.mfgYear}" )
            println("Front Wheel: ${bike.frontWheel.uri}, ${bike.frontWheel.diameter}, ${bike.frontWheel.mfgYear}, ${bike.frontWheel.mfgNames.joinToString(", ")}")
            if(bike.backWheel != null) {
                val bw = bike.backWheel!!
                println("Back Wheel: ${bw.uri}, ${bw.diameter}, ${bw.mfgYear}, ${bw.mfgNames.joinToString(", ")}")
            }
            bike.bells.forEach { bell ->
                println("Bell: ${bell.color}")
            }
            println()
        }

        /*val newBike = Bike.create(Wheel.loadSelected("bound(?mfgN)").first(),null, emptyList(), 2007)
        val newBell = Bell.create("light-leak red")
        newBike.bells_add(newBell)*/

        /*val lightleakBike = Bike.loadFromURI("http://rec0de.net/ns/bike#bike-42d4bwa-r5nrsvqs")
        val lightleakBell = Bell.loadSelected("?color = \"light-leak red\"").first()
        lightleakBike.bells_add(lightleakBell)
        Inkblot.commit()*/

        println("Unicycles:")
        val unicycles = Bike.loadSelected("!bound(?bw)")
        unicycles.forEach {
            println(it.uri)
        }
        println()

        val bikeA = AppSpaceBike(Bike.loadFromURI("http://rec0de.net/ns/bike#mountainbike"))
        val bikeB = AppSpaceBike(Bike.loadFromURI("http://rec0de.net/ns/bike#bike3"))
        //bikeB.removeAllBells()
        //Inkblot.commit()

        bikeA.ride("the supermarket", false)
        println()
        bikeB.ride("your destiny", true)
        println()
        bikeB.ride("the far away mountains", false)
    }
}

fun main(args: Array<String>) = Inkblt().subcommands(Analyze(), Playground(), Generate(), Configure()).main(args)