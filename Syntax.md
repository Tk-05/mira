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
var <name>;                  // Uninitialized
var <name> : <expression>;   // With initial value
```

### Assignment

```
$<name> : <expression>;
$<obj>.<field> : <expression>;
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
ret;                   // Return nothing
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

## Lambdas (Anonymous Functions)

Lambdas sind namenlose Funktionen, die als Werte gespeichert und weitergegeben werden können.

### Syntax

```
fn(<param1>, <param2>) {
    <body>
}
```

### Zuweisung an Variable

```
var double : fn(x) { ret(eval($x * 2)); };
eval(double(5));    // => 10
```

### Als Argument übergeben

```
fn apply(f, x) {
    ret(f($x));
}

eval(apply(fn(n) { ret(eval($n * $n)); }, 3));   // => 9
```

Über eine Variable:

```
var multiply : fn(a, b) { ret(eval($a * $b)); };
eval(apply($multiply, 4));
```

### Closure: Zugriff auf äußere Variablen

```
var factor : 3;
var scale : fn(x) { ret(eval($x * $factor)); };
eval(scale(5));    // => 15
```

### Als Konstante

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

### Break

```
break();
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

### Einzeilig

```
// This is a line comment
var x : 10; // inline comment
```

### Mehrzeilig

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
