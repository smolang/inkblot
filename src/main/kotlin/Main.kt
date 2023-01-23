import bikes.AppSpaceBike
import bikes.Bike
import bikes.Wheel
import inkblot.Inkblot
import inkblot.reasoning.ParameterAnalysis

fun main(args: Array<String>) {
    ParameterAnalysis.main()

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

    //val newBike = AppSpaceBike(Bike.create(2007, Wheel.loadAll().first(),null, emptyList()))
    Bike.loadFromURI("http://rec0de.net/ns/bike#bike-4yi7b5a-e2dosvpz").delete()
    Inkblot.commit()

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