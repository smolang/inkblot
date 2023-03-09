package ref


class DecoratedWheel(private val wheel: Wheel) {
    var diameter: Double
        get() = wheel.diameter
        set(value) { wheel.diameter = value }
    
    var mfgYear: Int?
        get() = wheel.mfgYear
        set(value) { wheel.mfgYear = value }
    
    val mfgNames: List<String>
        get() = wheel.mfgNames
    fun mfgNames_add(entry: String) = wheel.mfgNames_add(entry)
    fun mfgNames_remove(entry: String) = wheel.mfgNames_remove(entry)
    fun delete() = wheel.delete()
    fun merge(other: DecoratedWheel) = wheel.merge(other.wheel)
}