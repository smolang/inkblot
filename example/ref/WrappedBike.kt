package ref


class WrappedBike(private val bike: Bike) {
    var frontWheel: Wheel
        get() = bike.frontWheel
        set(value) { bike.frontWheel = value }
    
    var backWheel: Wheel
        get() = bike.backWheel
        set(value) { bike.backWheel = value }
    
    val bells: List<Bell>
        get() = bike.bells
    fun bells_add(entry: Bell) = bike.bells_add(entry)
    fun bells_remove(entry: Bell) = bike.bells_remove(entry)
    
    var mfgYear: Int?
        get() = bike.mfgYear
        set(value) { bike.mfgYear = value }
    fun delete() = bike.delete()
    fun merge(other: WrappedBike) = bike.merge(other.bike)
}