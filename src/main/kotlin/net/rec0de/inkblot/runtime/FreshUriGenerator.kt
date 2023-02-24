package net.rec0de.inkblot.runtime

import org.apache.commons.codec.binary.Base32
import kotlin.random.Random

// Customizable helper for URI generation
// generated suffixes are expected to be unique for the application namespace and the given context
// taking into account potential parallelism or simultaneous access and application restarts
// (duplicate suffixes for different contexts are acceptable)
interface FreshUriGenerator {
    fun freshSuffixFor(context: String): String
}

// With current parameters, this should provide reasonable collision resistance (p = 10^-6) for systems
// with up to 15.000 (global) object creations per second over a 14-year timeframe
object TinyUIDGen : FreshUriGenerator {
    private val encoder = Base32()
    private val rand = Random.Default
    override fun freshSuffixFor(context: String): String {
        // time to 1/10th of a second, truncated to 32bit should rollover every ~14 years
        val unixTime = (System.currentTimeMillis() / 100L).toUInt()

        val bytes = ByteArray(4)
        bytes[3] = unixTime.toByte()
        bytes[2] = (unixTime shr 8).toByte()
        bytes[1] = (unixTime shr 16).toByte()
        bytes[0] = (unixTime shr 24).toByte()

        val timeslug = encoder.encodeToString(bytes).trimEnd('=')
        val random = encoder.encodeToString(rand.nextBytes(5)).trimEnd('=')

        return ("-$timeslug-$random").lowercase()
    }
}