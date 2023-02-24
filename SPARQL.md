# Writing SPARQL for inkblot

SPARQL is a terrifyingly expressive language.

As inkblot will have to generate not just read but also write/modify queries from your SPARQL input, we intentionally limit the supported SPARQL constructs.  

Even with these limits, we acknowledge that it is still very possible to write a SPARQL query for which inkblot will generate unintuitive or straight up incorrect code. We will therefore take some time here to explain how to write SPARQL for which inkblot can generate code with high confidence and in which cases you should perhaps double-check the output.

At the end of this document we will also outline how you can customize the SPARQL queries used by inkblot to better suit your needs.

## Simple Properties

The simplest class of SPARQL queries are those that consist only of what we call **simple properties**. A SPARQL variable forms a simple property if it meets these three criteria:

1. It is the object of a relation that has the anchor as its subject, i.e. `?anchor ex:rel ?simpleProp`
2. The variable occurs in no other relationship
3. The relevant triple is located in the default graph

For these trivial properties, inkblot largely uses pre-written SPARQL queries and updates that can be expected to be correct.

## Other Benign Queries

Beyond simple properties, inkblot should, in general, be able to produce high-quality code for queries that form a tree rooted in the anchor variable. That is:

* There is exactly one path from the anchor variable to any given result variable
* Corollary: There are no cyclical relationships between variables

While leaves in this tree can be constant URIs or literals, trees without such leaves can be considered an especially simple case and should be preferred if possible.

## Caching Issues

Note that it is possible to write SPARQL queries that retrieve the same value as part of two different inkblot classes. For example, consider these two queries:

```sparql
SELECT * WHERE { ?anchor a ex:box; ex:contains ?item. ?item ex:weight ?weight }
SELECT * WHERE { ?anchor a ex:item; ex:weight ?weight }
```

If we load a box containing an item using these queries, both object instances will have a 'weight' field, initialized to the same value.
However, we have no way of knowing that these two values are aliases of each other. Updates to one field will not propagate to the other object
and the state of the backing value in the datastore after writeback is undefined.

## Writeback Semantics

Inkblot derives SPARQL updates for all properties of the generated classes from your SPARQL input query. This process involves making decisions that might not always match your intended semantics.

### Object creation

When creating a new object instance, inkblot created all triples found in the SELECT query that are not optional. Non-optional variables from the result set will be instantiated with values from the constructor, other variables will be assigned blank nodes.

Optional and non-functional properties are null / empty on object creation and have to be set explicitly.

### Setting properties

When assigning a new value to a propery, inkblot will create all triples involving the associated SPARQL variable in the SELECT query. Only triples that are not optional in the context of the SPARQL variable will be created. If newly created nodes are part of other triples in the SELECT query, these triples will be created recursively.

Consider this example query:
```sparql
SELECT ?bike ?wheel WHERE { ?bike a ex:bike; OPTIONAL { ex:hasWheel ?wheel. ?wheel ex:suitableFor ?someBike. ?someBike a ex:bike. } }
```

When we assign a Wheel object to `bike.wheel` in the generated library, inkblot will create the `?bike ex:hasWheel ?wheel` triple and also `?wheel ex:suitableFor ?someBike` and `?someBike a ex:bike` triples (`?someBike` will be a blank node). This is to ensure that the runtime objects stay consistent with the constraints set by the SELECT query.

### Unsetting properties

**Unsetting a property is not the inverse of setting it.** Unsetting a property removes all triples that form a *last edge* on a (simple) path from the anchor variable to the property variable in the SELECT query. These paths can contain edges in both subject-to-object as well as object-to-subject direction.

Consider this example query:
```sparql
SELECT ?bike ?x WHERE { ?bike a ex:bike; OPTIONAL { ex:hasComponent ?x. ?x a ex:component; ex:significance ex:high } }
```

When we set `x` to `null` using the generated library, inkblot will remove `?bike ex:hasComponent ?x`, as it is the last edge of the single-edge path `?bike-hasComponent>?x`, but leave the `?x a ex:component` and `?x ex:significance ex:high` triples in place, since they are not part of any path from `?bike` to `?x`.

### Updating properties

A property update is equivalent to unsetting and setting a property.

### Object deletion

Deleting an object removes all triples involving the anchor URI in either subject or object position. **No other triples are deleted.**

### Filters

Filters are allowed in the SELECT queries and will be used when loading objects but are ignored for writeback purposes. I.e. it is possible to create objects that will not be loaded back by the SELECT query because they do not satisfy the filter condition.

## Summary

* If something can be expressed as a simple property, do so.
* Avoid variables that can be reached using different relations
* Especially avoid introducing loops in the variable dependency graph (e.g. `?a ex:bigger ?b. ?b ex:smaller ?a`)
* Avoid using non-result variables in terminal/leaf positions
* Be aware of writeback semantics especially for constants included in the query

While inkblot can deal with all of these issues to some degree, they require significantly more complex code paths and allow for subtle deviations from expected behaviour.

## Query override / Using custom queries

If the default decisions made by inkblot do not match the semantics required by your application, we provide an override mechanism you can use to customize all non-trivial SPARQL queries/updates executed by inkblot.

Whenever inkblot generates a library, it also creates a file named `inkblot-query-override.json` in the output directory that contains all generated SPARQL queries. You can edit the queries in this file to suit your needs. Then, when generating the library again, pass `--use-queries <modified override json>` as an argument and inkblot will use your modified queries. Queries not found in the override file will be generated as usual.

When modifying queries, keep in mind that:
* `?anchor` will be bound to the anchor URI of the current object
* for creation updates, sparql variables will be bound to values from the class constructor as specified in the configuration
* for initializer queries, `?v` will be bound to the value of the initialized property
* for change/add/remove updates, `?o` will be bound to the old value of the property and `?n` will be bound to the new value (if such values exist)