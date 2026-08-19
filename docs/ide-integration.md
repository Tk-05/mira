# Mira IDE Integration (LSP)

[← Back to index](../Documentation.md) · [Language Guide](language-guide.md) · [Grammar Reference](grammar.md) · [Standard Library Reference](standard-library.md) · [Build System](build-system.md)

---

## IDE Integration (LSP)

Mira ships with a built-in Language Server that implements the [Language Server Protocol (LSP)](https://microsoft.github.io/language-server-protocol/). It runs as a subprocess of your editor and communicates over stdin/stdout.

### Features

| Feature                    | Description                                                                                              |
| -------------------------- | --------------------------------------------------------------------------------------------------------- |
| **Syntax highlighting**    | Keywords, strings, numbers, variables (`x`), comments, function names — via the static TextMate grammar   |
| **Semantic highlighting**  | Additional binding-aware coloring for variables, parameters, functions/methods, and object/struct fields   |
| **Diagnostics**            | Parse errors, linter warnings, [type errors](language-guide.md#type-annotations), and hints shown inline as you type |
| **Code completion**        | Keywords, built-in functions, stdlib functions, local variables and functions, imported module functions, built-in and declared type names |
| **Hover**                  | Signatures and doc comments for local functions/variables, stdlib functions, and object/struct fields — including any declared [type annotation](language-guide.md#type-annotations) |
| **Go to Definition**       | Jumps to the declaration of a local symbol or one imported from another module                             |
| **Find All References**   | Lists every usage of a symbol in the current file, plus cross-file usages of top-level symbols             |
| **Rename Symbol**          | Renames a symbol and all its known references in one edit (`F2` in VS Code)                                |
| **Workspace Symbol Search**| Jumps to any function, variable, or enum by name across the whole project (`Ctrl+T` in VS Code)             |
| **Document Symbols**       | Outline view of functions, variables, enums, and struct/object fields for the current file                 |
| **Code Actions**           | Quick fixes offered inline for certain diagnostics                                                         |
| **Signature Help**         | Parameter hints shown while typing a function call's arguments                                             |
| **Document formatting**    | Re-prints the file from its parsed AST with `Shift+Alt+F` — see [Formatter](#formatter) below              |

### VS Code Extension

The Mira VS Code extension is located in `lsp/` inside the repository. It activates automatically for `.mira` files.

**Requirements:**

- Java 17 or later on `PATH`
- The Mira JAR at `~/.mira/mira.jar` (placed there automatically by `mvn package`)

**Building the extension:**

```
cd lsp
npm install
npm run build
```

This compiles the TypeScript and produces `mira-language-*.vsix`.

**Installing:**

1. Open VS Code
2. Go to **Extensions** (`Ctrl+Shift+X`)
3. Click the `···` menu → **Install from VSIX...**
4. Select the generated `.vsix` file

**Reinstalling after a JAR update:**

Run `mvn package -DskipTests` from the project root — the JAR is automatically copied to `~/.mira/mira.jar`. The extension picks up the new JAR on the next VS Code window reload.

### LSP Server

The language server starts as a subprocess of the editor. It can also be started manually for debugging:

```
java -jar mira.jar --lsp
```

It reads LSP JSON-RPC messages from stdin and writes responses to stdout. All other Mira output (diagnostics, errors) goes to stderr so it does not interfere with the protocol.

### Diagnostics

Errors and warnings appear as red/yellow underlines directly in the editor. Hover over them to see the message and any hint.

**Sources of diagnostics:**

| Source         | Severity       | Example                                                                                  |
| -------------- | -------------- | ---------------------------------------------------------------------------------------- |
| Tokenizer      | Error          | Unterminated string literal                                                              |
| Parser         | Error          | Unexpected token, missing `)`                                                            |
| Linter         | Warning / Info | Unused variable, shadowed name                                                           |
| Static checker | Error          | Undeclared variable, arity mismatch, [type mismatch](language-guide.md#type-annotations) |

Type errors (`E324`-`E329`) only appear for code that actually carries a [type annotation](language-guide.md#type-annotations) somewhere in the comparison — unannotated code never produces new diagnostics from this.

Diagnostics are cleared automatically when the file is closed.

### Completions

Completions trigger automatically as you type. The following are always available:

- All Mira **keywords** (`var`, `fn`, `if`, `for`, `switch`, `return`, `comptime`, `static_assert`, …)
- All **built-in globals** (`print`, `scan`, `eval`, `length`, `assert`, …)
- All **built-in type names** (`Number`, `String`, `Bool`, `List`, `Array`, `Map`, `Object`, `Fn`, `Null`, `Any`, `Void` — see [Type Annotations](language-guide.md#type-annotations))

Additionally, for each open file the server provides:

- **Local variables** declared with `var` or `const` — shown as `name`
- **Local functions** declared with `fn` — shown with their parameter list (including any declared parameter/return types)
- **Declared `type` aliases, `enum` names, and struct template variable names** — usable as type names (e.g. `var Point : struct { ... };` suggests bare `Point`, in addition to the usual `$Point`/`$Point.field` forms)
- **Imported stdlib symbols** — shown as `alias.name(params)` when imported with an alias; only the selected symbols when using brace or colon syntax
- **Imported module symbols** — parsed from the imported `.mira` file; only `pub`-marked symbols are shown. Shown as `alias.name` when imported with `as alias`, or as the bare `name` when imported without one (e.g. `import module "lib.mira" {greet};` suggests bare `greet`, not `greet` under a namespace); if the import selects specific names, only those are suggested

Example — after `import math as m;`, typing `m.` suggests:

```
m.sqrt(x)
m.pow(base, exp)
m.sin(x)
...
```

### Formatter

The document formatter (`Shift+Alt+F` in VS Code) parses the file into an AST and re-prints it with consistent 4-space indentation — it is not a simple whitespace/re-indent pass. It:

- Normalizes indentation and spacing around every statement and expression form
- Preserves string content exactly, including multi-line (`"""..."""`) strings, escaping it correctly when re-printing (e.g. a `"` inside a map key)
- Preserves standalone and inline comments, including one left alone inside an otherwise-empty block
- Preserves blank lines between top-level declarations

Since it re-prints from the AST rather than editing the original text in place, formatting is idempotent (formatting already-formatted code is a no-op) and always produces a program equivalent to the original — it will not silently change what the code means.

### Language Configuration

The extension registers the following editor behaviors for `.mira` files:

- **Line comment:** `//`
- **Block comment:** `/* ... */`
- **Auto-closing pairs:** `{}`, `[]`, `()`, `""`
- **Bracket matching:** `{}`, `[]`, `()`

---
