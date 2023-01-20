import bikes.AppSpaceBike
import bikes.Bike
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
    //Bike.loadFromURI("http://rec0de.net/ns/bike#bike4xufe2a-2vrkm33d").delete()
    //RuntimeSupport.commit()

    println("Unicycles:")
    val unicycles = Bike.loadSelected("!bound(?bw)")
    unicycles.forEach {
        println(it.uri)
    }

    val bikeA = AppSpaceBike(Bike.loadFromURI("http://rec0de.net/ns/bike#mountainbike"))
    val bikeB = AppSpaceBike(Bike.loadFromURI("http://rec0de.net/ns/bike#bike3"))
    //bikeB.removeAllBells()
    //RuntimeSupport.commit()

    bikeA.ride("the supermarket", false)
    println()
    bikeB.ride("your destiny", true)
    println()
    bikeB.ride("the far away mountains", false)

}