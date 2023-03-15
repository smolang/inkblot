# inkblot

Inkblot generates object-oriented libraries for semantic data access.

## Setup

Run `./gradlew shadowJar` to assemble an executable jar containing the inkblot library generation tool. To obtain a jar containing the inkblot runtime, run `./gradlew runtimeJar`. Generated files are located in `build/libs/`. For information on automated tests, see [Testing.md](src/test/Testing.md).

To use generated libraries, add the generated files to your project source tree and add the inkblot runtime jar as a dependency. Inkblot also requires [Jena](https://jena.apache.org/) to run.

## Usage

The main inputs for inkblot will be the SPARQL queries that you would use to load the instances you are interested in. Many advanced SPARQL features are not supported, but any sufficiently simple query should work. For guidelines on how to write SPARQL for inkblot, see [SPARQL.md](SPARQL.md).

For a full usage example including all example files and outputs, see [Example.md](example/Example.md).

Write one query for each class you'd like to have in the generated library. For example:

```sparql
PREFIX ex: <http://e.x/> SELECT ?bike ?frontWheel ?backWheel ?color WHERE { ?bike a ex:bike; ex:hasWheel ?frontWheel, ?backWheel. ?frontWheel ex:inFrontOf ?backWheel }
PREFIX ex: <http://e.x/> SELECT ?wheel ?diameter WHERE { ?wheel a ex:wheel; ex:diameter ?diameter }
```

From these queries we can generate a configuration template:

```bash
java -jar inkblot.jar configure queries.txt bikes.json
```

```
Usage: inkblt configure [OPTIONS] QUERYLIST OUTPUT

Generate a placeholder configuration file from a list of SPARQL queries

Options:
  -f / --force       overwrite existing config file
  -h, --help         Show this message and exit

Arguments:
  QUERYLIST  file containing SPARQL select statements (one per line)
  OUTPUT     location of generated JSON configuration
```

This will create the file `bikes.json` that looks something like this:

```json
{
    "Bike": {
        "anchor": "bike",
        "type": "http://example.com/ns/class",
        "query": "PREFIX ex: <http://e.x/> SELECT ?bike ?frontWheel ?backWheel ?color WHERE { ?bike a ex:bike; ex:hasWheel ?frontWheel; ex:hasWheel ?backWheel. ?frontWheel ex:inFrontOf ?backWheel }",
        "properties": {
            "frontWheel": {
                "sparql": "frontWheel",
                "type": "http://example.com/ns/any",
                "multiplicity": "*"
            },
            "backWheel": {
                "sparql": "backWheel",
                "type": "http://example.com/ns/any",
                "multiplicity": "*"
            },
            "color": {
                "sparql": "color",
                "type": "http://example.com/ns/any",
                "multiplicity": "*"
            }
        }
    },
    "Wheel": {
        "anchor": "wheel",
        "type": "http://example.com/ns/class",
        "query": "PREFIX ex: <http://e.x/> SELECT ?wheel ?diameter WHERE { ?wheel a ex:wheel; ex:diameter ?diameter }",
        "properties": {
            "diameter": {
                "sparql": "diameter",
                "type": "http://example.com/ns/any",
                "multiplicity": "*"
            }
        }
    }
}
```

We can now fill in some additional details:

* Change property names (while keeping the 'sparql' key the same). This will change the property names in the generated code.
* Similarly, change class names (while keeping the 'anchor' key the same).
* Add type information to classes. This is necessary for inkblot to recognize object references. For example, change the type of `Bike` to `http://e.x/bike` in this example.
* Add type information to properties. For references, use the type annotated in the corresponding class. For data types, use xsd types, either as URI or with the `xsd:` prefix. For references that you want to treat as simple strings in the generated code, use `inkblot:rawObjectReference`.
* Change multiplicity information. By default, inkblot expects that a property can have arbitrarily many values, which will be rendered as a list. To assert that a property can only have one value (i.e. is functional), set multiplicity to `!` for exactly one or `?` for zero or one.

With all that filled in, we are ready to generate our library:

```bash
java -jar inkblot.jar generate --wrappers bikes.json /path/to/sources
```

```
Usage: inkblot generate [OPTIONS] CONFIG OUTPATH

Generate library classes from a configuration file

Options:
  --use-queries TEXT  use SPARQL queries from an inkblot-override JSON file
                      instead of generated queries
  -p, --package TEXT  package identifier to use for generated files
  -w, --wrappers      generate empty wrappers for classes
  --namespace TEXT    default namespace to use for new entities
  -h, --help          show this message and exit

Arguments:
  CONFIG   JSON file containing SPARQL queries and options
  OUTPATH  location where generated files should be placed
```

This will generate kotlin classes and factories alongside some SHACL constraints formalizing multiplicity and type assumptions we made in the configuration.

Note that due to the expressiveness of SPARQL, the SHACL constraints may be overly strict and failure to validate them does not necessarily indicate a flaw in your data or query.

The generated code can be used something like this:

```kotlin
val bikes = BikeFactory.commitAndLoadAll()
val bike = bikes.first()

println(bike.color ?: "no color")
println(bike.frontWheel.diameter)

val tmp = bike.frontWheel
bike.frontWheel = bike.backWheel
bike.backWheel = tmp

Inkblot.commit()
```

### Advanced configuration

In the json configuration, you can add a `namespace` entry for a class that will specify the namespace prefix in which new instances of this class will be created. If omitted, it defaults to a global namespace specified using the `--namespace` option, defaulting to `http://rec0de.net/ns/inkblot#` if no such option is given.

If the name of the property (as it should be rendered in generated code) is the same as the name of the sparql variable, the `sparql` key may be omitted.

## Library usage

### Committing changes

All changes made to objects remain local and in-memory until they are committed. To commit accumulated changes, call `Inkblot.commit()`. To avoid cache-consistency issues, the `commitAndLoadAll` and `commitAndLoadSelected` methods also commit outstanding changes before loading any objects (much like the name implies).

### Factories

For each class defined in the json configuration, inkblot will generate a `<classname>Factory` that can be used to create and load objects. It offers the following methods:

* `ExFactory.create([parameters])` creates a new instance of the 
* `ExFactory.loadFromURI(uri: String)` loads a single object given its anchor URI
* `ExFactory.commitAndLoadAll()` returns a list of all class instances in the datastore
* `ExFactory.commitAndLoadSelected(filter: String)` returns a list of all class instances matching the given SPARQL filter expression (using variable names as in the input SPARQL query)
* `ExFactory.validateOnlineData()` runs validating SPARQL queries against the endpoint to check if the datastore is consistent with the assumptions made in the configuration. Causes a `ValidationQueryFailure` constraint violation on failure.
* `ExFactory.addValidatingQuery(query: Query)` add an additional custom validating query. This query will be executed every time `validateOnlineData` is called and is expected to return an empty result. If the result is not empty, an exception is thrown. See [Validation](Validation.md) for more info.

### Classes

For convenience, generated classes include a companion object giving access to the associated factory methods, meaning you can write `Ex.create(...)` instead of `ExFactory.create(...)`.

#### Functional, non-null properties

Properties defined with `multiplicity: "!"` are rendered as, and can be used like, simple object properties:

```kotlin
bike.distanceDriven = 0
bike.distanceDriven += 33
bike.frontWheel = Wheel.create(...)
etc
```

#### Functional, nullable properties

Properties defined with `multiplicity: "?"` work much the same way, the only difference being that they can be null and, therefore, you can assign null values to them.

```kotlin
bike.lamp = Lamp.create(...)
bike.lamp = null
val x = bike.lamp ?: defaultLamp
etc
```

#### Non-functional properties

Properties defined with `multiplicity: "*"` are rendered as lists. Inkblot provides read-only access to a kotlin list containing all values using a property of the name defined in the json config file. Elements may be added or removed using the `<name>_add(element)` and `<name>_remove(element)` class methods. Note that despite being accessed as a list, these collections of values have set semantics, following from RDF semantics.

```kotlin
val ornamentColors = bike.ornaments.map{ it.color }
bike.ornaments_remove(someOrnament)
etc
```

#### Merge & Delete

Objects can be marked for deletion using the `delete()` class method. Objects marked as deleted can still be used for read-access to their properties, but any attempt to write to them will throw an exception.

It is also possible to merge two objects of the same class together using the `merge(other)` class method. This will copy all properties from the `other` object into the invoking object, overwriting existing values for functional properties. If a nullable property in the `other` object is null, any existing value in the invoking object will not be overwritten.

Finally, the `other` object is deleted and **all references to its anchor URI are replaced with the URI of the invoking object**.

## Runtime details

### Setting a SPARQL endpoint

Before using any inkblot functions, you should set the SPARQL endpoint of your data store. You can do this at runtime (`Inkblot.endpoint = "http://..."`) or by hardcoding a custom value in `Inkblot.kt`.

### Object creation

For newly created objects, inkblot will generate a fresh URI in the namespace configured for that class or the default namespace of the project, defaulting to `http://rec0de.net/ns/inkblot#` if no namespace is given. To ensure unique identifiers across restarts and/or multiple instances, inkblot generates short UUID-like identifiers consisting of a timestamp and some random bytes. For example, an object of class bike created in the default namespace might receive the URI `http://rec0de.net/ns/inkblot#bike-42d4bwa-r5nrsvqs`.

These unique identifiers are designed to provide a collision probability of ≤10^-6 over a lifetime of 14 years while supporting 15.000 global object creations of a given class per second (technically 1.500 per 100ms).

If these parameters do not match your needs, it is possible to change the ID generator of the inkblot runtime to a custom implementation of the `FreshUriGenerator` interface.

### Constraint violations

The inkblot runtime offers some support for detecting data constraint violations at runtime. For now, this is only supported for failing [validation queries](Validation.md) and the (non-) positive/negative integer xsd types, which are mapped to java BigInteger objects that do not have the same range constraints. When values conflicting with the xsd data type range are assigned to a property at runtime, inkblot registers a constraint violation and notifies all `ConstraintViolationListeners`. Listeners may be added or removed using `Inkblot.addViolationListener(listener: ConstraintViolationListener)` and `Inkblot.removeViolationListener(listener: ConstraintViolationListener)`.

You may also extend the amount of runtime checks by implementing custom subclasses of `ConstraintViolation` and call `Inkblot.violation(violation: ConstraintViolation)` to notify listeners.