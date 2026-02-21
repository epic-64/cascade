# cascade
We are creating project cascade.
It is a Scala 3 project that uses Cask and ScalaJS.
More parts of the stack may be added later.

Preferred code style:
- always prefer Scala 3 syntax over Scala 2 syntax
- never use _ as a wildcard import, always use *
- use braceless syntax unless there is a good reason not to
- instead of try/catch, we use Try()/match.
- in general, we like to use match, .pipe, .tap, .map and so on

Test style:
- we use ScalaTest for testing
- we prefer BDD style high-level tests that focus on behavior and outcomes rather than implementation details
- the tests should call one and only one high-level function and treat it as a black box
- tests should avoid shared state and invisible dependencies
- tests must not rely on if-statements and should avoid loops

sbt configuration:
- This is a cross-project with three modules: `shared`, `js`, and `jvm`
- shared is not compiled standalone, but is included in both js and jvm
- to build the js, run `sbt js/fastLinkJS`
- to build the jvm, run `sbt jvm/compile`

ui library:
- we are using a fully custom css lib, defined in `jvm/src/main/resources/static/base.css`

terminal integration (use terminal.txt):
- Please pipe all outputs that you want to read, into a txt file terminal.txt 
  The copilot integration is a dumpster fire and the terminal readout no longer works. 
  So you have to use this file from now on.