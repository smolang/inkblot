# Overview of codegen classes

### AbstractQuerySynthesizer

Abstract class allowing easy migration between different QuerySynthesizer implementations. Provides override mechanism for all generated SPARQL queries and utility functions for implementing Synthesizers.

### AbstractSemanticObjectGenerator

Abstract code generator to be extended by language-specific backends. Contains some generic utility functions that can be shared across implementations.

### ChangeNodeGenerator

Using synthesized Queries, this generates target-language code creating the runtime ChangeNode objects containging SPARQL updates. An equivalent class may or may not be required for other target languages depending on the chosen RDF library.

### ConfigGenerator

Creates an empty default configuration record for a SPARQL query.

### Configuration

Data classes used to parse/dump JSON configuration files. 

### QuerySynthesizer

Contains the algorithms used to create all types of required SPARQL updates from the input query (using results from variable path analysis).

### SemanticObjectGenerator

Assembles the bulk of the generated code. Builds core and factory classes using synthesized queries and property configurations. Highly specialized to target language.

### ShaclGenerator

Formalizes assumptions made about variable cardinalities and types into SHACL shapes (using results from variable path analysis).

### TypeMapper

Maps XSD and object reference types to appropriate target language types. Used to emit well-typed code in SemanticObjectGenerator.

### ValidatingSparqlGenerator

Generates SPARQL queries that search for invalid class instances in the data store based on annotated assumptions by modifying the retrieval query. Used to perform runtime consistency checks in factory classes.

### WrapperGenerator

Generates wrapper classes in the target language that lifts the properties of the base class. 