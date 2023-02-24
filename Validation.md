# Validating Existing Data

Like any library, the code generated by inkblot makes some assumptions about the data it is interacting with. Inkblot provides several mechanisms to help ensure these assumptions are valid and the data the library is operating on is consistent.

## SHACL

Alongside every generated class, inkblot creates a SHACL file expressing type and multiplicity assumptions made about instances of this class. These SHACL constraints are not validated automatically but can be checked if desired using any off-the-shelf SHACL validator. They generally provide more detailed feedback than runtime validation checks.

However, in some cases the input SPARQL queries are too complex for inkblot to express data constraints precisely in SHACL. In these cases, the generated SHACL will be *more restrictive* than required, and inkblot will show warnings saying so. A validation failure therefore does not guarantee a real issue with the data, but can still be seen as a good indication that something may be wrong.

Also note that all generated SHACL assumes that *everything of the RDF type annotated in the class configuration is actually an instance of that class* (i.e. the generated NodeShape will have a `sh:targetClass <class type uri>` even if there are nodes of that type that are not selected by the input SPARQL query).

## Validating SPARQL queries

Inkblot also generates additional SPARQL queries to validate data consistency. These queries run automatically when an object factory is initialized (that is to say before any object of the class is loaded or created) and cause an exception on failure.

You may also invoke these consistency checks at runtime by calling `<obj>Factory.validateOnlineData()`. If invoked this way, a validation failure will not cause an exception but instead a `ConstraintViolation` event that is dispatched to registered `ConstraintViolationListeners`.

In addition, custom validation queries can be added using `<obj>Factory.addValidatingQuery(query: Query)`. These will be executed whenever `validateOnlineData` is called and cause a violation when the query does not return an empty result.

Be aware that these checks are always executed against the remote SPARQL endpoint and thus are unaware of any uncommitted changes.