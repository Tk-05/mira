# Mira Language Documentation

This page is an index — pick the guide for what you're trying to do:

| Guide                                                  | Covers                                                                                                                                                                                                                                                             |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [Language Guide](docs/language-guide.md)               | Syntax, values, expressions, data structures, control flow, functions, comptime, `static_assert`, objects/methods, structs, enums, built-in functions, multithreading, the test framework, and a full example program. Start here if you're learning the language. |
| [Grammar Reference](docs/grammar.md)                   | Formal lexical and syntactic grammar: tokens, keywords, operator precedence, every statement/expression production, and the parser's context-sensitive disambiguation rules. Look here for exact syntax, not semantics.                                          |
| [Standard Library Reference](docs/standard-library.md) | Every `import`-able module (`string`, `collection`, `map`, `math`, `io`, `net`, `dateTime`, `json`, `regex`, `shell`, `process`, `bytes`, `crypto`, `path`, `csv`, `term`, `zip`, `toml`, `random`, `url`, `number`, `set`, `log`, `time`).                        |
| [Build System](docs/build-system.md)                   | Projects, `mira.toml`, CLI commands, the release pipeline, build modes, dependencies (local/git/locally-installed/native), `mira deps`, tasks, and compiling to `.class` files or a standalone JAR.                                                                |
| [IDE Integration (LSP)](docs/ide-integration.md)       | The built-in Language Server: features, the VS Code extension, diagnostics, completions, formatting.                                                                                                                                                               |

## Quick Start

```bash
mkdir my-app && cd my-app
mira init --name my-app
mira run
```

See [Build System → Creating a Project](docs/build-system.md#creating-a-project) for details, or jump straight to the [Language Guide](docs/language-guide.md) to start writing Mira code.
