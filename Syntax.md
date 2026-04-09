# Mira Language Syntax Reference

## Variables

### Declaration

```
var <name>;                  // Uninitialized (implicitly null)
var <name> : <expression>;   // With initial value
const <name> : <expression>; // Immutable
```

### Access

Variables are accessed with a `$` prefix:

```
$<name>
$<obj>.<field>
$<obj>.<nested>.<field>
```

### Assignment

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
$<name> &= <expression>;
$<name> |= <expression>;
$<name> ^= <expression>;
```

---

## Literals

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

---

## Expressions

### Operators

| Category   | Operators                        |
| ---------- | -------------------------------- |
| Arithmetic | `+`, `-`, `*`, `/`, `%`          |
| Comparison | `<`, `>`, `<=`, `>=`, `==`, `!=` |
| Logical    | `&&`, `\|\|`, `!`                |
| Bitwise    | `&`, `\|`, `^`, `~`, `<<`, `>>`  |
| Postfix    | `++`, `--`                       |
| Ternary    | `? :`                            |

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

### Grouping

```
(($val1 + $val2) + 1)
```

---

## Data Structures

### List

Ordered collection using curly braces:

```
var x : {10, 20, 30};
```

### Tuple

Fixed-size sequence using square brackets:

```
var x : [10, 20, 30];
$x[0];                   // Index access
```

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

Iterates over a collection, tuple, string, or range:

```
foreach (var <name> in <collection>) {
    <body>
}
```

### Switch

Compares an expression against a list of `case` values. Only the first matching block is executed — no `break` needed. `default` is optional and runs when no `case` matches.

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

Example:

```
var x : 2;
switch ($x) {
    case (1) { print("one\n"); }
    case (2) { print("two\n"); }
    default  { print("other\n"); }
}
```

### Break / Continue

```
break();
continue();
```

### Try / Catch

Executes the `try` block and, if an exception is thrown, binds the value to the catch parameter and runs the `catch` block.

```
try {
    <body>
} catch(<param>) {
    <body>
}
```

### Throw

Throws a value as an exception. Can be caught by an enclosing `try / catch`.

```
throw(<expression>);
```

Example:

```
try {
    throw("something went wrong");
} catch(e) {
    print($e "\n");
}
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
    OK       : 200,
    NOT_FOUND : 404,
    ERROR    : 500
};

