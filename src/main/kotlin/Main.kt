import bikes.AppSpaceBike
import bikes.Bike
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import inkblot.codegen.ClassConfig
import inkblot.codegen.Configuration
import inkblot.codegen.PropertyConfig
import inkblot.codegen.SemanticObjectGenerator
import inkblot.reasoning.ParameterAnalysis
import inkblot.reasoning.VariableProperties
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.jena.query.ParameterizedSparqlString
import java.io.File

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

    override fun run() {
        org.apache.jena.query.ARQ.init()

        val jsonCfg = File(config)
        val cfg: Map<String, ClassConfig> = Json.decodeFromString(jsonCfg.readText())

        val classNames = cfg.keys.toSet()

        cfg.forEach { (className, classConfig) ->
            val variableInfo = classConfig.properties.map { (propName, propConfig) ->
                val nullable = propConfig.multiplicity == "?"
                val functional = propConfig.multiplicity != "*"
                val objectReference = classNames.contains(propConfig.datatype)
                Pair(propConfig.sparql, VariableProperties(propName, nullable, functional, propConfig.datatype, objectReference))
            }.toMap()

            val query = ParameterizedSparqlString(classConfig.query).asQuery()
            val generator = SemanticObjectGenerator(className, query, classConfig.anchor, "http://rec0de.net/ns/bike#", variableInfo)
            println(generator.gen())
        }
    }
}

class Playground: CliktCommand(help="Execute playground environment") {
    override fun run() {
        org.apache.jena.query.ARQ.init()

        val bikes = Bike.loadAll()

        println("Loading bikes from data store")

        bikes.forEach { bike ->
            println("Bike: ${bike.uri}, ${bike.mfgDate}" )
            println("Front Wheel: ${bike.frontWheel.uri}, ${bike.frontWheel.diameter}, ${bike.frontWheel.mfgDate}, ${bike.frontWheel.mfgName.joinToString(", ")}")
            if(bike.backWheel != null) {
                val bw = bike.backWheel!!
                println("Back Wheel: ${bw.uri}, ${bw.diameter}, ${bw.mfgDate}, ${bw.mfgName.joinToString(", ")}")
            }
            bike.bells.forEach { bell ->
                println("Bell: ${bell.color}")
            }
            println()
        }

        //val newBike = Bike.create(2007, Wheel.loadAll().first(),null, emptyList())

        //newBike.bells_remove()
        //Bike.loadFromURI("http://rec0de.net/ns/bike#bike-4yi7b5a-e2dosvpz").delete()
        //Inkblot.commit()

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

fun main(args: Array<String>) = Inkblt().subcommands(Analyze(), Playground(), Generate()).main(args)