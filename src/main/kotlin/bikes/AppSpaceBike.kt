package bikes

import kotlin.random.Random

// Demonstration of application logic using decorator pattern
class AppSpaceBike(val bk: Bike) {
    fun ride(where: String, canRideUnicycle: Boolean) {
        println("It's a fine day to ride your bike to $where")
        if(!canRideUnicycle && bk.backWheel == null)
            throw Exception("Half the way to $where, you lose balance and fall down")

        if(Random.Default.nextBoolean()) {
            println("Suddenly a car swerves into your lane!")
            if(bk.bells.isNotEmpty())
                println("You ring your ${bk.bells.first().color} bell violently and they drive away")
            else
                throw Exception("With no way to call attention to yourself, you crash and die. You will never get to $where")
        }

        println("You arrive safely at $where")
    }

    fun removeAllBells() {
        bk.bells.forEach { bk.bells_remove(it) }
    }
}