enum Color {
    RED   : "red",
    GREEN : "green",
    BLUE  : "blue"
};
```

Mixed enums (some explicit, some auto-indexed) are allowed. Auto-indexed variants count from their position regardless of any explicit values.

### Access

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

### Usage with Switch

```
var code : Status.NOT_FOUND;
switch ($code) {
    case (200) { print("ok\n"); }
    case (404) { print("not found\n"); }
    default    { print("error\n"); }
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
    ret($total);
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

Lambdas support variadic parameters too:

```
var join : fn(sep, ...parts) { ret(join($parts, $sep)); };
```

### Return

```
ret();                 // Return nothing
ret(<expression>);     // Return a value
```

### Call

```
<name>(<arg1>, <arg2>)
```

### Namespace-qualified call

Functions from aliased imports are called via dot notation:

```
<alias>.<name>(<arg>)
```

---

## Comments

### Single-line

```
// This is a line comment
var x : 10; // inline comment
```

### Multi-line

```
/* This is a
   multi-line comment */

var x : /* inline block */ 10;
```

---

## Module System

Every file declares its module name at the top:

```
module <name>;
```

### Imports

```
import <lib>;                                   // Standard library (global scope)
import <lib> as <alias>;                        // Standard library under alias
import module "./path/to/file.mira";            // File import (global scope)
import module "./path/to/file.mira" as <alias>; // File import under alias
```

When a lib is imported without an alias, all its functions are available globally. When imported with an alias, functions are accessed via `<alias>.<function>(...)`.

If two libs imported without an alias define a function with the same name, a conflict error is thrown. Use aliases to resolve it:

```
import string;            // ok
import collection as col; // avoids conflict with 'indexOf'

trim($text);
col.findIndex($list, "x");
```

---

## Lambdas

Lambdas are nameless functions that can be stored and passed around.

```
fn(<param1>, <param2>) {
    <body>
}
```

### As a variable

```
var double : fn(x) { ret(eval($x * 2)); };
eval(double(5));    // => 10
```

### As an argument

```
fn apply(f, x) {
    ret(f($x));
}

eval(apply(fn(n) { ret(eval($n * $n)); }, 3));   // => 9
```

### Closures

Lambdas capture variables from their outer scope:

```
var factor : 3;
var scale : fn(x) { ret(eval($x * $factor)); };
eval(scale(5));    // => 15
```

---

## Example Program
```
module main;

fn fibonacci(n) {
    if ($n <= 1) {
        ret($n);
    } else {
        ret(fibonacci(eval($n - 2)) + fibonacci(eval($n - 1)));
    }
    ret(0);
}

fn main() {
    var result : 0;

    for (var i : 0; $i < 10; $i : eval($i + 1)) {
        $result : eval($result + fibonacci($i));
    }

    print("Sum: " $result "\n");
}
```

---

## Internal Functions

Always available without any import.

| Function                    | Parameters             | Description                                               |
| --------------------------- | ---------------------- | --------------------------------------------------------- |
| `print(<value>)`            | Any value              | Prints the value to stdout without a newline              |
| `scan()`                    | —                      | Reads a line from stdin and returns it as a string        |
| `eval(<expr>)`              | Arithmetic expression  | Evaluates an arithmetic expression and returns the result |
| `exec(<code>)`              | String                 | Parses and executes a string of Mira code at runtime      |
| `length(<value>)`           | String, List, or Tuple | Returns the number of characters / elements               |
| `exit(<code>)`              | Number                 | Exits the program with the given exit code                |
| `assert(<cond>)`            | Boolean expression     | Throws a runtime error if the condition is false          |
| `assert(<cond>, <message>)` | Boolean, String        | Throws with a custom message if condition is false        |

---

## Standard Libraries

### `string`

| Function                        | Description                              |
| ------------------------------- | ---------------------------------------- |
| `charAt(str, index)`            | Returns the character at the given index |
| `indexOf(str, char)`            | Returns the first index of a character   |
| `trim(str)`                     | Removes leading and trailing whitespace  |
| `split(str, delimiter)`         | Splits string into an array              |
| `substr(str, start, end)`       | Returns a substring                      |
| `strEqual(str1, str2)`          | Returns true if both strings are equal   |
| `replace(str, from, to)`        | Replaces all occurrences of a character  |

### `collection`

| Function                    | Description                                          |
| --------------------------- | ---------------------------------------------------- |
| `size(list)`                | Returns the number of elements                       |
| `push(list, value)`         | Appends a value to the list (mutates)                |
| `pop(list)`                 | Removes the last element (mutates)                   |
| `first(list)`               | Returns the first element                            |
| `last(list)`                | Returns the last element                             |
| `contains(list, value)`     | Returns true if the value is in the collection       |
| `findIndex(list, value)`    | Returns the index of a value, or `-1`                |
| `slice(list, from, to)`     | Returns a sub-list                                   |
| `reverse(list)`             | Returns a reversed copy                              |
| `concat(list1, list2)`      | Concatenates two collections into a new list         |
| `flatten(list)`             | Flattens one level of nested lists                   |
| `join(list, separator)`     | Joins elements into a string                         |
| `newList()`                 | Creates an empty mutable list                        |
| `newTuple()`                | Creates an empty tuple                               |

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

### `net`

| Function                           | Description                              |
| ---------------------------------- | ---------------------------------------- |
| `httpGet(url)`                     | Sends a GET request, returns body        |
| `httpPost(url, body, contentType)` | Sends a POST request, returns body       |
| `httpStatus(url)`                  | Returns the HTTP status code             |
| `httpHeader(url, header)`          | Returns a response header value          |
| `httpDownload(url, path)`          | Downloads a file to the given path       |

### `dateTime`

| Function               | Description                                       |
| ---------------------- | ------------------------------------------------- |
| `now()`                | Current date-time as ISO string                   |
| `timestamp()`          | Current Unix timestamp in seconds                 |
| `timestampMs()`        | Current Unix timestamp in milliseconds            |
| `dateFormat(date, fmt)`| Formats a date string with a pattern              |
| `year()`               | Current year                                      |
| `month()`              | Current month (1–12)                              |
| `day()`                | Current day of month                              |
| `hour()`               | Current hour (0–23)                               |
| `minute()`             | Current minute                                    |
| `second()`             | Current second                                    |
| `dayOfWeek()`          | Day name e.g. `"MONDAY"`                          |
| `dayOfYear()`          | Day of year (1–366)                               |
| `secondsSince(date)`   | Seconds elapsed since the given date string       |
| `fromEpoch(seconds)`   | Converts a Unix timestamp (seconds) to date string|

### `json`

| Function                          | Description                                           |
| --------------------------------- | ----------------------------------------------------- |
| `jsonGet(json, key)`              | Gets a scalar value by key                            |
| `jsonHas(json, key)`              | Returns true if the key exists                        |
| `jsonArray(json, key)`            | Returns a top-level array as a list                   |
| `jsonNested(json, parent, key)`   | Returns a nested array by parent key and array key    |
| `jsonBuild(keys, values)`         | Builds a JSON string from two lists                   |
| `jsonFormat(json)`                | Pretty-prints a JSON string                           |
| `jsonIndexOf(list, value)`        | Returns the index of a value in a JSON list, or `-1`  |

### `io`

| Function          | Description                        |
| ----------------- | ---------------------------------- |
| `readFile(path)`  | Reads a file and returns its content as a string |

### `map`

| Function                  | Description                                      |
| ------------------------- | ------------------------------------------------ |
| `newMap()`                | Creates an empty mutable map                     |
| `mapSize(map)`            | Returns the number of entries                    |
| `mapHas(map, key)`        | Returns true if the key exists                   |
| `mapRemove(map, key)`     | Removes the entry and returns the map            |
| `mapKeys(map)`            | Returns a list of all keys                       |
| `mapValues(map)`          | Returns a list of all values                     |

### `regex`

| Function                           | Description                                      |
| ---------------------------------- | ------------------------------------------------ |
| `matches(input, pattern)`          | True if the whole string matches the pattern     |
| `contains(input, pattern)`         | True if the pattern is found anywhere            |
| `findFirst(input, pattern)`        | Returns the first match, or `""`                 |
| `findAll(input, pattern)`          | Returns all matches as a list                    |
| `replaceAll(input, pattern, repl)` | Replaces all matches                             |
| `replaceFirst(input, pattern, repl)`| Replaces the first match                        |
| `split(input, pattern)`            | Splits by regex pattern into a list              |
| `capture(input, pattern)`          | Returns capture groups of the first match        |
| `countMatches(input, pattern)`     | Returns the number of matches                    |

### `shell`

| Function            | Description                                       |
| ------------------- | ------------------------------------------------- |
| `execute(cmd)`      | Runs a shell command and returns stdout           |
| `executeCode(cmd)`  | Runs a shell command and returns the exit code    |
| `getenv(name)`      | Returns an environment variable value             |
| `hasenv(name)`      | Returns true if the environment variable exists   |
| `osName()`          | Returns the OS name                               |
| `isWindows()`       | True if running on Windows                        |
| `isLinux()`         | True if running on Linux                          |
| `isMac()`           | True if running on macOS                          |
| `cwd()`             | Current working directory                         |
| `username()`        | Current OS username                               |
| `homedir()`         | Home directory path                               |

### `process`

| Function                  | Description                                         |
| ------------------------- | --------------------------------------------------- |
| `processStart(cmd)`       | Starts a background process, returns an ID          |
| `processAlive(id)`        | True if the process is still running                |
| `processWait(id)`         | Waits for the process to finish, returns exit code  |
| `processKill(id)`         | Terminates the process                              |
| `processOutput(id)`       | Returns buffered stdout of the process              |
| `processExitCode(id)`     | Returns the exit code of a finished process         |
| `pid()`                   | Returns the PID of the current process              |
| `listProcesses()`         | Returns a list of all running PIDs                  |
| `processInfo(pid)`        | Returns the command of a process by PID             |
| `sleep(ms)`               | Pauses execution for the given number of milliseconds|

---