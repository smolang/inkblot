import gen.Bell
import gen.Bike
import gen.Wheel
import net.rec0de.inkblot.runtime.Inkblot
import org.apache.jena.sparql.exec.http.UpdateExecHTTPBuilder
import org.apache.jena.update.UpdateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.forEachDirectoryEntry

internal class ExecuteExample {

    @Test
    fun run() {
        Inkblot.endpoint = "http://localhost:3030/inkblottest"
        clearDataStore()
        createBasicEntities()

        val wheels = Wheel.commitAndLoadAll()
        assertEquals(5, wheels.size, "should have exactly five wheels in datastore")

        val purpleBell = Bell.commitAndLoadSelected("?color = 'purple'")
        assertEquals(1, purpleBell.size, "should have exactly one purple bell")

        val (aUri, bUri) = createTestBikes(wheels, purpleBell)
        Inkblot.commit()

        // force garbage collect and wait a bit for cached objects to be cleared
        System.gc()
        Thread.sleep(5_000)

        testModifyBike(aUri, wheels)

        // force garbage collect and wait a bit for cached objects to be cleared
        System.gc()
        Thread.sleep(5_000)

        Inkblot.commit()
        val bike = Bike.loadFromURI(aUri)
        assertEquals(2023, bike.mfgYear)
        assertEquals("primary wheel", bike.frontWheel.mfgNames.first())
        assertEquals(3000.0, bike.frontWheel.diameter)
    }

    private fun testModifyBike(uri: String, wheels: List<Wheel>) {
        val bike = Bike.loadFromURI(uri)
        assertEquals(1, bike.bells.size)
        assertEquals("purple", bike.bells.first().color)
        assertEquals(wheels[0], bike.frontWheel)

        bike.frontWheel.diameter = 3000.0
        bike.mfgYear = 2023
        bike.frontWheel.mfgNames_add("primary wheel")
    }

    private fun createTestBikes(wheels: List<Wheel>, bells: List<Bell>): Pair<String, String> {
        val bikeA = Bike.create(wheels[0], wheels[1], bells, null)
        val bikeB = Bike.create(wheels[2], wheels[3], bells, null)

        val bikeAuri = bikeA.uri
        val bikeBuri = bikeB.uri

        assertEquals(bikeA.bells.first(), bikeB.bells.first())
        assertNotEquals(bikeA.frontWheel, bikeB.frontWheel)

        return Pair(bikeAuri, bikeBuri)
    }

    private fun createBasicEntities() {
        val bellColors = listOf("purple", "white", "grey", "black")
        bellColors.forEach { Bell.create(it) }

        for (i in (1..5))
            Wheel.create(i*1.5, 2000+i, listOf())
        Inkblot.commit()
    }

    private fun clearDataStore() {
        val builder = UpdateExecHTTPBuilder.create()
        builder.endpoint(Inkblot.endpoint)
        builder.update(UpdateFactory.create("DELETE {?s ?p ?o} WHERE {?s ?p ?o}"))


        builder.execute()
    }
}