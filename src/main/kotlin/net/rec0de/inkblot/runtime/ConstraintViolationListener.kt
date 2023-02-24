package net.rec0de.inkblot.runtime

interface ConstraintViolationListener {
    fun handleViolation(violation: ConstraintViolation)
}

interface ConstraintViolation

data class VariableDomainViolation(val onObject: SemanticObject, val propertyName: String, val targetType: String) :
    ConstraintViolation

data class ValidationQueryFailure(val forClass: String, val query: String) : ConstraintViolation