# Mira Language Syntax Reference

## Table of Contents

1. [Program Structure](#program-structure)
2. [Values](#values) — Variables, Destructuring, Literals
3. [Expressions](#expressions) — Operators, `??`, `?.`, Ternary, Pipe
4. [Data Structures](#data-structures) — List, Array, Object, Map, Range
5. [Control Flow](#control-flow)
6. [Functions](#functions) — Default Parameters, Variadic, Inner Functions, Lambdas, Async/Await, spawn
7. [Objects with Methods](#objects-with-methods)
8. [Enums](#enums)
9. [Built-in Functions](#built-in-functions)
10. [Standard Libraries](#standard-libraries)
11. [Example Program](#example-program)

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
import <lib>;                                   // Standard library (global scope)
import <lib> as <alias>;                        // Standard library under alias
import <lib>: <fn1>, <fn2>;                     // Selective import (global scope)
import <lib>: <fn1>, <fn2> as <alias>;          // Selective import under alias
import module "./path/to/file.mira";            // File import (global scope)
import module "./path/to/file.mira" as <alias>; // File import under alias
import native "./path/to/lib.jar" as <alias>;   // Native JAR extension (alias required)
```

When a lib is imported without an alias, all its functions are available globally. When imported with an alias, functions are accessed via `<alias>.<function>(...)`.

If two libs imported without an alias define a function with the same name, a conflict error is thrown. Use aliases to resolve it:

```
import string;            // ok
import collection as col; // avoids conflict with 'indexOf'

trim($text);
col.findIndex($list, "x");
```

### Native JAR Extensions

`import native` loads an external Java JAR at runtime. The JAR must implement the `com.mira.lib.Lib` interface and register itself via the Java `ServiceLoader` mechanism (`META-INF/services/com.mira.lib.Lib`).

An alias is always required — native imports never pollute the global scope.

```
import native "./extensions/mylib.jar" as ext;

ext.greet("world");
ext.compute(42);
```

The path is resolved relative to the importing file. Absolute paths are also accepted.

**JAR structure required:**

```
mylib.jar
├── META-INF/services/com.mira.lib.Lib   ← fully-qualified class name
└── com/example/MyLib.class              ← implements Lib
```

**Errors:**

| Error | Cause |
| ----- | ----- |
| `E222 NativeLibNotFoundError` | JAR file does not exist at the given path |
| `E223 NativeLibNoImplementationError` | JAR has no `META-INF/services/com.mira.lib.Lib` entry |
| `E224 NativeLibLoadError` | JAR is invalid or incompatible with the interpreter |

---

## Values

### Variable Declaration

```
var <name>;                  // Uninitialized (implicitly null)
var <name> : <expression>;   // With initial value
const <name> : <expression>; // Immutable
```

### Destructuring

Unpacks a list or array into multiple variables in one statement:

```
var (<name1>, <name2>, ...) : <expression>;
```

Example:

```
var t : {10, 20, 30};
var (a, b, c) : $t;
print($a "\n");   // => 10
print($b "\n");   // => 20
print($c "\n");   // => 30
```

Works with arrays too:

```
var (x, y) : {1, 2};
var (p, q) : [3, 4];
```

If there are fewer names than elements, the extra elements are ignored. If there are more names than elements, the extra variables are set to `null`.

### Variable Access & Assignment

Variables are accessed with a `$` prefix:

```
$<name>
$<obj>.<field>
$<obj>.<nested>.<field>
```

Assignment:

```
$<name> : <expression>;
$<obj>.<field> : <expression>;
```

### Compound Assignment

```
$<name> += <expression>;
$<name> -= <expression>;
$<name> *= <expression>;
$<name> /= <expression>;
$<name> %= <expression>;
$<name> **= <expression>;
$<name> \%= <expression>;
$<name> &= <expression>;
$<name> |= <expression>;
$<name> ^= <expression>;
```

### Literals

| Type    | Example         |
| ------- | --------------- |
| Number  | `10`, `3.14`    |
| String  | `"hello world"` |
| Boolean | `true`, `false` |
| Null    | `null`          |

String concatenation is done by placing values side by side:

```
"hello " $name "\n"
```

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

| Category         | Operators                        |
| ---------------- | -------------------------------- |
| Arithmetic       | `+`, `-`, `*`, `/`, `%`, `**`, `\%` |
| Comparison       | `<`, `>`, `<=`, `>=`, `==`, `!=` |
| Logical          | `&&`, `\|\|`, `!`                |
| Bitwise          | `&`, `\|`, `^`, `~`, `<<`, `>>`  |
| Postfix          | `++`, `--`                       |
| Ternary          | `? :`                            |
| Null-Coalescing  | `??`                             |
| Optional Chaining | `?.`                            |

`**` is the power/exponentiation operator. It has higher precedence than `*`, `/`, and `%`:

```
eval(10 ** 2)      // => 100
eval(2 ** 10)      // => 1024
eval(9 ** 0.5)     // => 3.0  (square root)
eval(2 * 3 ** 2)   // => 18   (3**2 first, then * 2)
```

`\%` is the floor division operator — divides and rounds down to the nearest integer:

```
eval(7 \% 2)       // => 3
eval(9 \% 4)       // => 2
eval(7.5 \% 3.0)   // => 2.0
```

Arithmetic must be wrapped in `eval()`:

```
eval($a + $b)
eval($x * 2)
```

Comparisons can be used directly in conditions:

```
$i < 5
$x > 3 && $y == 0
```

### Ternary Operator

Evaluates a condition and returns one of two values:

```
<condition> ? <then> : <else>
```

Example:

```
var label : $score >= 50 ? "pass" : "fail";
$x > 0 ? "positive" : "negative"
```

Ternaries can be nested:

```
$x > 10 ? "high" : ($x > 5 ? "mid" : "low")
```

### Pipe Operator

Passes the left-hand value as the first argument to the right-hand call:

```
$x |> trim()             // equivalent to trim($x)
$x |> add(1)             // equivalent to add($x, 1)
```

Pipes can be chained left-to-right:

```
$input |> trim() |> upper()
```

### Null-Coalescing Operator

Returns the left-hand value if it is not `null`, otherwise evaluates and returns the right-hand value:

```
$x ?? "default"
$config ?? newMap()
```

Chains left-to-right:

```
$a ?? $b ?? "fallback"
```

### Optional Chaining

Accesses a field on an object, but returns `null` instead of throwing when the object is `null`:

```
$obj?.field
```

Works in chains — if any step is `null`, the whole expression short-circuits to `null`:

```
$user?.address?.city
```

Combine with `??` to provide a fallback:

```
$user?.name ?? "anonymous"
```

### Grouping

Parentheses with a single expression group for precedence:

```
(($val1 + $val2) + 1)
eval(($a + $b) * $c)
```

---

## Data Structures

### List

Ordered, mutable, dynamic-size collection using curly braces:

```
var x : {10, 20, 30};
$x[0];                   // Index access
$x[1] : 99;              // Mutate element
```

### Array

Ordered, mutable, fixed-size collection using square brackets:

```
var x : [10, 20, 30];
$x[0];                   // Index access
$x[1] : 99;              // Mutate element (allowed)
```

Arrays cannot grow or shrink — `push` and `pop` only work on lists.

### Object

Inline struct with named fields declared as `var`:

```
var obj : {
    var x : 0;
    var name : "hello";
};

$obj.x;
$obj.name;
$obj.x : 42;             // Field assignment
```

Objects can be nested:

```
var wrapper : {
    var inner : {
        var a : 0;
    };
};
$wrapper.inner.a;
```

Objects can also contain methods — see [Objects with Methods](#objects-with-methods).

### Map

Key-value store using curly braces with `"key": value` pairs:

```
var m : {"name": "Alice", "score": 42};
```

Access and assignment use bracket notation with string keys:

```
$m["name"];              // => "Alice"
$m["name"] : "Bob";      // reassign
```

Maps are mutable. An empty map is created with `newMap()` from the `map` library.

### Range

Used in loops, exclusive end:

```
<0..5>              // 0, 1, 2, 3, 4
<0..length($x)>
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

Range-based for:

```
for (var <name> in <range>) {
    <body>
}
```

### Foreach

Iterates over a list, array, string, or range:

```
foreach (var <name> in <collection>) {
    <body>
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
switch ($x) {
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
    return switch($n) {
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
var label : switch($code) {
    case (200) -> "ok"
    case (404) -> "not found"
    default    -> "error"
};
```

Usage with enums:

```
var dir : Direction.EAST;
var label : switch($dir) {
    case (Direction.NORTH) -> "N"
    case (Direction.SOUTH) -> "S"
    case (Direction.EAST)  -> "E"
    case (Direction.WEST)  -> "W"
};
```

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
    print($e "\n");
} finally {
    print("always runs\n");
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
    print($greeting " " $name "\n");
}

greet("World");           // => Hello World
greet("World", "Hi");     // => Hi World
```

Default parameters must come after required parameters. Works in lambdas too:

```
var add : fn(x, step : 1) { return eval($x + $step); };
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
    foreach (var x in $args) {
        $total : eval($total + $x);
    }
    return $total;
}

sum(1, 2, 3)    // => 6
sum()           // => 0  ($args is an empty list)
```

Mixed (fixed + variadic):

```
fn log(prefix, ...args) {
    print($prefix ": ");
    foreach (var a in $args) { print($a " "); }
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
        return eval($base + $x);
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
        return $x > $threshold;
    }
    foreach (var item in $data) {
        if (isValid($item)) {
            print($item "\n");
        }
    }
}
```

For a local-only helper that should not leak into global scope, use a lambda stored in a `var` instead:

```
fn process(data, threshold) {
    var isValid : fn(x) { return $x > $threshold; };
    foreach (var item in $data) {
        if (isValid($item)) {
            print($item "\n");
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
var double : fn(x) { return eval($x * 2); };
eval(double(5));    // => 10
```

As an argument:

```
fn apply(f, x) {
    return f($x);
}

eval(apply(fn(n) { return eval($n * $n); }, 3));   // => 9
```

Closures — lambdas capture variables from their outer scope:

```
var factor : 3;
var scale : fn(x) { return eval($x * $factor); };
eval(scale(5));    // => 15
```

Lambdas support variadic parameters too:

```
var join : fn(sep, ...parts) { return join($parts, $sep); };
```

### typeof

`typeof` is a keyword operator that returns a string describing the runtime type of any value. It has the same precedence as a unary prefix operator.

```
typeof <expression>
```

**Return values:**

| Value | Result |
|---|---|
| Integer or float | `"number"` |
| String | `"string"` |
| Boolean | `"bool"` |
| `null` | `"null"` |
| List `{...}` | `"list"` |
| Array `[...]` | `"array"` |
| Map | `"map"` |
| Function or lambda | `"fn"` |
| Promise | `"promise"` |
| Object | `"object"` |

```
typeof 42;          // "number"
typeof "hello";     // "string"
typeof true;        // "bool"
typeof null;        // "null"
```

For variables, `typeof` evaluates the variable and inspects the stored value:

```
var x : 99;
typeof $x;          // "number"

var l : {1, 2, 3};
typeof $l;          // "list"
```

`typeof` can be used in conditions and switch expressions:

```
var x : 42;
typeof $x == "number" ? "yes" : "no";   // "yes"
```

```
var result : switch(typeof $x) {
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
    var response : httpGet($url);
    return $response;
}

var data : await fetchData("https://example.com/api");
print($data "\n");
```

Multiple async calls can be started before awaiting, so they run in parallel:

```
async fn slow(n) {
    sleep(eval($n * 100));
    return $n;
}

var p1 : slow(3);
var p2 : slow(1);
var p3 : slow(2);

print(await $p1 "\n");   // => 3
print(await $p2 "\n");   // => 1
print(await $p3 "\n");   // => 2
```

Async lambdas work the same way:

```
var fetch : async fn(url) { return httpGet($url); };
var result : await fetch("https://example.com");
```

**Scoping:** async functions share the global environment with the caller. Each async call runs on a separate interpreter instance, so local variables are isolated.

**Error handling:** if an async function throws, the exception is re-thrown at the `await` site and can be caught normally:

```
try {
    var result : await riskyOp();
} catch(e) {
    print("failed: " $e "\n");
}
```

### spawn

`spawn` runs any callable (lambda or function reference) in a background thread and returns a `Promise`. This is the low-level building block for parallelism — use it when you need to run arbitrary code concurrently without declaring an `async fn`.

```
var handle : spawn(fn() { <body> });
var result : await($handle);
```

Example — parallel heavy computations:

```
fn heavy(n) {
    var s : 0;
    for (var i : 0; $i < $n; $i++) { $s += $i; }
    return $s;
}

var h1 : spawn(fn() { return heavy(1000000); });
var h2 : spawn(fn() { return heavy(2000000); });

print(await($h1) "\n");
print(await($h2) "\n");
```

Both spawned tasks run in parallel on the common thread pool. `await` blocks only when you actually need the result.

Error propagation works the same as with `async fn`:

```
var h : spawn(fn() { throw "oops"; });
try {
    await($h);
} catch(e) {
    print("caught: " $e "\n");
}
```

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
$<name>.<method>(<args>)
```

### Field Access Inside Methods

Fields are accessible directly by name inside methods:

```
var counter : {
    var count : 0;
    fn increment() {
        $count += 1;
    }
    fn get() {
        return $count;
    }
};

$counter.increment();
$counter.increment();
$counter.get();          // => 2
```

### `this` Reference

`$this` is always available inside methods and refers to the object itself:

```
var obj : {
    var value : "hello";
    fn get() {
        return $this.value;
    }
};

$obj.get();              // => "hello"
```

### Method-only Objects

Objects can consist of only methods without any fields:

```
var math : {
    fn add(a, b) { return eval($a + $b); }
    fn square(x) { return eval($x * $x); }
};

$math.add(3, 4);         // => 7
$math.square(5);         // => 25
```

### Optional Chaining

Method calls support optional chaining — returns `null` if the object is `null`:

```
$obj?.method()
$obj?.method(arg)
```

### Methods with Default Parameters

Methods support the same default parameter syntax as regular functions:

```
var greeter : {
    fn greet(name, greeting : "Hello") {
        return $greeting " " $name;
    }
};

$greeter.greet("World");          // => "Hello World"
$greeter.greet("World", "Hi");    // => "Hi World"
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
if ($dir == Direction.SOUTH) {
    print("heading south\n");
}
```

Usage with `switch`:

```
var code : Status.NOT_FOUND;
switch ($code) {
    case (200) -> print("ok\n")
    case (404) -> print("not found\n")
    default    -> print("error\n")
}
```

Or as a switch expression:

```
var message : switch($code) {
    case (200) -> "ok"
    case (404) -> "not found"
    default    -> "error"
};
```

---

## Built-in Functions

Always available without any import.

| Function                    | Parameters             | Description                                               |
| --------------------------- | ---------------------- | --------------------------------------------------------- |
| `print(<value>)`            | Any value              | Prints the value to stdout without a newline              |
| `scan()`                    | —                      | Reads a line from stdin and returns it as a string        |
| `eval(<expr>)`              | Arithmetic expression  | Evaluates an arithmetic expression and returns the result |
| `exec(<code>)`              | String                 | Parses and executes a string of Mira code at runtime      |
| `length(<value>)`           | String, List, or Array | Returns the number of characters / elements          |
| `exit(<code>)`              | Number                 | Exits the program with the given exit code                |
| `assert(<cond>)`            | Boolean expression     | Throws a runtime error if the condition is false          |
| `assert(<cond>, <message>)` | Boolean, String        | Throws with a custom message if condition is false        |

---

## Standard Libraries

### `string`

| Function                    | Description                              |
| --------------------------- | ---------------------------------------- |
| `charAt(str, index)`        | Returns the character at the given index |
| `indexOf(str, char)`        | Returns the first index of a character   |
| `trim(str)`                 | Removes leading and trailing whitespace  |
| `split(str, delimiter)`     | Splits string into an array              |
| `substr(str, start, end)`   | Returns a substring                      |
| `strEqual(str1, str2)`      | Returns true if both strings are equal   |
| `replace(str, from, to)`    | Replaces all occurrences of a character  |

### `collection`

Works with lists and arrays unless noted otherwise.

| Function                    | Description                                               |
| --------------------------- | --------------------------------------------------------- |
| `size(col)`                 | Returns the number of elements                            |
| `push(list, value)`         | Appends a value to the end (mutates) — lists only         |
| `pop(list)`                 | Removes the last element (mutates) — lists only           |
| `remove(list, index)`       | Removes the element at the given index (mutates) — lists only |
| `first(col)`                | Returns the first element                                 |
| `last(col)`                 | Returns the last element                                  |
| `contains(col, value)`      | Returns true if the value is in the collection            |
| `findIndex(col, value)`     | Returns the index of a value, or `-1`                     |
| `slice(col, from, to)`      | Returns a sub-list                                        |
| `reverse(col)`              | Returns a reversed copy as a list                         |
| `concat(col1, col2)`        | Concatenates two collections into a new list              |
| `flatten(col)`              | Flattens one level of nested lists/arrays                 |
| `join(col, separator)`      | Joins elements into a string                              |
| `newList()`                 | Creates an empty mutable list                             |

### `map`

| Function                  | Description                                      |
| ------------------------- | ------------------------------------------------ |
| `newMap()`                | Creates an empty mutable map                     |
| `mapSize(map)`            | Returns the number of entries                    |
| `mapHas(map, key)`        | Returns true if the key exists                   |
| `mapRemove(map, key)`     | Removes the entry and returns the map            |
| `mapKeys(map)`            | Returns a list of all keys                       |
| `mapValues(map)`          | Returns a list of all values                     |

### `math`

Constants: `pi`, `e`, `inf`, `nan`

| Function              | Description                        |
| --------------------- | ---------------------------------- |
| `pow(base, exp)`      | Exponentiation                     |
| `sqrt(x)`             | Square root                        |
| `cbrt(x)`             | Cube root                          |
| `abs(x)`              | Absolute value                     |
| `round(x)`            | Round to nearest integer           |
| `floor(x)`            | Round down                         |
| `ceil(x)`             | Round up                           |
| `min(a, b)`           | Minimum of two values              |
| `max(a, b)`           | Maximum of two values              |
| `clamp(x, min, max)`  | Clamp value to range               |
| `sign(x)`             | Sign: `-1`, `0`, or `1`            |
| `log(x)`              | Natural logarithm                  |
| `log10(x)`            | Base-10 logarithm                  |
| `log2(x)`             | Base-2 logarithm                   |
| `sin(x)`              | Sine (radians)                     |
| `cos(x)`              | Cosine (radians)                   |
| `tan(x)`              | Tangent (radians)                  |
| `asin(x)`             | Arc sine                           |
| `acos(x)`             | Arc cosine                         |
| `atan(x)`             | Arc tangent                        |
| `atan2(y, x)`         | Arc tangent of y/x                 |
| `toRad(deg)`          | Degrees to radians                 |
| `toDeg(rad)`          | Radians to degrees                 |
| `rand()`              | Random float in `[0, 1)`           |
| `randInt(min, max)`   | Random integer in `[min, max]`     |
| `isNaN(x)`            | True if value is NaN               |
| `isInf(x)`            | True if value is infinite          |

### `io`

| Function                    | Description                                               |
| --------------------------- | --------------------------------------------------------- |
| `readFile(path)`            | Reads a file and returns its content as a string          |
| `writeFile(path, content)`  | Writes a string to a file, creating directories if needed |

### `net`

| Function                           | Description                              |
| ---------------------------------- | ---------------------------------------- |
| `httpGet(url)`                     | Sends a GET request, returns body        |
| `httpPost(url, body, contentType)` | Sends a POST request, returns body       |
| `httpStatus(url)`                  | Returns the HTTP status code             |
| `httpHeader(url, header)`          | Returns a response header value          |
| `httpDownload(url, path)`          | Downloads a file to the given path       |

### `dateTime`

| Function                | Description                                        |
| ----------------------- | -------------------------------------------------- |
| `now()`                 | Current date-time as ISO string                    |
| `timestamp()`           | Current Unix timestamp in seconds                  |
| `timestampMs()`         | Current Unix timestamp in milliseconds             |
| `dateFormat(date, fmt)` | Formats a date string with a pattern               |
| `year()`                | Current year                                       |
| `month()`               | Current month (1–12)                               |
| `day()`                 | Current day of month                               |
| `hour()`                | Current hour (0–23)                                |
| `minute()`              | Current minute                                     |
| `second()`              | Current second                                     |
| `dayOfWeek()`           | Day name e.g. `"MONDAY"`                           |
| `dayOfYear()`           | Day of year (1–366)                                |
| `secondsSince(date)`    | Seconds elapsed since the given date string        |
| `fromEpoch(seconds)`    | Converts a Unix timestamp (seconds) to date string |

### `json`

| Function                        | Description                                          |
| ------------------------------- | ---------------------------------------------------- |
| `jsonGet(json, key)`            | Gets a scalar value by key                           |
| `jsonHas(json, key)`            | Returns true if the key exists                       |
| `jsonArray(json, key)`          | Returns a top-level array as a list                  |
| `jsonNested(json, parent, key)` | Returns a nested array by parent key and array key   |
| `jsonBuild(keys, values)`       | Builds a JSON string from two lists                  |
| `jsonFormat(json)`              | Pretty-prints a JSON string                          |
| `jsonIndexOf(list, value)`      | Returns the index of a value in a JSON list, or `-1` |

### `regex`

| Function                             | Description                                   |
| ------------------------------------ | --------------------------------------------- |
| `matches(input, pattern)`            | True if the whole string matches the pattern  |
| `contains(input, pattern)`           | True if the pattern is found anywhere         |
| `findFirst(input, pattern)`          | Returns the first match, or `""`              |
| `findAll(input, pattern)`            | Returns all matches as a list                 |
| `replaceAll(input, pattern, repl)`   | Replaces all matches                          |
| `replaceFirst(input, pattern, repl)` | Replaces the first match                      |
| `split(input, pattern)`              | Splits by regex pattern into a list           |
| `capture(input, pattern)`            | Returns capture groups of the first match     |
| `countMatches(input, pattern)`       | Returns the number of matches                 |

### `shell`

| Function            | Description                                     |
| ------------------- | ----------------------------------------------- |
| `execute(cmd)`      | Runs a shell command and returns stdout         |
| `executeCode(cmd)`  | Runs a shell command and returns the exit code  |
| `getenv(name)`      | Returns an environment variable value           |
| `hasenv(name)`      | Returns true if the environment variable exists |
| `osName()`          | Returns the OS name                             |
| `isWindows()`       | True if running on Windows                      |
| `isLinux()`         | True if running on Linux                        |
| `isMac()`           | True if running on macOS                        |
| `cwd()`             | Current working directory                       |
| `username()`        | Current OS username                             |
| `homedir()`         | Home directory path                             |

### `process`

| Function                  | Description                                          |
| ------------------------- | ---------------------------------------------------- |
| `processStart(cmd)`       | Starts a background process, returns an ID           |
| `processAlive(id)`        | True if the process is still running                 |
| `processWait(id)`         | Waits for the process to finish, returns exit code   |
| `processKill(id)`         | Terminates the process                               |
| `processOutput(id)`       | Returns buffered stdout of the process               |
| `processExitCode(id)`     | Returns the exit code of a finished process          |
| `pid()`                   | Returns the PID of the current process               |
| `listProcesses()`         | Returns a list of all running PIDs                   |
| `processInfo(pid)`        | Returns the command of a process by PID              |
| `sleep(ms)`               | Pauses execution for the given number of milliseconds|

---

## Example Program

```
module main;

fn fibonacci(n) {
    if ($n <= 1) {
        return $n;
    } else {
        return fibonacci(eval($n - 2)) + fibonacci(eval($n - 1));
    }
    return 0;
}

fn main() {
    var result : 0;

    for (var i : 0; $i < 10; $i : eval($i + 1)) {
        $result : eval($result + fibonacci($i));
    }

    print("Sum: " $result "\n");
}
```
