# Usage Example

Let's walk through a complete example from start to finish. We're still talking about bikes, but this version is a bit more complex than the minimal example in the main readme.

## Preparation

To get started, build the inkblot tool and the runtime jar using gradle:

```bash
./gradlew assemble
./gradlew runtimeJar
mv build/libs/inkblot-1.0-SNAPSHOT-all.jar inkblot.jar
mv build/libs/inkblot-runtime.jar .
```

## Queries

Gather all your queries in a file, one line per query. In this example we'll generate the classes `Bike`, `Wheel` and `Bell` from the following queries in [queries.txt](queries.txt):

```sparql
PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?bike ?mfg ?fw ?bw ?bells WHERE { ?bike a bk:bike; bk:hasFrame [bk:frontWheel ?fw] OPTIONAL { ?bike bk:hasFrame [bk:backWheel ?bw] } OPTIONAL { ?bike bk:mfgDate ?mfg } OPTIONAL { ?bike bk:hasFrame [bk:hasBell ?bells] } }
PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?wheel ?dia ?mfgD ?mfgN WHERE { ?wheel a bk:wheel; bk:diameter ?dia. OPTIONAL { ?wheel bk:mfgDate ?mfgD } OPTIONAL {?wheel bk:mfgName ?mfgN } }
PREFIX bk: <http://rec0de.net/ns/bike#> SELECT ?bell ?color WHERE { ?bell a bk:bell; bk:color ?color }
```

## Generating a configuration file

While we can always write our configuration files by hand, it's usually faster to generate a template. Note that this assumes the first SPARQL variable in each query to be the anchor.

```bash
java -jar inkblot.jar configure queries.txt config.json
```

Fill in the type and multiplicity details in the generated `config.json` file to your liking. For a completed example config, see [bike-example.json](bike-example.json). Refer to the [readme](../README.md#usage) for details on how to fill in the necessary details.

## Generating library code

With the configuration completed, we can generate the actual library code. A minimal command to assemble the library in the `gen` folder looks like this:

```bash
java -jar inkblot.jar generate config.json gen
```

To add decorators for the generated classes and be more explicit about things, we can use this more verbose command:

```bash
java -jar inkblot.jar generate --decorators --namespace "http://rec0de.net/ns/bike#" --package "gen" config.json gen
```

The second command should produce the same files you can find in the [reference folder](ref), possibly with minor changes if you made different decisions in filling in the configuration template.

## Using the generated code

Add the previously generated `inkblot-runtime.jar` as a library to your target project. Also add [Jena](https://jena.apache.org/) as a dependency, for example by adding this to your `build.gradle.kts`:

```kts
dependencies {
    implementation("org.apache.jena:jena-core:4.4.0")
    implementation("org.apache.jena:apache-jena-libs:4.4.0")
}
```

Now you can add the generated sources to your project source tree and you should be able to use them just like regular objects. Have a look at the [one of the decorators](ref/DecoratedBike.kt) for a good idea of how to interact with the generated classes.

Remember to set your SPARQL endpoint on startup and commit whenever you want to persist changes:

```kotlin
Inkblot.endpoint = "http://example.com/your/sparql/endpoint"
...
Inkblot.commit()
```