# Mira Grammar Reference

[← Back to index](../Documentation.md) · [Language Guide](language-guide.md) · [Standard Library Reference](standard-library.md) · [Build System](build-system.md) · [IDE Integration](ide-integration.md)

A formal reference for Mira's lexical and syntactic grammar, extracted directly
from the tokenizer and parser (`src/main/java/com/mira/lexer/Tokenizer.java`,
`src/main/java/com/mira/vocabulary/Vocabulary.java`,
`src/main/java/com/mira/parser/Parser.java`). If this document and the actual
parser ever disagree, the parser is right — file an issue rather than trusting
this file blindly.

This is a **syntax** reference. For semantics, runtime behavior, and worked
examples, see the [Language Guide](language-guide.md).

## Table of Contents

1. [Notation](#notation)
2. [Lexical Grammar](#lexical-grammar) — whitespace, comments, identifiers, literals, keywords, operators, delimiters
3. [Operator Precedence](#operator-precedence)
4. [Program Structure](#program-structure)
5. [Statement Grammar](#statement-grammar)
6. [Expression Grammar](#expression-grammar)
7. [Context-Sensitive Rules](#context-sensitive-rules) — the parts that aren't context-free

---

## Notation

EBNF-ish, read loosely:

| Syntax                      | Meaning                                                            |
| --------------------------- | ------------------------------------------------------------------ |
| `'text'`                    | a literal token (keyword, operator, or delimiter)                  |
| `Name`                      | a nonterminal, defined elsewhere in this doc                       |
| `A \| B`                    | `A` or `B`                                                         |
| `[ A ]`                     | `A` is optional (zero or one)                                      |
| `{ A }`                     | zero or more repetitions of `A`                                    |
| `( A B )`                   | grouping                                                           |
| `IDENT`, `NUMBER`, `STRING` | lexer-level tokens, defined in [Lexical Grammar](#lexical-grammar) |

Mira has **no context-free grammar in the strict sense** — several productions
depend on lookahead past what a single token of lookahead can resolve (see
[Context-Sensitive Rules](#context-sensitive-rules)). The EBNF below describes
the shape of each construct; where the _choice_ between two shapes depends on
lookahead rather than a leading keyword, that's called out explicitly.

---

## Lexical Grammar

### Whitespace and comments

Space, tab, `\r`, `\n` are insignificant and separate tokens. Two comment forms,
neither produces a token:

```
LineComment  ::= '//' <any char except '\n'>*
BlockComment ::= '/*' <any char>* '*/'
```

`BlockComment` **nests** — a `/*` inside a block comment opens another level,
and the comment only ends once every opened level has been closed by a
matching `*/`. `/* outer /* inner */ still outer */` is a single comment that
ends at the final `*/`.

### Identifiers

```
IDENT ::= IdentStart IdentPart*
IdentStart ::= <Unicode letter> | '_'
IdentPart  ::= <Unicode letter or digit> | '_'
```

Unicode letters are allowed (not restricted to ASCII). An identifier that
matches a [keyword](#keywords) is lexed as that keyword, not as `IDENT`.

### Number literals

```
NUMBER   ::= HexInt | Float | Int
HexInt   ::= ('0x' | '0X') HexDigitOrSep+
Int      ::= DigitOrSep+
Float    ::= DigitOrSep+ '.' [ DigitOrSep+ ] [ Exponent ] | DigitOrSep+ Exponent
Exponent ::= ('e' | 'E') [ '+' | '-' ] Digit+

DigitOrSep    ::= Digit | ( '_' Digit )     // '_' only between two digits
HexDigitOrSep ::= HexDigit | ( '_' HexDigit )
```

- Digit-group separators: a single `_` is allowed between two digits (decimal
  or hex), e.g. `1_000_000`, `0xFF_FF`. It's stripped before the literal is
  evaluated, so `1_000` and `1000` are identical. A leading, trailing, or
  doubled `_` (`_1`, `1_`, `1__000`) is **not** consumed as part of the
  number — the tokenizer just stops, so e.g. `100_` lexes as `NUMBER "100"`
  followed by `IDENT "_"`.
- Scientific notation is supported: `e`/`E`, an optional `+`/`-`, and at
  least one digit. `1e10`, `1.5e-3`, `1E+2` are all valid. If there's no
  digit after `e`/`E` (with an optional sign), the exponent isn't consumed at
  all — `1e` lexes as `NUMBER "1"` followed by `IDENT "e"`.
- `5.` (dot with no following digit) **does** parse as a float — equivalent
  to `5.0`. The tokenizer consumes a `.` after a digit run unless the next
  character is _also_ a `.` (which means it's actually the start of the `..`
  range separator or `...` variadic marker, not a decimal point — this is
  what keeps `0..5` from being corrupted into `NUMBER "0."` + `DELIMITER "."`
  - `NUMBER "5"`).
- `.5` is not a valid literal at all (a leading `.` never starts a number).
- No binary/octal literals, no numeric type suffixes (`1L`, `1.0f`).
- Sign is never part of the literal: `-5` is the unary `-` operator applied to
  `5`, always.
- `NUMBER` and `IDENT` are the _same token type_ at the lexer level
  (`EXPRESSION`); the distinction ("is this a number or a bareword") is made
  later, by the parser/interpreter checking whether the text starts with a
  digit. This is why numbers and barewords share so much grammar (see
  [Context-Sensitive Rules](#context-sensitive-rules)).

### String literals

```
STRING     ::= '"' StringChar* '"'
StringChar ::= <any char except '"' or unescaped '\'> | Escape
Escape     ::= '\n' | '\t' | '\r' | '\"' | '\\' | '\u' HexDigit HexDigit HexDigit HexDigit
```

- Recognized escapes: `\n`, `\t`, `\r`, `\"`, `\\`, and `\uXXXX` (exactly 4
  hex digits, decoded as a UTF-16 code unit — e.g. `"é"` is `é`).
- Any _other_ escape sequence (`\q`, `\0`, `\b`, …) is a **lex error**
  (`E003`, `InvalidEscapeSequenceError`) — Mira does not silently drop the
  backslash. A `\u` not followed by 4 valid hex digits is also an error.
- Unescaped newlines are allowed inside a normal `"…"` string — it can span
  multiple physical lines without error.
- An unterminated string (EOF before the closing `"`) is a lex error.
- **Adjacent string literals are spliced at parse time**, exactly like C:
  `"a" "b"` becomes a single string token `"ab"`. This is the only place two
  expressions can sit side by side with no operator between them — it works
  because both sides are unambiguously `STRING_LITERAL` tokens, so there is
  no precedence or juxtaposition-vs-binary-operator ambiguity to resolve.
  Joining a string with anything else (a variable, a call, an arithmetic
  expression) requires an explicit `+`; see
  [String Concatenation](#string-concatenation).

### Text blocks

```
TextBlock ::= '"""' [ '\n' ] TextBlockChar* '"""'
```

- If the character immediately after the opening `"""` is a newline, that one
  newline is stripped. That is the **only** trimming Mira text blocks do —
  unlike Java text blocks, there is **no common-leading-whitespace dedent**.
  Every other character, including indentation, is copied verbatim.
- No escape processing at all inside a text block — content is taken
  raw, character-for-character, until the closing `"""`.

### Boolean and null literals

`true`, `false`, `null` are lexically [keywords](#keywords) (not a distinct
literal token type), but the parser treats them as literal expressions
wherever an expression is expected.

### Keywords

The complete reserved-word set (37 words) — none of these can be used as an
identifier via `var`/`const`/struct-field names (see
[Context-Sensitive Rules](#context-sensitive-rules) for the exact strictness
rule, which differs by context):

```
var       return    fn        if        else      for
while     break     import    in        module    as
const     true      false     continue  null      switch
case      default   enum      try       catch     finally
throw     native    do        await     async     typeof
lock      pure      comptime  test      static_assert  pub
struct
```

### Operators

Longest-match tokenization (up to 3 characters). The complete set:

```
Arithmetic       +   -   *   /   %   **   \%
Increment/decr.  ++  --
Compound-assign  +:  -:  *:  /:  %:  **:  \%:  &:  |:  ^:
Comparison       ==  !=  <  >  <=  >=
Logical          &&  ||
Bitwise          &   |   ^   ~   <<  >>
Pipe             |>
Null/optional    ??  ?.
Misc             :   !   ?   ->
```

Notes:

- `**` is exponentiation, `\%` is floor division; `**:`/`\%:` are their
  compound-assign forms.
- **There is no plain `=` token anywhere in the grammar.** Assignment is
  spelled `:` (see [Statement Grammar → Assign](#assign)).
- There is no variable sigil. A bareword identifier is a variable reference
  wherever a value is expected, exactly like C — see
  [Context-Sensitive Rules](#context-sensitive-rules).
- `->` is used for both arrow-lambdas and switch-arm arrows.

### Delimiters

```
(   )   {   }   ;   ,   [   ]   .   ..   ...
```

`..` is the range operator (`start..end`, see [Range](#range)); `...` marks a
variadic parameter.

---

## Operator Precedence

From lowest to highest binding. All levels are **left-associative** in Mira's
Pratt parser (same-precedence operators group left-to-right).

| Level                      | Operators         | Associativity |
| -------------------------- | ----------------- | ------------- |
| 1 (lowest)                 | `\|>` `\|\|` `??` | left          |
| 2                          | `&&`              | left          |
| 3                          | `\|`              | left          |
| 4                          | `^`               | left          |
| 5                          | `&`               | left          |
| 6                          | `==` `!=`         | left          |
| 7                          | `<` `>` `<=` `>=` | left          |
| 8                          | `<<` `>>`         | left          |
| 9                          | `+` `-`           | left          |
| 10                         | `*` `/` `%` `\%`  | left          |
| 11 (highest of this table) | `**`              | left          |

Everything else binds **tighter** than every entry above, and is resolved
outside the binary-operator table entirely:

- Field access and optional chaining, `.` / `?.` — attach directly to the
  primary expression before precedence climbing ever runs.
- Prefix/postfix `++`, `--`, and prefix `!`, `-`, `+`, `~` — resolved in the
  primary/postfix layer.
- `await` and `typeof` — each wraps a single primary expression (not a full
  precedence-climbed expression), so `await foo() + 1` parses as
  `(await foo()) + 1`, and `await foo().bar()` awaits the whole call chain
  since postfix access/call resolution happens inside that inner primary
  parse.

Ternary `? :` binds **looser** than everything in the table — it's checked
before precedence climbing, only fires when nothing tighter claimed the
tokens first, and is right-associative for chained ternaries
(`a ? b : c ? d : e` reads as `a ? b : (c ? d : e)`).

The range operator `..` (see [Range](#range)) binds looser than every entry
in the table too, same as ternary — `1 - 1..2 * 3` reads as `(1 - 1)..(2 * 3)`.
It is checked only when nothing tighter has already claimed the tokens, and
does not chain: `a..b..c` is a syntax error rather than a nested range,
so parenthesize the second range if that's what's meant.

Compound-assign operators (`+:`, `-:`, …) never participate in expression
precedence at all — they only appear as the terminator of an
[assignment statement](#assign).

---

## Program Structure

```
Program ::= { Statement }
```

There is no dedicated top-level wrapper node — a program is just a sequence
of statements. By convention every file starts with a
[`module` declaration](#module-declaration), and [`import`](#import),
[`comptime`](#comptime-block), and [`test`](#test-call) statements are only legal
at this top level (`parsingDepth == 0`), never inside a function, block, or
loop body.

---

## Statement Grammar

A recurring shape: `if`, `for`, `while`, and `do…while` all accept
either a `{ }` block **or** a single bare statement as their body:

```
Body ::= Block | Statement
```

By contrast, `switch` block-arms, `try`/`catch`/`finally`, `lock`, `comptime`,
function/lambda bodies, and object/struct literal bodies always require an
explicit `{ }`.

### Variable declaration

```
VarDecl   ::= [ 'pub' ] 'var'   IDENT [ TypeAnnotation ] [ ':' Expression ] { ',' IDENT [ TypeAnnotation ] [ ':' Expression ] } ';'
ConstDecl ::= [ 'pub' ] 'const' IDENT ':' Expression     { ',' IDENT ':' Expression }     ';'

TypeAnnotation ::= ':' TypeExpr ':'
TypeExpr       ::= 'Fn' '(' [ TypeExpr { ',' TypeExpr } ] ')' [ '->' TypeExpr ] [ '?' ]
                  | NameToken [ '?' ]
```

- The `Fn(...)` form is a checked function type: each parameter's own type
  and, if given, the `-> TypeExpr` return type are matched against whatever
  value is bound to it. A bare `Fn` (no parens) is any callable, unchecked.
  The trailing `[ '?' ]` binds to whichever `TypeExpr` it immediately
  follows: with no return type, `Fn(Number)?` makes the function reference
  itself nullable; with a return type, `Fn(Number) -> Number?` instead makes
  the _return type_ nullable, not the function reference.

- `const` requires an initializer on every name; `var`'s initializer is
  optional, but only directly before `;`, `)`, `,`, or `in` — anything else
  there is a syntax error.
- Names use the _strict_ identifier check (see
  [Context-Sensitive Rules](#context-sensitive-rules)) — no keyword, including
  `true`/`false`/`null`, can be a variable name.
- `pub` is only legal at the true top level (not inside any function/block).
- Fields declared inside an [object](#object-literal) or
  [struct](#struct-literal-template) literal use this same production but can never
  carry `pub`.
- `TypeAnnotation` is optional and requires an initializer to follow — there
  is no "typed but uninitialized" `var` form. See
  [Type annotation disambiguation](#type-annotation-disambiguation) for how
  the parser tells `var x : Expr;` apart from `var x : Type : Expr;` without
  backtracking.

```mira
var x : 5, y : "a", z;
pub const PI : 3;
var age : Number : 30;
var maybe : Number? : null;
```

### Destructuring declaration

```
VarDestructure ::= 'var' '(' IDENT { ',' IDENT } ')' ':' Expression ';'
```

Note: this always uses `var` — a `const (...)` spelling is accepted by the
parser but the const-ness is silently dropped (not preserved on the node).

```mira
var (a, b) : pair;
```

### Function declaration

```
FuncDecl ::= [ 'pub' ] [ 'async' | 'pure' ] 'fn' NameToken '(' ParamList ')' [ '->' TypeExpr ] Block
```

- `pub` must come before `async`/`pure` if both are present.
- `async` and `pure` are mutually exclusive — `async pure fn` / `pure async fn`
  are both syntax errors.
- `NameToken` uses the _loose_ identifier check (see
  [Context-Sensitive Rules](#context-sensitive-rules)).
- The body braces are mandatory — there's no brace-less shorthand for a named
  function.
- The `-> TypeExpr` return-type annotation only applies to named `fn`
  declarations — lambdas and arrow lambdas don't accept one, since `->`
  there already introduces the lambda body (see
  [Type annotation disambiguation](#type-annotation-disambiguation)).

```mira
pub async fn fetch(url, timeout : 30) { return await http.get(url); }
fn add(a : Number, b : Number) -> Number { return a + b; }
```

See [Function/Lambda Details](#functionlambda-details) for `ParamList`.

### Type alias declaration

```
TypeAliasDecl ::= 'type' IDENT ':' TypeExpr ';'
```

```mira
type UserId : Number;
```

Syntactically legal anywhere a statement is legal, like `VarDecl` — but only
a `type` declaration at the true top level of a file is actually registered
and usable as a type name; one written inside a function or block currently
parses but has no effect (the static checker only scans top-level statements
for `TypeAliasDecl` nodes). `TypeExpr` is the same production used by
`TypeAnnotation` (see [Variable declaration](#variable-declaration)).

### Return

```
Return ::= 'return' [ Expression ] ';'
```

The expression is optional; omitting it is equivalent to `return 0.0;`
internally.

### Assign

```
Assign ::= IDENT { ( '.' FIELD ) | ( '[' Expression ']' ) } AssignOp Expression ';'
AssignOp ::= ':' | '+:' | '-:' | '*:' | '/:' | '%:' | '**:' | '\%:' | '&:' | '|:' | '^:'
```

- The field/index chain can mix `.field` and `[index]` arbitrarily (including
  the `{index}` spelling — see [index access](#index--access)).
- The field chain stops before a `.` that's immediately followed by `(` —
  that's a method call, not an assignment target.
- Compound forms desugar at parse time: `x +: 1;` becomes
  `x : x + 1;` internally.
- A leading bareword only starts an `Assign` statement when it is followed
  (after any field/index chain) by one of the assignment operators above —
  otherwise it's parsed as an ordinary expression statement. See
  [Context-Sensitive Rules](#context-sensitive-rules).

```mira
counter : 0;
obj.field : 5;
arr[0] : "x";
counter +: 1;
```

### If

```
If ::= 'if' '(' Expression ')' Body [ 'else' ( If | Body ) ]
```

`else if` is just `else` followed by a nested `If` — there's no dedicated
"elif" node.

### For

Three distinct forms, all spelled `for`:

```
For ::= 'for' '(' [ ForInit ] ';' [ Expression ] ';' [ Statement { Statement } ] ')' Body
      | 'for' '(' Range ')' Body
      | 'for' '(' 'var' IDENT 'in' ( Expression | Range ) ')' Body

ForInit ::= 'var' IDENT ':' Expression { ',' 'var' IDENT ':' Expression }
```

- The third form iterates: `IDENT` is bound to each element of a list, array,
  string, or range in turn. There is no separate `foreach` keyword — this is
  the only spelling for iteration-style loops.
- The second form (`for (Range)`) is sugar for the third form with an
  anonymous, unbound iterator — useful when only the number of iterations
  matters, not the value. Unlike the third form, the expression here must be
  a [range](#range) — the parser tells the two forms apart by trying to parse
  an expression when the next token is neither `var`, `;`, nor `)`, then
  rejecting it if it didn't turn out to be a range.
- `ForInit`'s comma-chaining requires each subsequent segment to start with
  its own `var` — this is what lets multiple declarations share one `for`
  header without colliding with a plain `VarDecl`'s own (different) comma
  handling.
- The post-clause accepts **zero or more statements with no separator
  required between them** — in practice this is almost always exactly one
  increment (`i++` or `i : i + 1`).
- All three clauses are independently optional: `for (;;) { ... }` is an
  infinite loop.

```mira
for (var i : 0, var j : 10; i < 10; i++) { ... }
for (0..10) { ... }
for (var i in 0..5) { ... }
for (var i in arr) { ... }
```

### While / do-while

```
While   ::= 'while' '(' Expression ')' Body
DoWhile ::= 'do' Body 'while' '(' Expression ')' ';'
```

Both are represented by the same node with a `doModifier` flag; note the
trailing `;` is required after `do…while(...)` but not after plain `while`.

### Break / continue

```
Break    ::= 'break' ';'
Continue ::= 'continue' ';'
```

No loop labels.

### Block

```
Block ::= '{' { Statement } '}'
```

### Switch (statement)

```
SwitchStmt ::= 'switch' '(' Expression ')' '{'
                   { 'case' '(' Expression ')' SwitchArm }
                   [ 'default' SwitchArm ]
               '}'

SwitchArm ::= '->' ArrowBody | Block
```

- Each arm independently picks the arrow form or the block form — a single
  `switch` can mix both.
- `ArrowBody` is a single statement/expression: an assignment, one of
  `var`/`const`/`return`/`break`/`continue`/`throw` (each still needs its own
  trailing `;`), a nested `if`/`while`/`for` (no extra `;`, since those
  self-terminate), or otherwise a single (non-juxtaposed) expression.
- The case value is one `Expression` — no comma-separated multi-value labels.

```mira
switch (status) {
    case (200) -> "OK";
    case (404) { throw NotFound("missing"); }
    default -> "unknown";
}
```

### Module declaration

```
ModuleDecl ::= 'module' NameToken ';'
```

Conventionally the first statement in a file; the parser itself doesn't
enforce that, but the static checker does.

### Import

Three kinds, all top-level only:

```
Import ::= 'import' 'native' STRING 'as' IDENT ';'
         | 'import' 'module' STRING [ '{' IDENT { ',' IDENT } '}' ] [ 'as' IDENT ] ';'
         | 'import' NameToken ( ':' IDENT { ',' IDENT } | '{' IDENT { ',' IDENT } '}' )? [ 'as' IDENT ] ';'
```

- `import native` requires the alias — it's a hard error to omit `as IDENT`.
- Stdlib imports support two interchangeable selective-import spellings,
  `import math: sin, cos;` and `import math { sin, cos };` — both populate the
  same selected-symbols list. The `module`-kind import only supports the
  brace spelling.

```mira
import math;
import math { sqrt, abs } as m;
import module "utils/strings.mira" as str;
import native "raylib.jar" as raylib;
```

### Enum declaration

```
EnumDecl ::= [ 'pub' ] 'enum' NameToken '{'
                 EnumMember { ',' EnumMember } [ ',' ]
             '}'

EnumMember ::= NameToken [ ':' Expression ]
```

- The value after `:` is a full `Expression`, evaluated once when the `enum`
  declaration runs — `RED : 1 + 2` evaluates `RED` to `3`, and the value can
  reference anything in scope at that point (a variable, a function call,
  …), not just a literal.
- Members without an explicit value get an auto-incrementing index, built as
  a numeric literal expression (so it evaluates to a `Number`, not a
  `String`) — the counter advances for _every_ member regardless of whether
  it had an explicit value.
- A trailing comma after the last member is allowed.

```mira
enum Color { RED, GREEN : "g", BLUE }   // RED=0, GREEN="g", BLUE=2
enum Calc  { A : 1 + 2 }                // A=3
```

### Try / catch / finally

```
TryCatch ::= 'try' Block { CatchClause } [ 'finally' Block ]
CatchClause ::= 'catch' [ '(' [ NameToken ] NameToken ')' ] Block
```

- `catch { }` (no parens) — no type filter, no bound name.
- `catch (e) { }` — one identifier binds the caught value, no type filter.
- `catch (Type e) { }` — first identifier is a type filter, second is the
  bound name.
- Zero or more `catch` clauses may chain; `finally` is optional; a bare
  `try { } finally { }` with no `catch` at all is legal.

```mira
try {
    risky();
} catch (IOError e) {
    log(e);
} catch (e) {
} finally {
    cleanup();
}
```

### Throw

```
Throw ::= 'throw' NameToken '(' [ Expression ] ')' ';'
```

At most one payload expression — no variadic throw arguments.

### Lock

```
Lock ::= 'lock' '(' Expression ')' Block
```

### Comptime block

```
ComptimeBlock ::= 'comptime' Block
```

Top-level only.

### Static assert

```
StaticAssert ::= 'static_assert' '(' Expression [ ',' Expression ] ')' ';'
```

Unlike `comptime`/`import`/`test`, this is **not** restricted to the top
level — it's legal inside function bodies too.

### Test call

```
TestCall ::= 'test' '(' Expression ',' Expression ')' ';'
```

Top-level only; exactly two arguments (name, test function), no variadics.

---

## Expression Grammar

Ordered roughly from "leaf" outward to the loosest-binding forms.

### Literals

```
Literal ::= NUMBER | STRING | TextBlock | 'true' | 'false' | 'null'
```

### Variable reference

```
VarRef ::= IDENT
```

A bareword `IDENT` that isn't a keyword is a variable reference, exactly
like C — see [Context-Sensitive Rules](#context-sensitive-rules). There is no
sigil and no "bareword is a string" fallback: every string value must come
from an actual `STRING` literal.

### Prefix and postfix `++`/`--`, unary operators

```
Prefix  ::= ( '++' | '--' | '!' | '-' | '+' | '~' ) Primary
Postfix ::= Primary ( '++' | '--' )
```

`++`/`--` work on any expression, not just variables — see the
[Language Guide's operators section](language-guide.md#operators) for what
happens when the operand isn't a variable/element/field (mutates if it can, otherwise just
computes `value ± 1`). Prefix and postfix are equivalent in Mira — there is no
C-style "postfix returns the old value" distinction.

### Array / list / map literals

```
ArrayLiteral ::= '[' [ Expression { ',' Expression } ] ']'
ListLiteral  ::= '{' [ Expression { ',' Expression } ] '}'
MapLiteral   ::= '{' STRING ':' Expression { ',' STRING ':' Expression } '}'
```

`[...]` is always an array. A bare `{...}` is a list, _unless_ the first
thing inside looks like `STRING :`, in which case it's parsed as a map — see
[Context-Sensitive Rules](#context-sensitive-rules) for the exact
disambiguation priority against object/struct literals.

### Object literal

```
ObjectLiteral ::= '{' { FieldOrMethod } '}'
FieldOrMethod ::= ( VarDecl | ConstDecl ) | FuncDecl
```

Chosen when the token right after `{` is `var`, `const`, or `fn`. Fields never
carry `pub`; methods declared here are never `async`/`pure`/`pub`.

```mira
var counter : { var n : 0; fn increment() { this.n +: 1; } };
```

### Struct literal (template)

```
StructLiteral ::= 'struct' '{' { FieldOrMethod } '}'
```

Same inner grammar as an object literal, distinguished by the leading
`struct` keyword. Produces a _template_ that [struct-init](#struct-init)
expressions instantiate.

### Struct init

```
StructInit ::= Expression '{' '}'
             | Expression '{' FIELD ':' Expression { ',' FIELD ':' Expression } '}'
```

The target expression is typically a variable referencing a struct template.
Field names use the strict identifier check (no keywords).

```mira
PointTemplate { x : 1, y : 2 }
```

### Index / access

```
AccessExpr ::= Expression ( '[' Expression ']' | '{' Expression '}' ) { ( '[' Expression ']' | '{' Expression '}' ) }
```

`[...]` and `{...}` are **interchangeable** as the index-access brackets, and
can be mixed within one chain (`arr[0]{1}` is valid, if unusual style).

### Field access and method calls

```
FieldAccess  ::= Expression ( '.' | '?.' ) FIELD
MethodCall   ::= Expression ( '.' | '?.' ) FIELD '(' [ Expression { ',' Expression } ] ')'
```

Chains arbitrarily: `a.b?.c.d()`. `?.` short-circuits to `null` if the
left-hand side is null/missing, instead of raising a field-access error.

### Calls

```
Call ::= Callee '(' [ Expression { ',' Expression } ] ')'
NamespaceCall ::= IDENT '.' IDENT '(' [ Expression { ',' Expression } ] ')'
```

`NamespaceCall` is its own node (used for `alias.function(...)` style calls
into an imported namespace) distinct from `FieldAccess` immediately followed
by a call.

### Binary expressions

```
Binary ::= Expression BinOp Expression
BinOp  ::= '+' | '-' | '*' | '/' | '%' | '\%' | '**'
         | '&' | '|' | '^' | '<<' | '>>'
         | '==' | '!=' | '<' | '>' | '<=' | '>='
         | '&&' | '||' | '|>' | '??'
```

Grouped and precedence-climbed per the [precedence table](#operator-precedence).

### Ternary

```
Ternary ::= Expression '?' Expression ':' Expression
```

Right-associative when chained (see [precedence](#operator-precedence)).

### Assignment expression

```
AssignExpr ::= IDENT ':' Expression
```

This is the _expression_-position form (usable as one item inside a larger
expression, e.g. inside a parenthesized group or argument list) — it only
ever targets a bare `name`, never a field/index chain. The richer
field/index-chain assignment target is [statement](#assign)-only.

### Range

```
Range ::= Expression '..' Expression
```

Usable as a general expression anywhere (`var r : 0..5;`, `print(1..10);`),
in addition to the `for (Range)` and `for (var x in Range)` forms. Always
exclusive of `end` (`0..5` produces `0, 1, 2, 3, 4`) and always steps by 1 —
there is no step syntax; iterate with a classic `for` loop instead if a
different step is needed.

`..` is not in the binary-operator [precedence table](#operator-precedence)
— it binds looser than everything in it (same tier as ternary `? :`), so
`1-1..2*3` reads as `(1-1)..(2*3)`, and it doesn't chain (`a..b..c` is a
syntax error, not a nested range).

```mira
for (var i in 0..10) { ... }
var r : 2*3..20;
```

### `await` / `typeof`

```
Await  ::= 'await' Primary
Typeof ::= 'typeof' Primary
```

Both bind tighter than every binary operator (see
[precedence](#operator-precedence)).

### Switch expression

```
SwitchExpr ::= 'switch' '(' Expression ')' '{'
                   { 'case' '(' Expression ')' '->' Expression }
                   [ 'default' '->' Expression ]
               '}'
```

Unlike the statement form, **only** the arrow spelling is accepted — a
`{ block }` arm is a syntax error here.

### Lambda

```
Lambda      ::= [ 'async' ] 'fn' '(' ParamList ')' Block
ArrowLambda ::= '(' ParamList ')' '->' ( Expression | Block )
```

`ArrowLambda` is never `async`. See
[Function/Lambda Details](#functionlambda-details).

### Exec block

```
ExecBlock ::= 'exec' [ 'isolated' ] Block
```

Only recognized as an `ExecBlock` when `exec` is immediately followed by `{`
or `isolated` — otherwise `exec` is just an ordinary bareword reference.

### String Concatenation

There is no implicit juxtaposition production — two expressions never sit
side by side with no operator between them (the one exception, splicing two
adjacent `STRING` literal tokens, is a lexer/parser-level rule, not a general
expression form; see [String literals](#string-literals)). Joining a string
with anything else is an ordinary `+` [binary expression](#binary-expressions):

```mira
"Hello " + name + "!"
```

`+` adds numbers, and falls back to stringifying and concatenating its
operands whenever either side isn't a number. This is also why arithmetic
never needs to be wrapped in `eval()` — `n - 1`, `x * 2`, `-x` are ordinary
expressions usable anywhere, including right next to a `+`-joined string,
with no parsing ambiguity to work around. `eval(<code>)` is reserved for its
one remaining job: running a runtime-constructed code _string_ — see
[Dynamic Code Execution](language-guide.md#dynamic-code-execution). For
formatted output, see the `format(pattern, ...args)` builtin in the
[Standard Library Reference](standard-library.md).

---

## Function/Lambda Details

```
ParamList ::= [ Param { ',' Param } [ ',' '...' IDENT ] | '...' IDENT ]
Param     ::= NameToken ( [ ':' TypeExpr ] ':' Expression | ':' TypeExpr | ε )
```

- A parameter's default value is a full `Expression` (it can itself be a
  juxtaposed/complex expression).
- A parameter can be typed with or without a default: `a : Number` (typed,
  no default) or `a : Number : 0` (typed, with default). A lone `: Expr`
  with no second colon is still a plain untyped default, exactly as before —
  see [Type annotation disambiguation](#type-annotation-disambiguation) for
  the one narrow case this changes (a parameter defaulted to a bare,
  unquoted identifier-shaped string).
- The variadic marker, if present, must be the **last** item, and there can
  be at most one. It never carries a type annotation.
- Arity for call-checking purposes: minimum arity = count of parameters
  without a default (or unbounded if variadic); maximum arity = total
  parameter count (or unbounded if variadic).

**Where each modifier is legal:**

| Position                            | `async`       | `pure`        | `pub`                             |
| ----------------------------------- | ------------- | ------------- | --------------------------------- |
| Top-level named `fn`                | yes           | yes           | yes (must precede `async`/`pure`) |
| `async`+`pure` together             | not supported | not supported | —                                 |
| `fn(...) {...}` lambda              | yes           | no            | no                                |
| `(...) -> ...` arrow lambda         | never         | no            | no                                |
| Method inside object/struct literal | no            | no            | no                                |

`pub` additionally requires true top-level position (`parsingDepth == 0`) —
never legal inside any function, block, loop, or literal body. It applies
only to `var`/`const`, `fn`, and `enum` declarations.

---

## Context-Sensitive Rules

Mira's grammar leans on lookahead and a few genuinely context-sensitive
rules. These recur throughout the parser rather than being one-off special
cases, so they're documented once here instead of repeated per production.

### A bareword is always a variable

There is no sigil and no "bareword is a string" fallback. A bareword
identifier is a variable reference wherever a value is expected — exactly
like C. Every string value must come from an actual `STRING` literal (`"…"`
or `"""…"""`). This is why:

- `x : 5` **is** assignment syntax: `x` is recognized as an assignment
  target whenever it's followed (after any `.field`/`[index]` chain) by an
  assignment operator (`:`, `+:`, …) — see [Assign](#assign).
- A name immediately followed by `(` is always a call by that name
  (`fib(n - 1)`), whether the callee is a top-level `fn`, an inner function,
  or a local variable holding a lambda — the callee is resolved by name at
  the call site, local scope first, then global.
- A name immediately followed by `.otherName(` is ambiguous on its own — see
  the next rule.

### `alias.name(...)` vs. `variable.method(...)`

Since a bareword is always a variable now, `col.push(x, y)` (a call into an
aliased library import) and `obj.increment()` (a method call on a variable
holding an object) are spelled identically: `IDENT '.' IDENT '(' ... ')'`.
The parser resolves this the only way it can while staying a single
left-to-right pass: it remembers every name bound by `import ... as alias`
seen so far in the file, and only treats `IDENT.name(...)` as a static
namespace call ([NamespaceCall](#calls)) when `IDENT` is one of those known
aliases. Otherwise it parses as an ordinary [method call](#field-access-and-method-calls)
on the variable `IDENT`, which also works correctly at runtime for a
namespace-valued variable (e.g. one bound by `importDynamic`), since a
`Namespace` value is itself a kind of environment that method-call dispatch
already knows how to look functions up in.

### String literals never accidentally act as punctuation

Because a `STRING_LITERAL` token's lexeme is its _decoded content_, a string
whose contents happen to be `"{"` or `"."` or `"?"` must never be mistaken
for actual structural punctuation. Essentially every lookahead check in the
parser (`peek().getLexeme().equals("{")`, etc.) is paired with a
`token type != STRING_LITERAL` guard. This is systemic, not a handful of
one-off checks.

### `{` after an expression or at primary position — five-way disambiguation

Checked in this priority order:

1. `{ }` or `{ ident : ` (a fixed 2-token lookahead) → [struct-init](#struct-init).
2. Otherwise, `{` (or `[`) following an _existing_ expression → [index access](#index--access).
3. At _primary_ position (no left-hand expression yet), next token
   `var`/`const`/`fn` → [object literal](#object-literal).
4. At primary position, next token `STRING` followed by `:` → [map literal](#array--list--map-literals).
5. At primary position, otherwise → [list literal](#array--list--map-literals).

### `(` at primary position — grouping vs. arrow lambda

A forward scan (tracking paren-nesting depth) checks whether the matching `)`
is immediately followed by `->`. If so, it's an [arrow lambda](#lambda);
otherwise it's a parenthesized grouped expression.

### `..` always means "range"

`<` and `>` are ordinary comparison operators everywhere — they carry no
special meaning for ranges. `..` is recognized directly inside the
precedence-climbing loop, the same way ternary `?` is: once an operand has
been parsed, a `..` token (only at the outermost precedence level — see
[precedence](#operator-precedence)) always starts a [range](#range). This is
what makes ranges work as a general expression, not just inside `for`
headers.

### Keyword strictness differs by context

- **Strict** (`matchIdentifier`): rejects _any_ keyword outright. Used for
  `var`/`const` names and struct-init field names.
- **Loose** (`matchExpression`/an `EXPRESSION`-typed token): accepts
  `true`/`false`/`null` (via the same predicate that lets literals satisfy
  "is this an expression token") in addition to plain identifiers, but still
  rejects real control keywords (`if`, `for`, `fn`, …). Used for function
  names, parameter names, `throw`'s exception-type name, `catch`'s
  type-filter/bound name, enum name/members, and import paths/aliases.

A parameter literally named `true` therefore parses; `var true : 1;` does not.

### `for`'s multi-`var` init clause vs. a plain multi-name `VarDecl`

Inside a classic `for(...)` header, a bare `,` in the init clause means
"expect another `var` next." A standalone `var i:0, j:10;` statement, by
contrast, refuses to continue past a comma that's followed by `var` — that
handoff is exactly what lets the `for`-loop claim the comma instead.

### Type annotation disambiguation

`:` already means "here's the initializer" (`VarDecl`), "here's the default"
(`Param`), and "here's the field's value" (struct/object field decls, which
reuse `VarDecl`) — reusing it for type annotations without breaking any of
that requires pure lookahead, never a speculative parse-and-reinterpret
(parsing the first clause as a general `Expression` and only later deciding
it was actually a type would make `var a : Int?` ambiguous with the
[ternary operator](#ternary), since `Int` would greedily start parsing
`Int ? … : …` and consume the very colon that was supposed to separate the
type from the initializer).

- **`VarDecl`/field decls**: when the token after the name is `:`, the
  parser looks ahead for the pattern `: TypeName [ ? ] :` — an
  `EXPRESSION`-typed token (so not `true`/`false`/`null`, which are
  `KEYWORD`-typed and can't start a `TypeExpr`), an optional `?`, and a
  **second** `:` — before committing to reinterpreting the first clause as a
  type rather than an initializer. A single `:` with no second one, or a
  first clause that doesn't look like a bare type name, is unconditionally
  the pre-existing untyped form. This is why `var a : true ? 1 : 2;` still
  parses as an untyped `var` with a ternary initializer: `true` is
  `KEYWORD`-typed, so it never matches the type-name lookahead in the first
  place.
- **`Param`**: since "typed, no default" (a single `: Type` with nothing
  after it) is the common case for parameters, and there's no second colon
  available to disambiguate the way `VarDecl` does, the parser instead looks
  ahead for `: TypeName [ ? ]` immediately followed by `,` or `)`. This is a
  real, narrow behavior change: a parameter previously defaulted to a bare,
  unquoted, identifier-shaped string (e.g. `fn f(mode : enabled)`) is now
  read as `mode` typed as `enabled` instead. Every other default value shape
  (numbers, strings, booleans, `null`, any non-trivial expression) is
  unaffected, since none of them match "single bare identifier with nothing
  after it in parameter position."
- **`FuncDecl`'s `-> TypeExpr`**: unambiguous by construction — `->` between
  a named function's `)` and its `{` was unused grammar space before this
  feature existed. It is deliberately **not** extended to lambdas: an arrow
  lambda's own `->` already means "body follows," so `(x) -> Int` staying
  "the body is the bare expression `Int`" (not "declares return type `Int`,
  body is next") avoids reopening that ambiguity.
