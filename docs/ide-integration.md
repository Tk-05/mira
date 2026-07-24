# Mira IDE Integration (LSP)

[← Back to index](../Documentation.md) · [Language Guide](language-guide.md) · [Standard Library Reference](standard-library.md) · [Build System](build-system.md)

---

## IDE Integration (LSP)

Mira ships with a built-in Language Server that implements the [Language Server Protocol (LSP)](https://microsoft.github.io/language-server-protocol/). It runs as a subprocess of your editor and communicates over stdin/stdout.

### Features

| Feature                 | Description                                                                                              |
| ----------------------- | -------------------------------------------------------------------------------------------------------- |
| **Syntax highlighting** | Keywords, strings, numbers, variables (`$x`), comments, function names                                   |
| **Diagnostics**         | Parse errors, linter warnings, and hints shown inline as you type                                        |
| **Code completion**     | Keywords, built-in functions, stdlib functions, local variables and functions, imported module functions |
| **Document formatting** | Fixes indentation with `Shift+Alt+F` — preserves all string content                                      |

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

| Source    | Severity       | Example                        |
| --------- | -------------- | ------------------------------ |
| Tokenizer | Error          | Unterminated string literal    |
| Parser    | Error          | Unexpected token, missing `)`  |
| Linter    | Warning / Info | Unused variable, shadowed name |

Diagnostics are cleared automatically when the file is closed.

### Completions

Completions trigger automatically as you type. The following are always available:

- All Mira **keywords** (`var`, `fn`, `if`, `foreach`, `switch`, `return`, `comptime`, `static_assert`, …)
- All **built-in globals** (`print`, `scan`, `eval`, `length`, `assert`, …)

Additionally, for each open file the server provides:

- **Local variables** declared with `var` or `const` — shown as `$name`
- **Local functions** declared with `fn` — shown with their parameter list
- **Imported stdlib symbols** — shown as `alias.name(params)` when imported with an alias; only the selected symbols when using brace or colon syntax
- **Imported module symbols** — parsed from the imported `.mira` file; only `pub`-marked symbols are shown

Example — after `import math as m;`, typing `m.` suggests:

```
m.sqrt(x)
m.pow(base, exp)
m.sin(x)
...
```

### Formatter

The document formatter (`Shift+Alt+F` in VS Code) re-indents the entire file using 4-space indentation. It:

- Increases indent after `{`
- Decreases indent before `}`
- Preserves all string content exactly — no characters inside strings are modified
- Preserves multi-line strings (`"""..."""`) verbatim
- Preserves blank lines
- Skips brace counting inside `//` line comments and `/* */` block comments

The formatter does not change anything other than leading whitespace — it will not add or remove semicolons, reorder statements, or modify expressions.

### Language Configuration

The extension registers the following editor behaviors for `.mira` files:

- **Line comment:** `//`
- **Block comment:** `/* ... */`
- **Auto-closing pairs:** `{}`, `[]`, `()`, `""`
- **Bracket matching:** `{}`, `[]`, `()`

---

