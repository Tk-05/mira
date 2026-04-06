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
| Arithmetic | `+`, `-`, `*`, `/`               |
| Comparison | `<`, `>`, `<=`, `>=`, `==`, `!=` |
| Logical    | `&&`, `\|\|`, `!`                |
| Postfix    | `++`, `--`                       |

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

### Return

```
ret();                 // Return nothing
ret(<expression>);     // Return a value
```

### Call

```
<name>(<arg1>, <arg2>)
```

### Module-qualified call

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
import <module-name>;                           // Standard library module
import "./path/to/file.mira" as <alias>;        // File import with alias
import module "./path/to/file.mira";            // File import without alias
import module "./path/to/file.mira" as <alias>;
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

## Internal Functions

Always available without any import.

| Function          | Parameters             | Description                                               |
| ----------------- | ---------------------- | --------------------------------------------------------- |
| `eval(<expr>)`    | Arithmetic expression  | Evaluates an arithmetic expression and returns the result |
| `print(<value>)`  | Any value              | Prints the value to stdout without a newline              |
| `exec(<code>)`    | String                 | Parses and executes a string of Mira code at runtime      |
| `length(<value>)` | String, List, or Tuple | Returns the number of characters / elements               |
| `exit(<code>)`    | Number                 | Exits the program with the given exit code                |

---

## Example Program

```mira
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
