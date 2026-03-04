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

Compiling:
- bloop compile jvm
- bloop compile js

running tests:
- to run jvm tests, use bloop: `bloop test jvm`

ui library:
- we are using a fully custom css lib, defined in `jvm/src/main/resources/static/base.css`

Important Laminar Concepts:
- when using signals that update frequently (e.g., game state during combat), always use `.distinct` on the signal to prevent unnecessary DOM updates
  - without `.distinct`, every signal emission triggers a re-render even if the value hasn't changed
  - example: `child.text <-- gameSignal.map(g => g.someValue).distinct`
  - this is especially important for elements inside frequently-updating views like combat, animations, or real-time data
- combineWith automatically flattens tuples: Signal[A].combineWith[B].combineWith[C] => Signal[(A, B, C)]

terminal issues:
- sometimes you will not be able to read terminal output. In this case, clear the terminal buffer and try again.