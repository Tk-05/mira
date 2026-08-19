# Mira Language Guide

[← Back to index](../Documentation.md) · [Grammar Reference](grammar.md) · [Standard Library Reference](standard-library.md) · [Build System](build-system.md) · [IDE Integration](ide-integration.md)

Core language reference: syntax, types, control flow, functions, and the built-in test framework. For `import`ed modules like `string`/`math`/`json` etc., see the [Standard Library Reference](standard-library.md).

## Table of Contents

1. [Program Structure](#program-structure) — Module Declaration, Comments, Imports, Module Visibility, Native JAR Extensions, Dynamic Import
2. [Values](#values) — Variables, Destructuring, Literals
3. [Expressions](#expressions) — Operators, `??`, `?.`, Ternary, Pipe
4. [Data Structures](#data-structures) — List, Array, Object, Map, Range
5. [Control Flow](#control-flow) — If, While, For, Foreach, Switch, `exec { }`
6. [Functions](#functions) — Default Parameters, Variadic, Inner Functions, Lambdas, Async/Await, spawn, Pure Functions
7. [Type Annotations](#type-annotations) — Optional typing, type aliases, enforcement, strict mode
8. [Comptime](#comptime) — Compile-Time Code Execution
9. [static_assert](#static_assert) — Compile-Time Assertions
10. [Objects with Methods](#objects-with-methods)
11. [Structs](#structs)
12. [Enums](#enums)
13. [Built-in Functions](#built-in-functions)
14. [Multithreading](#multithreading)
15. [Testing](#testing)
16. [Example Program](#example-program)

---

## Program Structure

### Module Declaration

Every file declares its module name at the top:

```
module <name>;
```

### Comments

```
// Single-line comment
var x : 10; // Inline comment

/* Multi-line
   comment */

var x : /* inline block */ 10;
```

### Imports

```
import <lib>;                                                       // Standard library (global scope)
import <lib> as <alias>;                                            // Standard library under alias
import <lib>: <sym1>, <sym2>;                                        // Selective stdlib import with colon (global scope)
import <lib>: <sym1>, <sym2> as <alias>;                            // Selective stdlib import with colon under alias
import <lib> {<sym1>, <sym2>};                                      // Selective stdlib import with braces (global scope)
import <lib> {<sym1>, <sym2>} as <alias>;                           // Selective stdlib import with braces under alias
import module "./path/to/file.mira";                                // File import — all pub symbols (global scope)
import module "./path/to/file.mira" as <alias>;                     // File import — all pub symbols under alias
import module "./path/to/file.mira" {<name1>, <name2>};             // Partial file import — named pub symbols (global scope)
import module "./path/to/file.mira" {<name1>, <name2>} as <alias>;  // Partial file import under alias
import native "./path/to/lib.jar" as <alias>;                       // Native JAR extension (alias required)
```

When a lib is imported without an alias, all its symbols are available globally. When imported with an alias, symbols are accessed via `<alias>.<name>(...)`.

If two libs imported without an alias define a function with the same name, a conflict error is thrown. Use aliases to resolve it:

```
import string;            // ok
import collection as col; // avoids conflict with 'indexOf'

trim(text);
col.indexOf(list, "x");
```

### Module Visibility

Module files use `pub` to mark declarations as exportable. Declarations without `pub` are **private** and are never made available to importing files.

```
module MyMod;

pub fn greet(name) { return "hello " + name; }   // exported
fn helper() { return "internal"; }              // private — not visible to importers
pub const MAX : 100;                             // exported
pub enum Color { Red, Green, Blue }              // exported
```

`pub` applies to `fn`, `const`, `var`, and `enum` declarations. It is only valid at the top level of a module — using `pub` inside a function body is a parse error.

When a module is imported without partial braces, only `pub`-marked symbols are copied into the importing scope. Private symbols remain hidden regardless of how the file is imported.

**Partial import — selecting specific symbols:**

```
import module "./utils.mira" {greet, MAX};          // only greet and MAX (global scope)
import module "./utils.mira" {greet} as utils;      // only greet, under utils.greet(...)
```

Requesting a private symbol throws `E230 PrivateSymbolImportError`. Requesting a symbol that does not exist throws `E231 ModuleSymbolNotFoundError`. Both are also caught at static-check time (E318 / E319) without running the program.

**Selective stdlib imports** use the same brace syntax. The selected names can be functions, constants, or any other symbol the library exposes:

```
import string {trim, split};         // only trim and split in global scope
import string {trim} as str;         // only trim, accessed as str.trim(...)
import string: trim, split;          // colon syntax — equivalent to braces
```

**Errors:**

| Error                            | Kind         | Cause                                                                 |
| -------------------------------- | ------------ | --------------------------------------------------------------------- |
| `E230 PrivateSymbolImportError`  | Runtime      | Selective import requested a symbol that is not marked `pub`          |
| `E231 ModuleSymbolNotFoundError` | Runtime      | Selective import requested a symbol that does not exist in the module |
| `E318 PrivateImportError`        | Static check | Same as E230, detected at analysis time before the program runs       |
| `E319 UnknownModuleSymbolError`  | Static check | Same as E231, detected at analysis time before the program runs       |

### Native JAR Extensions

`import native` loads an external Java JAR at runtime. The JAR must implement the `com.mira.lib.Lib` interface and register itself via the Java `ServiceLoader` mechanism (`META-INF/services/com.mira.lib.Lib`).

An alias is always required — native imports never pollute the global scope.

```
import native "./extensions/mylib.jar" as ext;

ext.greet("world");
ext.compute(42);
```

The path is resolved relative to the importing file. Absolute paths are also accepted. A bare filename with no path separators (e.g. `import native "raylib.jar" as raylib;`) is first checked against any `[native]`-declared dependency (see [Native Dependencies](build-system.md#native-dependencies)) before falling back to a literal relative/absolute path — this is what lets a project depend on a native JAR without hardcoding a path to it.

**JAR structure required:**

```
mylib.jar
├── META-INF/services/com.mira.lib.Lib   ← fully-qualified class name
└── com/example/MyLib.class              ← implements Lib
```

**Errors:**

| Error                                 | Cause                                                 |
| ------------------------------------- | ----------------------------------------------------- |
| `E222 NativeLibNotFoundError`         | JAR file does not exist at the given path             |
| `E223 NativeLibNoImplementationError` | JAR has no `META-INF/services/com.mira.lib.Lib` entry |
| `E224 NativeLibLoadError`             | JAR is invalid or incompatible with the interpreter   |

### Dynamic Import

`import module` is a static, top-level-only statement — the path must be a string literal, and it cannot appear inside a function body. `importDynamic(<path>)` is the runtime counterpart: it loads a `.mira` module whose path is only known at runtime, and it can be called from anywhere, including inside a function.

```
importDynamic(<path>);              // whole module — returns a Namespace
importDynamic(<path>, {<sym1>, <sym2>});  // only the selected pub symbols — returns a Namespace
```

Unlike `import module`, `importDynamic` never merges symbols into the current scope. It always returns the module as a first-class `Namespace` value:

```
var plugin : importDynamic("./plugins/" + name + ".mira");
plugin.run();
```

Because it is an ordinary function call rather than a statement, it works inside functions:

```
fn loadPlugin(path) {
    var mod : importDynamic(path);
    return mod.run();
}
```

Path resolution follows the same rules as `import module` (relative to the importing file, then dependency roots), and the same `pub`/private visibility rules apply — requesting a private symbol or a nonexistent one throws (see below). Loaded modules are cached the same way as static imports, so re-importing an unchanged file does not reparse it.

**Errors:** failures (missing file, missing `module` declaration, requesting a private or unknown symbol) are surfaced as a catchable `ImportError`, not a raw crash:

```
try {
    importDynamic("./does-not-exist.mira");
} catch (ImportError e) {
    print("failed to load plugin: " + e + "\n");
}
```

---

## Values

### Variable Declaration

```
var <name>;                                    // Uninitialized (implicitly null)
var <name> : <expression>;                     // With initial value
var <name1>, <name2>, ...;                     // Multiple variables at once
var <name1> : <expr1>, <name2> : <expr2>, ...; // Multiple with individual initializers
const <name> : <expression>;                   // Immutable
const <name1> : <expr1>, <name2> : <expr2>;    // Multiple constants
```

Multiple variables can be declared in a single statement, each with its own optional initializer (like C):

```
var x, y;            // x = null, y = null
var x : 5, y;        // x = 5,    y = null
var x, y : 10;       // x = null, y = 10
var x : 5, y : 10;   // x = 5,    y = 10
```

### Destructuring

Unpacks a list or array into multiple variables in one statement:

```
var (<name1>, <name2>, ...) : <expression>;
```

Example:

```
var t : {10, 20, 30};
var (a, b, c) : t;
print(a + "\n");   // => 10
print(b + "\n");   // => 20
print(c + "\n");   // => 30
```

Works with arrays too:

```
var (x, y) : {1, 2};
var (p, q) : [3, 4];
```

If there are fewer names than elements, the extra elements are ignored. If there are more names than elements, the extra variables are set to `null`.

### Variable Access & Assignment

Variables are accessed by bare name — no sigil, like C:

```
<name>
<obj>.<field>
<obj>.<nested>.<field>
```

Assignment:

```
<name> : <expression>;
<obj>.<field> : <expression>;
```

Field access also works directly on any expression — including function call results and inline structs — without assigning to a variable first:

```
fn test() {
    return { var a : 42; };
}

test().a          // => 42
test().a.b        // Chained field access
test()?.a         // Optional chaining on call result
```

### Compound Assignment

```
<name> +: <expression>;
<name> -: <expression>;
<name> *: <expression>;
<name> /: <expression>;
<name> %: <expression>;
<name> **: <expression>;
<name> \%: <expression>;
<name> &: <expression>;
<name> |: <expression>;
<name> ^: <expression>;
```

### Literals

| Type    | Example         |
| ------- | --------------- |
| Number  | `10`, `3.14`    |
| String  | `"hello world"` |
| Boolean | `true`, `false` |
| Null    | `null`          |

A string value only ever comes from an actual string literal — there is no
"bareword is a string" fallback. Adjacent string *literals* are spliced at
parse time, exactly like C:

```
"hello " "world"    // => "hello world"
```

Joining a string with a variable or any other expression uses `+`:

```
"hello " + name + "\n"
```

`+` adds numbers, and falls back to stringifying and concatenating whenever
either side isn't a number — so it works for any value, not just strings.
For structured/printf-style output, see `format()` in the
[Standard Library Reference](standard-library.md).

### Text Blocks

Multi-line strings using triple quotes. The first newline after `"""` is automatically stripped:

```
var text : """
Hello World
Line 2
""";
```

---

## Expressions

### Operators

| Category          | Operators                           |
| ----------------- | ----------------------------------- |
| Arithmetic        | `+`, `-`, `*`, `/`, `%`, `**`, `\%` |
| Comparison        | `<`, `>`, `<=`, `>=`, `==`, `!=`    |
| Logical           | `&&`, `\|\|`, `!`                   |
| Bitwise           | `&`, `\|`, `^`, `~`, `<<`, `>>`     |
| Postfix / Prefix  | `++`, `--`                          |
| Ternary           | `? :`                               |
| Null-Coalescing   | `??`                                |
| Optional Chaining | `?.`                                |

### `++` / `--`

`target++`/`target--` (postfix) and `++target`/`--target` (prefix) both
evaluate `target`, coerce it to a number, and compute `value + 1`/`value - 1`.
What happens to that result depends on what `target` is:

- **Bare variable** (`x++`, `++x`) — the variable is mutated in place.
- **Array/list/map element** (`arr[0]++`, `++arr[0]`, `map["k"]++`) — the
  element is mutated in place.
- **Object/struct field** (`obj.count++`, `++obj.count`) — the field is
  mutated in place.
- **Anything else** (a literal, an arithmetic expression, a function call,
  …) — there is no variable/element/field to write back to, so nothing is
  mutated. The expression just evaluates to `value ± 1`, exactly like
  `value + 1` would.

Prefix and postfix are purely a matter of where you write the operator —
Mira has no C-style "postfix returns the old value" distinction. Both forms
return the already-incremented/decremented value and both mutate (or don't)
the same way, so `++x` and `x++` are fully interchangeable:

```
var x : 2;
print(1++);          // 2  — no referent, nothing mutated
print(++1);          // 2  — same thing, prefix form
print((x + 1)++);    // 4  — x is still 2 afterwards

var arr : [1, 2, 3];
arr[0]++;             // arr[0] is now 2
++arr[1];              // arr[1] is now 3

var obj : { var count : 1; };
obj.count++;           // obj.count is now 2
```

`**` is the power/exponentiation operator. It has higher precedence than `*`, `/`, and `%`:

```
10 ** 2      // => 100
2 ** 10      // => 1024
9 ** 0.5     // => 3.0  (square root)
2 * 3 ** 2   // => 18   (3**2 first, then * 2)
```

`\%` is the floor division operator — divides and rounds down to the nearest integer:

```
7 \% 2       // => 3
9 \% 4       // => 2
7.5 \% 3.0   // => 2.0
```

Arithmetic is an ordinary expression, usable anywhere — no wrapping needed:

```
a + b
x * 2
```

Comparisons can also be used directly in conditions:

```
i < 5
x > 3 && y == 0
```

### Ternary Operator

Evaluates a condition and returns one of two values:

```
<condition> ? <then> : <else>
```

Example:

```
var label : score >= 50 ? "pass" : "fail";
x > 0 ? "positive" : "negative"
```

Ternaries can be nested:

```
x > 10 ? "high" : (x > 5 ? "mid" : "low")
```

Branches can be lambdas, picking which function to use based on a condition:

```
var op : useDouble ? (x) -> (x * 2) : (x) -> (x + 1);
print(op(5));   // 10
```

Both branches support string concatenation:

```
length(d) > 0 ? " (" d ")" : ""
ok ? "Result: " + value + "\n" : "n/a"
```

### Pipe Operator

Passes the left-hand value as the first argument to the right-hand call:

```
x |> trim()             // equivalent to trim(x)
x |> add(1)             // equivalent to add(x, 1)
```

Pipes can be chained left-to-right:

```
input |> trim() |> upper()
```

### Null-Coalescing Operator

Returns the left-hand value if it is not `null`, otherwise evaluates and returns the right-hand value:

```
x ?? "default"
config ?? newMap()
```

Chains left-to-right:

```
a ?? b ?? "fallback"
```

### Optional Chaining

Accesses a field on an object, but returns `null` instead of throwing when the object is `null`:

```
obj?.field
```

Works in chains — if any step is `null`, the whole expression short-circuits to `null`:

```
user?.address?.city
```

Combine with `??` to provide a fallback:

```
user?.name ?? "anonymous"
```

Both `.` and `?.` work on any expression, not just variables:

```
getUser().name          // field access on call result
getUser()?.address?.city
```

### Grouping

Parentheses with a single expression group for precedence:

```
((val1 + val2) + 1)
((a + b) * c)
```

---

## Data Structures

### List

Ordered, mutable, dynamic-size collection using curly braces:

```
var x : {10, 20, 30};
x[0];                   // Index access
x[1] : 99;              // Mutate element
```

### Array

Ordered, mutable, fixed-size collection using square brackets:

```
var x : [10, 20, 30];
x[0];                   // Index access
x[1] : 99;              // Mutate element (allowed)
```

Arrays cannot grow or shrink — `push` and `pop` only work on lists.

### Object

Inline struct with named fields declared as `var`:

```
var obj : {
    var x : 0;
    var name : "hello";
};

obj.x;
obj.name;
obj.x : 42;             // Field assignment
```

Objects can be nested:

```
var wrapper : {
    var inner : {
        var a : 0;
    };
};
wrapper.inner.a;
```

Objects can also contain methods — see [Objects with Methods](#objects-with-methods).

### Map

Key-value store using curly braces with `"key": value` pairs:

```
var m : {"name": "Alice", "score": 42};
```

Access and assignment use bracket notation with string keys:

```
m["name"];              // => "Alice"
m["name"] : "Bob";      // reassign
```

Maps are mutable. An empty map is created with `newMap()` from the `map` library.

### Range

Used in loops, exclusive end:

```
<0..5>              // 0, 1, 2, 3, 4
<0..length(x)>
<0..10, 2>          // 0, 2, 4, 6, 8  (with step)
```

---

## Control Flow

### If / Else

```
if (<condition>) {
    <body>
} else if (<condition>) {
    <body>
} else {
    <body>
}
```

### While

```
while (<condition>) {
    <body>
}
```

### Do-While

Executes the body at least once before checking the condition:

```
do {
    <body>
} while (<condition>);
```

### For

Classic C-style for loop:

```
for (<init>; <condition>; <update>) {
    <body>
}
```

Multiple initializers:

```
for (var i : 0, var j : 0; <condition>; <update>) { }
```

Omitting parts:

```
for (; <condition>; <update>) { }
for (;;) { }              // Infinite loop
```

Iterating with a bound variable — works over a list, array, string, or range:

```
for (var <name> in <collection>) {
    <body>
}
```

Range-based for without iterator:

```
for (<range>) {
    <body>
}
```

Iterates over the range without binding the value to a variable. Useful when only the number of iterations matters:

```
for (<0..10>) {
    println("hello");
}
```

### Switch

Compares an expression against a list of `case` values. Only the first matching block is executed — no `break` needed. `default` is optional and runs when no `case` matches.

**Block form:**

```
switch (<expression>) {
    case (<value>) {
        <body>
    }
    case (<value>) {
        <body>
    }
    default {
        <body>
    }
}
```

**Arrow form** — single statement per case, no braces needed:

```
switch (<expression>) {
    case (<value>) -> <statement>
    case (<value>) -> <statement>
    default -> <statement>
}
```

Example:

```
var x : 2;
switch (x) {
    case (1) -> print("one\n")
    case (2) -> print("two\n")
    default  -> print("other\n")
}
```

Both forms can be mixed freely in the same switch.

### Switch Expression

`switch` can also be used as an expression that returns a value. The arrow (`->`) form is required. Each arm is a single expression — no braces, no semicolons.

```
switch (<expression>) {
    case (<value>) -> <expression>
    case (<value>) -> <expression>
    default -> <expression>
}
```

Returns `null` if no case matches and there is no `default`.

Example as a return value:

```
fn describe(n) {
    return switch(n) {
        case (1) -> "one"
        case (2) -> "two"
        default  -> "other"
    };
}

describe(1)   // => "one"
describe(9)   // => "other"
```

Example as a variable initializer:

```
var label : switch(code) {
    case (200) -> "ok"
    case (404) -> "not found"
    default    -> "error"
};
```

Usage with enums:

```
var dir : Direction.EAST;
var label : switch(dir) {
    case (Direction.NORTH) -> "N"
    case (Direction.SOUTH) -> "S"
    case (Direction.EAST)  -> "E"
    case (Direction.WEST)  -> "W"
};
```

### exec Block

`exec { }` executes a block of statements as an isolated expression and returns the value of its `return` statement. Variables declared inside the block do not leak into the surrounding scope.

```
exec {
    <body>
}

exec isolated {
    <body>
}
```

- **`exec { }`** — runs with access to the enclosing local scope (reads and writes outer variables).
- **`exec isolated { }`** — runs with access to the global scope only; local variables of the enclosing function are not visible.

Without a `return` statement, the block yields `null`.

**As an expression (variable initializer):**

```
var label : exec {
    if (score > 90) { return "A"; }
    if (score > 75) { return "B"; }
    return "C";
};
```

**Scope isolation — temporary variables are discarded:**

```
var checksum : exec {
    var buf : readFile("data.bin");
    var hash : computeHash(buf);
    return hash;
};
// buf and hash are not accessible here
```

**Reading and writing outer variables:**

```
var x : 10;
exec { x : 99; };
// x is now 99
```

**`exec isolated` inside a function:**

```
var config : { var token : "abc"; };

fn processRequest(userId) {
    var token : exec isolated {
        return config.token;   // sees globals, not userId
    };
}
```

**`return` in an exec block only exits the block, not the enclosing function:**

```
fn test() {
    var r : exec { return 1; };   // returns 1 from exec, not from test()
    return 100;
}
test();   // => 100
```

> **Note:** `(<code>)` (the built-in function) is the unrelated tool for running a dynamically constructed code *string* — see [Dynamic Code Execution](#dynamic-code-execution). `exec { }` is the _static_ block form documented here — it does not accept a string.

### Break / Continue

```
break;
continue;
```

### Try / Catch / Finally / Throw

Executes the `try` block and, if an exception is thrown, binds the value to the catch parameter and runs the `catch` block. The optional `finally` block always runs — whether or not an exception was thrown.

```
try {
    <body>
} catch(<param>) {
    <body>
} finally {
    <body>
}
```

`finally` is optional:

```
try {
    <body>
} catch(<param>) {
    <body>
}
```

`throw` raises a value as an exception:

```
throw <expression>;
```

Example:

```
try {
    throw "something went wrong";
} catch(e) {
    print(e + "\n");
} finally {
    print("always runs\n");
}
```

A `catch` clause can optionally filter by exception type: `catch (<Type> <param>) { ... }` only runs if the thrown value's type matches `<Type>`; `catch(<param>)` (no type) catches everything. Built-in dynamic-execution errors use this to let callers distinguish failure kinds:

```
try {
    importDynamic("./plugin.mira");
} catch (ImportError e) {
    print("import failed: " + e + "\n");
} catch (e) {
    print("something else went wrong: " + e + "\n");
}
```

---

## Functions

### Declaration

```
fn <name>(<param1>, <param2>) {
    <body>
}
```

### Return

```
return;                // Return nothing
return <expression>;   // Return a value
```

### Call

```
<name>(<arg1>, <arg2>)
```

Functions from aliased imports are called via dot notation:

```
<alias>.<name>(<arg>)
```

### Default Parameters

Parameters can have a default value using `:`. If the caller omits the argument, the default is evaluated:

```
fn <name>(<param1>, <param2> : <default>) {
    <body>
}
```

Example:

```
fn greet(name, greeting : "Hello") {
    print(greeting + " " + name + "\n");
}

greet("World");           // => Hello World
greet("World", "Hi");     // => Hi World
```

Default parameters must come after required parameters. Works in lambdas too:

```
var add : fn(x, step : 1) { return (x + step); };
add(5);     // => 6
add(5, 10); // => 15
```

### Variadic Parameters

The last parameter can be variadic using `...`. All remaining arguments are collected into a list:

```
fn <name>(<param1>, ...<rest>) {
    <body>
}
```

Example:

```
fn sum(...args) {
    var total : 0;
    for (var x in args) {
        total : (total + x);
    }
    return total;
}

sum(1, 2, 3)    // => 6
sum()           // => 0  (args is an empty list)
```

Mixed (fixed + variadic):

```
fn log(prefix, ...args) {
    print(prefix + ": ");
    for (var a in args) { print(a + " "); }
}
```

### Inner Functions

A `fn` declaration inside another function body is an inner function. When the outer function executes, the inner function is registered in the **global** scope and captures the outer function's local variables as a closure.

```
fn <outer>(<params>) {
    fn <inner>(<params>) {
        <body>
    }
    <inner>(<args>)
}
```

Example:

```
fn makeAdder(base) {
    fn add(x) {
        return (base + x);
    }
    return add(10);
}

makeAdder(5);   // => 15
add(3);         // => 8  (add is now globally visible, base is still 5)
```

**Scoping rules:**

- The inner function is added to the global environment when the outer function first runs — before that, it does not exist.
- The inner function closes over the outer function's local variables at the time of definition, exactly like a lambda.
- Calling the outer function multiple times re-registers the inner function, replacing the previous closure.

Inner functions are useful as named helper routines that share the outer function's parameters without passing them explicitly:

```
fn process(data, threshold) {
    fn isValid(x) {
        return x > threshold;
    }
    for (var item in data) {
        if (isValid(item)) {
            print(item + "\n");
        }
    }
}
```

For a local-only helper that should not leak into global scope, use a lambda stored in a `var` instead:

```
fn process(data, threshold) {
    var isValid : fn(x) { return x > threshold; };
    for (var item in data) {
        if (isValid(item)) {
            print(item + "\n");
        }
    }
}
```

### Lambdas

Lambdas are nameless functions that can be stored and passed around:

```
fn(<param1>, <param2>) {
    <body>
}
```

As a variable:

```
var double : fn(x) { return (x * 2); };
(double(5));    // => 10
```

As an argument:

```
fn apply(f, x) {
    return f(x);
}

(apply(fn(n) { return eval(n * n); }, 3));   // => 9
```

Closures — lambdas capture variables from their outer scope:

```
var factor : 3;
var scale : fn(x) { return (x * factor); };
(scale(5));    // => 15
```

Lambdas support variadic parameters too:

```
var join : fn(sep, ...parts) { return join(parts, sep); };
```

### Arrow Lambdas

A shorter syntax for lambdas using `->`. Parameters are always wrapped in parentheses:

```
(<param1>, <param2>) -> <expression>
(<param1>, <param2>) -> { <body> }
```

If the body is a single expression, it is returned implicitly — no `return` needed:

```
var double : (x) -> (x * 2);
var add : (a, b) -> (a + b);
var greet : () -> "hello";
```

A block body with `{}` allows multiple statements:

```
var process : (x) -> {
    println(x);
    return (x + 1);
};
```

Arrow lambdas work anywhere a regular lambda does — as arguments, in closures, with default parameters:

```
fn apply(f, x) { return f(x); }
(apply((x) -> eval(x * x), 5));   // => 25

var base : 10;
var offset : (n) -> (n + base);   // captures outer variable
(offset(3));   // => 13

var clamp : (x : 0) -> x;   // default parameter
clamp();    // => 0
```

### typeof

`typeof` is a keyword operator that returns a string describing the runtime type of any value. It has the same precedence as a unary prefix operator.

```
typeof <expression>
```

**Return values:**

| Value              | Result      |
| ------------------ | ----------- |
| Integer or float   | `"number"`  |
| String             | `"string"`  |
| Boolean            | `"bool"`    |
| `null`             | `"null"`    |
| List `{...}`       | `"list"`    |
| Array `[...]`      | `"array"`   |
| Map                | `"map"`     |
| Function or lambda | `"fn"`      |
| Promise            | `"promise"` |
| Object             | `"object"`  |

```
typeof 42;          // "number"
typeof "hello";     // "string"
typeof true;        // "bool"
typeof null;        // "null"
```

For variables, `typeof` evaluates the variable and inspects the stored value:

```
var x : 99;
typeof x;          // "number"

var l : {1, 2, 3};
typeof l;          // "list"
```

`typeof` can be used in conditions and switch expressions:

```
var x : 42;
typeof x == "number" ? "yes" : "no";   // "yes"
```

```
var result : switch(typeof x) {
    case("number") -> "it's a number"
    case("string") -> "it's a string"
    default        -> "something else"
};
```

### Async / Await

Mark a function as `async` to make it execute in the background. Calling an async function immediately returns a `Promise` without blocking. Use `await` to block until the promise resolves and get its value.

```
async fn <name>(<params>) {
    <body>
}

var result : await <name>(<args>);
```

Example:

```
async fn fetchData(url) {
    var response : httpGet(url);
    return response;
}

var data : await fetchData("https://example.com/api");
print(data + "\n");
```

Multiple async calls can be started before awaiting, so they run in parallel:

```
async fn slow(n) {
    sleep((n * 100));
    return n;
}

var p1 : slow(3);
var p2 : slow(1);
var p3 : slow(2);

print(await p1 + "\n");   // => 3
print(await p2 + "\n");   // => 1
print(await p3 + "\n");   // => 2
```

Async lambdas work the same way:

```
var fetch : async fn(url) { return httpGet(url); };
var result : await fetch("https://example.com");
```

**Scoping:** async functions share the global environment with the caller. Each async call runs on a separate interpreter instance, so local variables are isolated.

**Error handling:** if an async function throws, the exception is re-thrown at the `await` site and can be caught normally:

```
try {
    var result : await riskyOp();
} catch(e) {
    print("failed: " + e + "\n");
}
```

### spawn

`spawn` runs any callable (lambda or function reference) in a background thread and returns a `Promise`. This is the low-level building block for parallelism — use it when you need to run arbitrary code concurrently without declaring an `async fn`.

```
var handle : spawn(fn() { <body> });
var result : await(handle);
```

Example — parallel heavy computations:

```
fn heavy(n) {
    var s : 0;
    for (var i : 0; i < n; i++) { s +: i; }
    return s;
}

var h1 : spawn(fn() { return heavy(1000000); });
var h2 : spawn(fn() { return heavy(2000000); });

print(await(h1) "\n");
print(await(h2) "\n");
```

Both spawned tasks run in parallel on the common thread pool. `await` blocks only when you actually need the result.

Error propagation works the same as with `async fn`:

```
var h : spawn(fn() { throw "oops"; });
try {
    await(h);
} catch(e) {
    print("caught: " + e + "\n");
}
```

### Pure Functions

Mark a function as `pure` to enable automatic memoization. A pure function must produce the same output for the same inputs and must have no observable side effects. Mira caches the result of each unique argument combination so that subsequent calls with the same arguments skip the function body entirely and return the cached value.

```
pure fn <name>(<params>) {
    <body>
}
```

Example:

```
pure fn fib(n) {
    if (n <= 1) { return n; }
    return (fib(eval(n - 1)) + fib(eval(n - 2)));
}

fib(30)   // computed once
fib(30)   // returned from cache instantly
```

**How it works:**

- **Interpreter:** results are keyed by `(functionName, argList)`. On a cache hit the body is skipped and the stored result is returned immediately.
- **Compiler (AOT):** each `pure fn` gets a dedicated `ConcurrentHashMap` field in the generated class. The compiled wrapper checks the map before delegating to the actual implementation method.

**When to use `pure`:**

| Good fit                             | Bad fit                                   |
| ------------------------------------ | ----------------------------------------- |
| Recursive numeric algorithms         | Functions that read or write global state |
| Expensive computations with few args | Functions that perform I/O                |
| Deterministic transformations        | Functions whose result depends on time    |

> **Note:** The interpreter also has an automatic purity analyzer (`PurityAnalyzer`) that detects functions without side effects and caches them silently. The `pure` keyword extends this: it forces caching even when automatic analysis would not classify the function as pure (e.g. because it calls another function whose purity cannot be statically proven).

---

## Type Annotations

Mira is **gradually typed**: type annotations are entirely optional. Add them where they help; leave them off everywhere else and nothing changes — an unannotated program behaves exactly as it always has.

An annotation is written with a **second colon**, right before the value/initializer position it normally occupies. The type comes first, the value second:

```
var <name> : <Type> : <expression>;    // typed, initialized
var <name> : <expression>;             // unchanged — untyped
```

Example:

```
var age : Number : 30;
var name : String : "Ada";
var maybe : Number? : null;    // nullable — suffix `?`
```

A single `:` still means "here's the initializer," exactly as before. Mira only reinterprets it as a type when a **second** `:` follows — so every existing untyped program keeps parsing exactly as it did. Typed `var` declarations always need an initializer; there's no "typed but empty" form (use `<Type>?` with a `null` initializer instead).

### Built-in Types

`Number`, `String`, `Bool`, `List`, `Array`, `Map`, `Object`, `Fn`, `Null`, `Any`, `Void`. `Any` is an explicit escape hatch — it's always assignable in both directions. There's no separate `Int`/`Float`: Mira's numbers stay `Long`/`Double` under the hood exactly as before, and division still always widens to `Double`.

`Void` is only meaningful as a function's return type — see [Void Return Type](#void-return-type) below.

A declared struct variable name, `enum` name, or `type` alias name can also be used as a type (see [Type Aliases](#type-aliases) below). A struct instance is checked against the specific template it was created from, not just the generic `Object` shape:

```mira
var point : struct { var x; var y; };
var color : struct { var r; var g; var b; };

fn makePoint() -> point {
    return $point{$x : 1, $y : 2};   // OK - matches the declared template
}

fn makeColor() -> point {
    return $color{$r : 1, $g : 2, $b : 3};   // ReturnTypeMismatchError - wrong template
}
```

This also works through a variable, not just a literal instantiation directly in the `return`:

```mira
var origin : $point{$x : 0, $y : 0};

fn getOrigin() -> point {
    return $origin;   // OK - $origin was created from the `point` template
}
```

### Function Parameters and Return Types

Parameters use the same double-colon rule. A parameter with a type but no default only needs a single colon — a bare type name immediately followed by `,` or `)` is unambiguous:

```
fn <name>(<param> : <Type>, <param2> : <Type2> : <default>) -> <ReturnType> {
    <body>
}
```

Example:

```
fn add(a : Number, b : Number) -> Number {
    return eval($a + $b);
}
```

`->` for the return type only applies to named `fn` declarations. Lambdas and arrow lambdas (`($x) -> expr`) don't support a return-type annotation — `->` there still introduces the lambda body.

### Void Return Type

`Void` declares that a function doesn't return a value:

```mira
fn log(msg) -> Void {
    println($msg);
}
```

- A bare `return;`, or simply reaching the end of the function body without a `return` at all, is fine.
- `return <anything>;` — including `return null;` — is a compile-time error (`ReturnTypeMismatchError`, `E326`), even though `null` would normally be a valid value almost everywhere else. `Void` means "provides no value," which is a stricter statement than "provides `Null`."

`Void` is therefore not the same as `Null`: a function declared `-> Null` must actually `return null;` (or another expression that evaluates to `null`) on every path, while `-> Void` forbids providing a value in the first place.

### Struct Fields

Struct field declarations reuse the same `var` syntax as top-level variables, so they can be typed the same way:

```
var Point : struct {
    var x : Number : 0;
    var y : Number : 0;
};
```

Overriding a field at instantiation time (`$Point{$x : "oops"}`) with a value of the wrong type is a compile-time error — see [Enforcement](#enforcement) below.

### Type Aliases

```
type <Name> : <Type>;
```

```
type UserId : Number;
var id : UserId : 42;
```

> **Note:** only a `type` declared at the true top level of a file is
> currently recognized. One written inside a function or block parses
> without error but has no effect — using the name it was meant to define
> reports `UnknownTypeNameError` (`E327`).

### Enforcement

Type checking runs as part of the same static-check pass that already catches things like undeclared variables — violations are compile-time errors that stop the program from running, in both interpreted mode and `--compile`. Every check below only fires when an explicit annotation is present somewhere in the comparison, so untyped code is never newly rejected:

| Check                                                          | Error                                   |
| ---------------------------------------------------------------| -----------------------------------------|
| Initializer or reassignment doesn't match the declared type    | `TypeMismatchError` (`E324`)             |
| Call argument doesn't match a parameter's declared type        | `ArgumentTypeMismatchError` (`E325`)     |
| Returned value doesn't match the declared return type          | `ReturnTypeMismatchError` (`E326`)       |
| Unknown type name                                              | `UnknownTypeNameError` (`E327`)          |
| Struct field override doesn't match the field's declared type  | `StructFieldTypeMismatchError` (`E329`)  |

### Strict Mode

`--strict-types` (or `mira.toml`'s `[build] strict-types = true` — see [Build System → General flags](build-system.md#general-flags)) requires every **top-level** function's parameters and return type to be explicitly annotated. It's off by default and doesn't change anything else — a project can adopt it incrementally without annotating every variable. A missing annotation under strict mode is `MissingTypeAnnotationError` (`E328`).

---

## Comptime

`comptime` blocks execute code **before** the program starts — at what Mira calls "compile time". They are useful for computing constants that are expensive or verbose to write as literals, and for running build-time assertions or diagnostics.

### Syntax

```
comptime {
    <body>
}
```

The block body is a normal sequence of statements. Any variable declared inside becomes an **immutable constant** available throughout the rest of the program. Other statements (e.g. `println`) run immediately as a side effect during startup, before any other code executes.

### Example

```
comptime {
    var MAX_SIZE : (64 * 1024);
    var APP_NAME : "MyApp";
    println("Build: constants initialized");
}

fn main() {
    println(APP_NAME);          // => "MyApp"
    println(MAX_SIZE);          // => 65536
}
```

Output when running:

```
Build: constants initialized
MyApp
65536
```

### Multiple comptime Blocks

Multiple `comptime` blocks are allowed in the same file. They are all executed in order before the main program begins:

```
comptime {
    var BASE : 100;
}

comptime {
    var LIMIT : (BASE * 10);
}
```

### Immutability

Variables declared in a `comptime` block are constants — assigning to them later is an error:

```
comptime {
    var PI : 3.14159;
}

PI : 3.0;   // error E205: ReferenceIsImmutableError
```

### Execution Model

- **Interpreter path:** All `comptime` blocks run in an isolated interpreter instance during the pre-pass phase (before `loadGlobalContext` finishes). Their results are injected into the global environment as constants.
- **Compiler path (`--compile`):** The same pre-pass runs before JVM bytecode is generated. Side effects (e.g. `println`) execute during compilation; constants are available to the compiled program.
- **Errors** inside a `comptime` block are reported like any other runtime error and abort the program before it starts.

### What Can Be Used Inside comptime

All built-in functions, standard library functions (if imported), arithmetic, string operations, and control flow are available:

```
comptime {
    import math as m;
    var SQRT2 : m.sqrt(2.0);
    var MSG : "version-" "1.0";
}
```

Recursive functions, loops, and `if` statements work too — the comptime block is ordinary Mira code, just executed at a different point in time.

---

## static_assert

`static_assert` checks a condition and aborts with error **E308** if it is falsy. At the top level it runs after `comptime` constants are injected but before user code, making it an effective compile-time guard. Inside functions it runs on every call.

### Syntax

```
static_assert(<condition>);
static_assert(<condition>, <message>);
```

The optional `message` is evaluated only when the assertion fails and is included in the error output.

### Examples

#### Basic guard

```
static_assert(1 == 1);               // passes silently
static_assert(false, "unreachable"); // [error][E308]: static assertion failed: unreachable
```

#### Using comptime constants

```
comptime {
    var MAX_SIZE : 64;
}

static_assert(MAX_SIZE > 0, "MAX_SIZE must be positive");
static_assert(MAX_SIZE <= 1024, "MAX_SIZE exceeds limit");
```

#### Inside a function

```
fn clampedSqrt(x) {
    static_assert(x >= 0, "argument must be non-negative");
    return m.sqrt(x);
}
```

### Error Format

A failing assertion produces a formatted diagnostic with the source location:

```
[error][E308]: static assertion failed: MAX_SIZE must be positive
  --> example.mira:8:0
   7 | }
   8 | static_assert(MAX_SIZE > 0, "MAX_SIZE must be positive");
     | ^^^^^^^^^^^^^
     |
```

### Relation to `assert`

|             | `assert`                    | `static_assert`                    |
| ----------- | --------------------------- | ---------------------------------- |
| Error code  | E210                        | E308                               |
| Typical use | runtime checks inside tests | compile-time / precondition guards |
| Message     | optional                    | optional                           |
| Scope       | anywhere                    | anywhere                           |

---

## Objects with Methods

Objects can contain `fn` declarations alongside `var` fields. Methods are called via dot notation and have implicit access to all fields of the same object.

### Declaration

```
var <name> : {
    var <field> : <value>;
    fn <method>(<params>) {
        <body>
    }
};
```

### Method Call

```
<name>.<method>(<args>)
```

### Field Access Inside Methods

Fields are accessible directly by name inside methods:

```
var counter : {
    var count : 0;
    fn increment() {
        count +: 1;
    }
    fn get() {
        return count;
    }
};

counter.increment();
counter.increment();
counter.get();          // => 2
```

### `this` Reference

`this` is always available inside methods and refers to the object itself:

```
var obj : {
    var value : "hello";
    fn get() {
        return this.value;
    }
};

obj.get();              // => "hello"
```

### Method-only Objects

Objects can consist of only methods without any fields:

```
var math : {
    fn add(a, b) { return (a + b); }
    fn square(x) { return (x * x); }
};

math.add(3, 4);         // => 7
math.square(5);         // => 25
```

### Optional Chaining

Method calls support optional chaining — returns `null` if the object is `null`:

```
obj?.method()
obj?.method(arg)
```

### Methods with Default Parameters

Methods support the same default parameter syntax as regular functions:

```
var greeter : {
    fn greet(name, greeting : "Hello") {
        return greeting + " " + name;
    }
};

greeter.greet("World");          // => "Hello World"
greeter.greet("World", "Hi");    // => "Hi World"
```

---

## Structs

A struct is a named, reusable template for objects. Unlike a plain object (see [Objects with Methods](#objects-with-methods)), a struct is declared once and then instantiated any number of times, each instance getting its own independent copy of the fields.

### Declaration

```
var <name> : struct {
    var <field> [: <default>];
    fn <method>(<params>) {
        <body>
    }
};
```

Fields without an initializer default to `null`:

```
var point : struct { var x; var y; };
```

### Instantiation

Create an instance from a template with `<name>{ ... }`. Any field can be overridden; omitted fields keep their declared default:

```
var point : struct { var x : 0; var y : 0; };
var origin : point{};             // x=0, y=0
var p : point{x : 1};            // x=1, y=0 (partial override)
var q : point{x : 1, y : 2};    // x=1, y=2 (full override)
```

Each instance is independent — mutating one does not affect another:

```
var a : point{x : 1};
var b : point{x : 2};
a.x; // => 1
b.x; // => 2
```

### Methods and `this`

Methods declared on a struct survive instantiation and bind to the instance they were created from, exactly like [object methods](#this-reference):

```
var counter : struct {
    var count : 0;
    fn increment() { this.count : this.count + 1; }
    fn get() { return this.count; }
};

var a : counter{};
var b : counter{count : 100};
a.increment();
a.increment();
a.get();   // => 2
b.get();   // => 100
```

### Nested Structs

A struct field can itself be a struct instance:

```
var point : struct { var x : 0; var y : 0; };
var rect : struct {
    var topLeft : point{x : 1, y : 2};
    var width : 10;
};

var r : rect{};
r.topLeft.x;   // => 1
r.topLeft.y;   // => 2
r.width;       // => 10
```

### Errors

Overriding a field that isn't declared on the template throws `UnknownStructFieldError`:

```
var point : struct { var x; var y; };
var bad : point{z : 1};   // UnknownStructFieldError
```

Instantiating something that isn't a struct template (e.g. a plain object) throws `NotAStructTemplateError`:

```
var notATemplate : { var a : 1; };
var bad : notATemplate{a : 2};   // NotAStructTemplateError
```

---

## Enums

Enums declare a named set of constant variants. Each variant is immutable and accessed via dot notation.

### Declaration

```
enum <Name> {
    <VARIANT>,
    <VARIANT>
};
```

Variants are automatically assigned integer values starting at `0`:

```
enum Direction {
    NORTH,
    SOUTH,
    EAST,
    WEST
};
```

### Explicit Values

Variants can be assigned explicit integer or string values using `:`:

```
enum Status {
    OK        : 200,
    NOT_FOUND : 404,
    ERROR     : 500
};

enum Color {
    RED   : "red",
    GREEN : "green",
    BLUE  : "blue"
};
```

Mixed enums (some explicit, some auto-indexed) are allowed. Auto-indexed variants count from their position regardless of any explicit values.

### Access & Usage

```
Direction.NORTH   // => 0
Status.OK         // => 200
Color.RED         // => "red"
```

Enum values can be stored and compared like any other value:

```
var dir : Direction.SOUTH;
if (dir == Direction.SOUTH) {
    print("heading south\n");
}
```

Usage with `switch`:

```
var code : Status.NOT_FOUND;
switch (code) {
    case (200) -> print("ok\n")
    case (404) -> print("not found\n")
    default    -> print("error\n")
}
```

Or as a switch expression:

```
var message : switch(code) {
    case (200) -> "ok"
    case (404) -> "not found"
    default    -> "error"
};
```

---

## Built-in Functions

Always available without any import.

| Function                          | Parameters              | Description                                                                                                                                       |
| --------------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `print(<value>)`                  | Any value               | Prints the value to stdout without a newline                                                                                                      |
| `scan()`                          | —                       | Reads a line from stdin and returns it as a string                                                                                                |
| `(<code>)`                    | String                  | Parses and runs a runtime code *string* `<code>` (any Mira statements) and returns its value (see [Dynamic Code Execution](#dynamic-code-execution)) |
| `format(<pattern>, ...<args>)`    | String, any values       | printf-style formatting — `%d %f %s %b %%`, e.g. `format("%s: %d\n", name, count)`                                                                |
| `exec { <body> }`                 | Block                   | Executes a block and returns its `return` value (see [exec Block](#exec-block))                                                                   |
| `exec isolated { <body> }`        | Block                   | Same as `exec { }` but restricted to global scope only                                                                                            |
| `importDynamic(<path>)`           | String                  | Loads a `.mira` module at runtime and returns it as a `Namespace` (see [Dynamic Import](#dynamic-import))                                         |
| `importDynamic(<path>, {<syms>})` | String, List of strings | Same, but only the listed `pub` symbols                                                                                                           |
| `length(<value>)`                 | String, List, or Array  | Returns the number of characters / elements                                                                                                       |
| `exit(<code>)`                    | Number                  | Exits the program with the given exit code                                                                                                        |
| `assert(<cond>)`                  | Boolean expression      | Throws a runtime error if the condition is false                                                                                                  |
| `assert(<cond>, <message>)`       | Boolean, String         | Throws with a custom message if condition is false                                                                                                |

### Dynamic Code Execution

`eval` has exactly one job: run a runtime-constructed code *string*, for
cases where the code to execute isn't known until the program is running
(loaded from a file, typed by a user, generated on the fly). Ordinary
arithmetic and other expressions are never wrapped in `()` — they're
just expressions, usable directly anywhere a value is expected.

`eval` tokenizes, parses, and runs `<code>` against the _live_ current
scope — the same engine that backs the REPL. Any Mira code is valid:
expressions, `var` declarations, control flow, function calls. Declarations
made this way are visible to later `eval` calls (and, at the top level, to
the rest of the program):

```
eval("var greeting : \"hi\";");
print(eval("greeting;"));   // hi
```

Failures — a syntax error in the code string, or a runtime error while running it (e.g. an undefined variable) — do not crash the program. They are thrown as a catchable `EvalError`:

```
try {
    eval("this is not valid mira");
} catch (EvalError e) {
    print("bad code: " + e + "\n");
}
```

A `throw` executed _by_ the evaluated code propagates normally and is not wrapped — `eval` only wraps its own parse/execution failures.

---

## Multithreading

Mira supports parallel execution via `spawn`/`await` and safe shared-state access via the `lock` statement and the `thread` standard library.

### Spawning Threads

`spawn(callable)` runs a callable concurrently and returns a **Promise**. `await(promise)` blocks until the result is ready.

```
import thread as thread;

fn compute(n) {
    var sum : 0;
    for (var i : 0; i < n; i : (i + 1)) {
        sum : (sum + i);
    }
    return sum;
}

var t1 : spawn(fn() { return compute(100000); });
var t2 : spawn(fn() { return compute(200000); });

println(await(t1));
println(await(t2));
```

### Mutexes and the `lock` Statement

When multiple threads share mutable state, use a **mutex** to prevent race conditions. Create one with `thread.newMutex()`, then guard critical sections with `lock`.

```
import thread as thread;

var mu      : thread.newMutex();
var counter : 0;

fn increment() {
    lock(mu) {
        counter : (counter + 1);
    }
}

var tasks : {};
for (var i : 0; i < 10; i : (i + 1)) {
    col.push(tasks, spawn(fn() { increment(); }));
}

for (var t in tasks) {
    await(t);
}

println(counter);   // => 10
```

**Syntax:**

```
lock(mutexExpression) {
    // critical section
}
```

The `lock` block is always released — even if an exception is thrown inside. The underlying implementation uses the JVM's built-in `monitorenter`/`monitorexit` instructions.

### `thread` Library

Import with `import thread as thread;`.

| Function     | Description                |
| ------------ | -------------------------- |
| `newMutex()` | Creates a new mutex object |

---

## Testing

Mira has a built-in test framework. Use the `test(name, fn)` function to register test cases and run them with the `--test` flag.

### Writing Tests

```
module MyTests;

test("addition works", fn() {
    assert(1 + 1 == 2);
});

test("string comparison", fn() {
    assert(strEqual("hello", "hello"));
});

test("math library", fn() {
    import math as m;
    assert(m.sqrt(9.0) == 3.0);
    assert(m.abs(-5.0) == 5.0);
});
```

The second argument to `test` is a zero-argument function. Inside it, use `assert(condition)` to check expectations — `assert` throws an exception when the condition is false, which the test runner catches and records as a failure.

### Running Tests

```
java -jar mira.jar MyTests.mira --test
```

**Output:**

```
  PASS addition works
  PASS string comparison
  PASS math library

─── Test Summary ───
  Passed : 3
  Failed : 0
  Total  : 3
  Status : OK
```

When a test fails:

```
  FAIL addition works — assertion failed
  PASS string comparison

─── Test Summary ───
  Passed : 1
  Failed : 1
  Total  : 2
  Status : FAILED
```

The process exits with code `1` when any test fails, making it suitable for CI pipelines.

### Coverage

Add `--coverage` to get a line-coverage report after the run, for the test file itself and every module it imports, transitively:

```
java -jar mira.jar MyTests.mira --test --coverage
```

**Output (appended after the test summary):**

```
=== MIRA COVERAGE ===
Files: 2
  MyTests.mira    3/3 lines (100.0%)
  mathlib.mira    1/2 lines (50.0%)

Totals: 4/5 lines covered (80.0%)

Uncovered lines:
  mathlib.mira: 8
=== END COVERAGE ===
```

`--coverage` also works with the `mira test` project command (see [Build System](build-system.md#coverage)), where it reports across every discovered test file plus everything they import.

### Notes

- `test(name, fn)` is only available when running with `--test`. Defining a function named `test` in normal code works without conflicts.
- Tests run sequentially in the order they are registered.
- Any uncaught exception inside a test body counts as a failure. The error message is shown next to `FAIL`.
- `assert` is a built-in function available in all contexts, not only inside tests.

---

## Example Program

```
module main;

fn fibonacci(n) {
    if (n <= 1) {
        return n;
    } else {
        return fibonacci((n - 2)) + fibonacci((n - 1));
    }
    return 0;
}

fn main() {
    var result : 0;

    for (var i : 0; i < 10; i : (i + 1)) {
        result : (result + fibonacci(i));
    }

    print("Sum: " + result + "\n");
}
```
