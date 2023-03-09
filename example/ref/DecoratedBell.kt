package ref


class DecoratedBell(private val bell: Bell) {
    var color: String
        get() = bell.color
        set(value) { bell.color = value }
    fun delete() = bell.delete()
    fun merge(other: DecoratedBell) = bell.merge(other.bell)
}