# Automated Tests

Testing this project is a bit weird, since it requires two compilation passes for changes to take effect.
We therefore use some source-copying magic to only run tests on the actual generated sources every second time the tests are run.
This avoids conflicts between outdated generated sources and the inkblot runtime.

**You should therefore always run the tests twice to get the full results.**

The tests also expect a SPARQL endpoint to be available at `http://localhost:3030/inkblottest`.

**All data in the default graph at that endpoint will be deleted when the tests run.**