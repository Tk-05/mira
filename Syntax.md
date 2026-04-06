# Mira Language Syntax Reference

## Module System

Every file declares its module name at the top:

```
module <name>;
```

### Imports

```
import <module-name>;                        // Standard library module
import "./path/to/file.mira" as <alias>;     // File import with alias
import module "./path/to/file.mira";         // File import without alias
import module "./path/to/file.mira" as <alias>;
```

---

## Variables

### Declaration

```
var <name>;                  // Uninitialized (implicitly null)
var <name> : <expression>;   // With initial value
var <name> : null;           // Explicit null
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

### Access

Variables are accessed with a `$` prefix:

```
$<name>
$<obj>.<field>
$<obj>.<nested>.<field>
```

---

## Literals

| Type    | Example                   |
|---------|---------------------------|
| Number  | `10`, `3.14`              |
| String  | `"hello world"`           |
| Boolean | `true`, `false`           |
| Null    | `null`                    |

String concatenation is done by placing values side by side:

```
"hello " $name "\n"
```

---

## Expressions

### Arithmetic / Comparison

Operators: `+`, `-`, `*`, `/`, `<`, `>`, `<=`, `>=`, `==`, `!=`, `&&`, `||`

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

## Functions

### Declaration

```
fn <name>(<param1>, <param2>) {
    <body>
}
```

### Return

```
ret()                  // Return nothing
ret(<expression>)      // Return a value
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

## Lambdas (Anonymous Functions)

Lambdas are nameless functions that can be stored as values and passed around.

### Syntax

```
fn(<param1>, <param2>) {
    <body>
}
```

### Assignment to a variable

```
var double : fn(x) { ret(eval($x * 2)); };
eval(double(5));    // => 10
```

### Passing as an argument

```
fn apply(f, x) {
    ret(f($x));
}

eval(apply(fn(n) { ret(eval($n * $n)); }, 3));   // => 9
```

Via a variable:

```
var multiply : fn(a, b) { ret(eval($a * $b)); };
eval(apply($multiply, 4));
```

### Closure: accessing outer variables

```
var factor : 3;
var scale : fn(x) { ret(eval($x * $factor)); };
eval(scale(5));    // => 15
```

### As a constant

```
const square : fn(x) { ret(eval($x * $x)); };
eval(square(4));   // => 16
```

---

## Control Flow

### If / Else

```
if (<condition>) {
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

Range-based for (becomes foreach):

```
for (var <name> in <range>) {
    <body>
}
```

### Foreach

```
foreach (var <name> in <collection>) {
    <body>
}
```

### Range Expression

```
<0..5>          // 0, 1, 2, 3, 4  (exclusive end)
<0..length($x)>
```

### Switch

Vergleicht einen Ausdruck gegen eine Liste von `case`-Werten. Nur der erste passende Block wird ausgeführt — kein `break` nötig. `default` ist optional und wird ausgeführt, wenn kein `case` passt.

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

Beispiel:

```
var x : 2;
switch ($x) {
    case (1) { print("eins\n"); }
    case (2) { print("zwei\n"); }
    default  { print("andere\n"); }
}
```

Switch-Ausdrücke können auch Strings oder berechnete Werte sein:

```
switch (eval($a + $b)) {
    case (10) { print("zehn\n"); }
    case (20) { print("zwanzig\n"); }
}
```

### Break

```
break();
```

### Continue

```
continue();
```

---

## Data Structures

### List (ordered, curly braces with values)

```
var x : {10, 20, 30};
```

### Tuple (square brackets)

```
var x : [10, 20, 30];
$x[0];                   // Index access
```

### Object (inline struct)

Fields are declared as `var` inside `{}`:

```
var obj : {
    var x : 0;
    var name : "hello";
};

$obj.x;
$obj.name;
$obj.x : 42;          // Field assignment
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

---

## Built-in Functions

| Function              | Description                              |
|-----------------------|------------------------------------------|
| `eval(<expr>)`        | Evaluate an arithmetic expression        |
| `print(<args...>)`    | Print to stdout                          |
| `ret(<value>)`        | Return a value from a function           |
| `break()`             | Break out of a loop                      |
| `length(<collection>)`| Get the length of a string or collection |
| `charAt(<i>, <str>)`  | Get character at index `i`              |
| `incr(<n>)`           | Increment a number by 1                  |
| `overwrite(<code>)`   | Overwrite a function definition at runtime |

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

## Example Program

```mira
module main;

import "./utils.mira" as utils;
import math;

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
        $result : $result + fibonacci($i);
    }

    print("Sum: " $result "\n");
}
```
