# Simple Example

_I'm working through understanding what the library we're trying to generate should actually do (and how) based on this small example_

## Bike

* mfgDate: (max one) int
* frontWheel: (exactly one) Wheel
* backWheel: (max one) Wheel
* bell: (any number) Bell

Wheels and Bells are actually attached to an intermediary 'frame' node in the graph which doesn't appear in the object model

## Wheel

* diameter: (exactly one) double
* mfgDate: (max one) int
* mfgName: (any number) string

## Bell

* color: (exactly one) string