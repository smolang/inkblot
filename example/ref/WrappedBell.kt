package ref


class WrappedBell(private val bell: Bell) {
    var color: String
        get() = bell.color
        set(value) { bell.color = value }
    fun delete() = bell.delete()
    fun merge(other: WrappedBell) = bell.merge(other.bell)
}