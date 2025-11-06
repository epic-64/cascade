# cascade
We are creating project cascade.
It is a Scala 3 project that uses Cask, Tapir and ScalaJS.
More parts of the stack may be added later.

Preferred code style:
- always prefer Scala 3 syntax over Scala 2 syntax
- use braceless syntax unless there is a good reason not to
- instead of try/catch, we use Try()/match.
- in general, we like to use match, .pipe, .tap, .map and so on