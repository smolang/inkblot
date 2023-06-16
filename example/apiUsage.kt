package example

import org.smolang.inkblot.runtime.Inkblot

fun main() {
    Inkblot.endpoint = "http://localhost:3030/inkblot" // your SPARQL endpoint here

    println("Loading bike inventory")
    val bikes = BikeFactory.commitAndLoadAll()
    bikes.forEach { bk ->
        println("${bk.uri}: mfg ${bk.mfgYear}")
        println("-> Number of bells: ${bk.bells.size}")
        if(bk.bells.isNotEmpty())
            println("-> Bell colors: ${bk.bells.joinToString(", ") { bell -> bell.color }}")
        if(bk.frontWheel.diameter != bk.backWheel.diameter)
            println("-> Warning: Mismatched wheel diameters (${bk.frontWheel.diameter} vs ${bk.backWheel.diameter})")
    }

    println("Creating a bike")
    val newFrontWheel = WheelFactory.create(20.0, 2023, listOf("AluWheel"))
    val newBackWheel = WheelFactory.create(22.0, 2021, emptyList())
    val newBell = BellFactory.create("blood red")
    BikeFactory.create(newFrontWheel, newBackWheel, listOf(newBell), 2023)

    Inkblot.commit()
}