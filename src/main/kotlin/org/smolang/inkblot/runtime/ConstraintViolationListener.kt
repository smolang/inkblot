package org.smolang.inkblot.runtime

// Listener interface to be used with addConstraintListener/removeConstraintListener methods on the runtime object
interface ConstraintViolationListener {
    fun handleViolation(violation: ConstraintViolation)
}

// Top class of all violations handled by the runtime
interface ConstraintViolation

// Violation created when invalid values are assigned to a property (e.g. +5 to an XSD NegativeInteger property)
data class VariableDomainViolation(val onObject: SemanticObject, val propertyName: String, val targetType: String) :
    ConstraintViolation

// Violation created when a user-requested validation of on-line data fails. Runtime-enforced checks throw exceptions instead.
data class ValidationQueryFailure(val forClass: String, val query: String) : ConstraintViolation