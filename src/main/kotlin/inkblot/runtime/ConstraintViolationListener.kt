package inkblot.runtime

interface ConstraintViolationListener {
    fun handleViolation(violation: ConstraintViolation)
}

interface ConstraintViolation

data class VariableDomainViolation(val onObject: SemanticObject, val propertyName: String, val targetType: String) : ConstraintViolation