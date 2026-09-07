# Mira Build System

[← Back to index](../Documentation.md) · [Language Guide](language-guide.md) · [Grammar Reference](grammar.md) · [Standard Library Reference](standard-library.md) · [IDE Integration](ide-integration.md)

Projects, `mira.toml`, dependency management (local/git/registry/native), tasks, and compiling to bytecode or a standalone JAR.

## Table of Contents

1. [Build System](#build-system) — Creating a Project, `mira.toml`, Commands, Release Pipeline, Build Modes, Running Tests
2. [Dependencies](#dependencies) — Local, Git, Locally Installed, Native, `mira deps`
3. [Tasks](#tasks)
4. [Compilation](#compilation) — `.class` files, standalone JARs, in-memory compile-and-run, flags

---

## Build System

The Mira build system lets you manage a multi-file project with a single configuration file (`mira.toml`) and a set of subcommands. Single-file usage (`mira <file.mira> [flags]`) continues to work unchanged.

### Creating a Project

```bash
mira init                  # use current directory name as project name
mira init --name my-app    # explicit project name
```

Creates two files in the current directory:

```
my-app/
├── mira.toml        ← project configuration
└── src/
    └── main.mira    ← entry point skeleton
```

### `mira.toml` — Project Configuration

```toml
[project]
name    = "my-app"        # project name
version = "0.1.0"
entry   = "src/main.mira" # entry point (required, relative to mira.toml)

[build]
mode       = "interpret"   # interpret | compile | package  — used by mira build
run-mode   = "interpret"   # interpret | compile            — used by mira run (optional, defaults to mode)
                           # compile = equivalent to --compile --run: compiles to JVM bytecode in memory, no files written
main       = true          # call main() as entry point (equivalent to --main flag)
lint       = false         # run linter before execution
output     = "out"         # output directory for compiled files (default: "out/")
args       = []            # default program arguments
strict-types = false       # require type annotations on every top-level function (equivalent to --strict-types)
pre-build  = "codegen"              # single task — or an array: ["codegen", "lint"]
post-build = ["notify", "upload"]   # multiple tasks run in order
pre-run    = "prepare"              # task to run before mira run   (optional)
post-run   = "cleanup"              # task to run after  mira run   (optional)

[test]
pattern   = "**/*_test.mira" # glob for test files relative to project root
extra     = []               # additional test files
pre-test  = "seed-db"              # single task or array: ["seed-db", "migrate"]
post-test = ["teardown", "report"] # multiple tasks run in order

[dependencies]
math-utils = { path = "../math-utils" }  # local path dependency

[native]
raylib = { url = "https://example.com/raylib.jar", sha256 = "…" }  # native JAR dependency
```

All paths in `mira.toml` are relative to the file itself.

### Commands

| Command                                            | Description                                                                                              |
| -------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `mira init [--name <n>]`                           | Create a new project in the current directory                                                            |
| `mira build`                                       | Build the project using the mode defined in `mira.toml`                                                  |
| `mira build --mode interpret\|compile\|package`    | Override the build mode for this run                                                                     |
| `mira build --watch`                               | Build and re-run on file changes                                                                         |
| `mira run [--mode interpret\|compile] [-- <args>]` | Run the project; execution mode from `mira.toml` unless overridden; `--` passes arguments to the program |
| `mira test`                                        | Discover and run all test files matching `test.pattern`                                                  |
| `mira clean`                                       | Delete the output directory                                                                              |
| `mira release`                                     | Full pipeline: pre-build → build → post-build → pre-test → tests → post-test                             |
| `mira task`                                        | List all tasks defined in `mira.toml`                                                                    |
| `mira task <name>`                                 | Run the task named `<name>`                                                                              |
| `mira install`                                     | Copy this project into the local install cache (`~/.mira/packages/local/<name>/<version>`)               |
| `mira deps`                                        | Print the declared dependency graph and whether each entry is available locally (read-only, no fetching) |

All commands (except `init`) require a `mira.toml` in the current directory or any parent directory. If none is found, an error is printed with a hint to run `mira init`.

Every command above also accepts `--project <dir>` (short: `-C <dir>`) to point at a
project that isn't in the current directory, so you don't need to `cd` first:

```bash
mira build --project ../other-app
mira test -C ../other-app
```

`build`, `run`, `test`, and `release` also accept `--no-warn` (suppress warnings/hints)
and `--no-color` (disable ANSI diagnostic colors, also honors `NO_COLOR`); `run` and
`task <name>` additionally accept `--profile` and `--stats`; `test` additionally accepts
`--coverage` (see below). `build`, `run`, `test`, `release`, and `task <name>` all accept
`--strict-types`, equivalent to setting `mira.toml`'s `[build] strict-types = true`
(see [General flags](#general-flags) and the [Type Annotations](language-guide.md#type-annotations)
section of the Language Guide).

### Release Pipeline

`mira release` runs the full project lifecycle in a fixed order:

```
pre-build hooks
    ↓
mira build  (uses mode from mira.toml)
    ↓
post-build hooks
    ↓
pre-test hooks      ─┐
mira test            ├─ skipped with [info] message if [test] is not defined
post-test hooks     ─┘
```

A typical release setup:

```toml
[tasks.codegen]
cmd = "python scripts/gen.py"

[tasks.package-docs]
cmd = "mkdocs build"

[tasks.notify]
cmd = "curl -s https://hooks.example.com/released"

[build]
mode       = "package"
pre-build  = "codegen"
post-build = "package-docs"

[test]
pattern   = "**/*_test.mira"
post-test = "notify"
```

Running `mira release` with this config: generates code → builds a fat JAR → packages docs → runs all tests → sends a notification.

### Build Modes

The two mode fields control build and run independently:

| Field      | Used by      | Allowed values                    |
| ---------- | ------------ | --------------------------------- |
| `mode`     | `mira build` | `interpret`, `compile`, `package` |
| `run-mode` | `mira run`   | `interpret`, `compile`            |

If `run-mode` is not set, `mira run` falls back to `mode`.

| Effective mode | `mira build`                                       | `mira run`                                                                                                                                 |
| -------------- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `interpret`    | Interprets source directly — no files written      | Runs via tree-walk interpreter                                                                                                             |
| `compile`      | Compiles to `.class` files in the output directory | Compiles to JVM bytecode **in memory** and executes immediately — no files written (equivalent to the single-file `--compile --run` flags) |
| `package`      | Compiles and bundles into a self-contained fat JAR | Same as `compile` for run — executes in memory, no JAR written                                                                             |

A typical setup: build produces a JAR, but `mira run` uses the faster interpreter during development:

```toml
[build]
mode     = "package"    # mira build → standalone JAR
run-mode = "interpret"  # mira run  → fast interpreter
```

You can override `run-mode` for a single invocation:

```bash
mira run --mode interpret   # force interpreter
mira run --mode compile     # force in-memory compilation
```

### Running Tests

`mira test` discovers every `.mira` file matching the `test.pattern` glob (default `**/*_test.mira`), runs each file with test mode enabled, and prints a pass/fail summary per file. The process exits with code `1` if any test fails.

```bash
mira test
# Running tests for my-app...
#
# --- src/math_test.mira ---
#   PASS addition
#   PASS multiplication
# ─── Test Summary ───
#   Passed : 2
#   Failed : 0
#   Total  : 2
#   Status : OK
```

Test files use the built-in `test()` and `assert()` functions:

```
module math_test;

import math as m;

test("square root", fn() {
    assert(m.sqrt(9) == 3);
});
```

#### Coverage

`--coverage` (single-file `mira <file> --test --coverage`, or `mira test --coverage`) prints a line-coverage report after the run: for the test file(s) plus every module they import (transitively), which lines actually executed vs. which exist but never ran.

```bash
mira test --coverage
# ...
# === MIRA COVERAGE ===
# Files: 2
#   math_test.mira       2/3 lines (66.7%)
#   mathlib.mira          1/2 lines (50.0%)
#
# Totals: 3/5 lines covered (60.0%)
#
# Uncovered lines:
#   mathlib.mira: 8
# === END COVERAGE ===
```

Coverage is computed independently of `--profile` (which tracks timing, not test coverage, and is not test-aware in the same way); it only tracks the lines actually reached while running `test()` bodies.

## Dependencies

Mira resolves four kinds of dependency: local path, git, locally-installed, and native JAR.

### Local Dependencies

A dependency declared under `[dependencies]` must point to a directory that itself contains a `mira.toml`. The dependency's source files are added as import roots, so module imports that are not found relative to the current file are also searched in each dependency's root directory.

```toml
[dependencies]
utils = { path = "../utils" }
```

```
# in src/main.mira
import module "./utils/strings.mira" as str;   # resolved in the utils project
```

### Git Dependencies

A dependency can also point to a git repository instead of a local path. The repository is cloned into a shared local cache under `~/.mira/packages` (keyed by repo URL and resolved commit), so the same commit is never fetched twice across projects. The repository's root must itself contain a `mira.toml`, exactly like a path dependency.

```toml
[dependencies]
utils = { path = "../utils" }                                              # local
http   = { git = "https://github.com/user/http", tag = "v1.2.0" }          # exact tag
edge   = { git = "https://github.com/user/edge", branch = "main" }         # branch (mutable)
pinned = { git = "https://github.com/user/pinned", rev = "a1b2c3d..." }    # exact commit
json   = { git = "https://github.com/user/json", version = "^2.0" }       # semver constraint
```

Exactly one of `tag`, `branch`, `rev`, or `version` must be given alongside `git`. `version` is a semver constraint (`^1.2.3`, `~1.2.3`, or an exact `1.2.3`) matched against the repository's tags (`v1.2.3` or `1.2.3`); the highest matching tag is used.

Resolving a git dependency writes a `mira.lock` file next to `mira.toml`, pinning each dependency to the exact commit that was resolved. Subsequent builds reuse the locked commit (and the local cache) without contacting the remote again, as long as `mira.toml` doesn't change. Delete `mira.lock` (or remove the relevant entry) to force re-resolution against the remote.

Only `http(s)` and local `file://` URLs are supported; SSH remotes are not yet wired up. Two dependencies that transitively require incompatible versions of the same git dependency are not currently supported — only one version per dependency name can be resolved per project.

### Locally Installed Dependencies

`mira install`, run inside a project's own directory, copies that project into a local install cache under `~/.mira/packages/local/<name>/<version>/`, keyed by its `[project]` `name` and `version` — the local equivalent of `mvn install` populating `~/.m2/repository`. Other projects on the same machine can then depend on it by coordinates alone, without a relative path:

```toml
# in mylib's own mira.toml
[project]
name    = "mylib"
version = "1.0.0"
```

```bash
cd mylib && mira install
# Installed mylib 1.0.0 -> ~/.mira/packages/local/mylib/1.0.0
```

```toml
# in the consumer's mira.toml — no path, no git, just name + version
[dependencies]
mylib = { version = "1.0.0" }
```

The dependency's key in `[dependencies]` (`mylib` above) is looked up directly against `local/<key>/<version>`, so it must match the name the library was installed under. Re-running `mira install` overwrites the previous install for that exact version (skipping `.git` and the build output directory). If the version isn't installed yet, resolving fails with an error pointing at `mira install`. Like git dependencies, only one version per dependency name is resolved per project — there's no coexisting-versions support yet.

### Native Dependencies

A project can declare that it needs a native JAR (a compiled JVM jar implementing `com.mira.lib.Lib`, typically bundling native shared libraries — see [Native JAR Extensions](language-guide.md#native-jar-extensions)) under a `[native]` table, separate from `[dependencies]`:

```toml
[native]
raylib = { url = "https://github.com/user/repo/releases/download/v1.0.0/raylib.jar", sha256 = "…64 hex chars…" }
```

`sha256` is required for `http(s)://` URLs — there is no unverified/unpinned form for a remote fetch. Resolving a `[native]` entry downloads it into a shared local cache at `~/.mira/packages/native/<sha256>/<basename>`, keyed purely by content hash: a cache hit is trusted without re-hashing, and there's nothing "mutable" to re-resolve the way a git branch or tag can move — the hash _is_ the pin. Only `http(s)://` and local `file://` URLs are supported today.

**`sha256` is optional for `file://` URLs.** A file already on the local machine has no integrity concern worth verifying, and skipping the hash means the dependency isn't copied into the cache at all — every resolve reads the file fresh from wherever it points, so a local build (e.g. `mvn package` regenerating `extern/raylib/target/raylib.jar`) is picked up immediately on the next `mira build`, instead of a stale hash-pinned copy from whenever `mira.toml` was last edited:

```toml
[native]
raylib = { url = "file:///C:/Users/me/mira/extern/raylib/target/raylib.jar" }   # no sha256 — always reads live
```

This is exactly how `extern/raylib`'s own `mira.toml` in this repo is set up for local development. Switch to an `http(s)://` URL (with a `sha256`) once the jar is actually published somewhere stable.

Once resolved, the jar's containing cache directory is added to the same lookup a bare `import native "<basename>"` consults (see [Native JAR Extensions](language-guide.md#native-jar-extensions)), so consumers never write a path:

```
import native "raylib.jar" as raylib;   // no path — found via the [native] entry
```

**Transitivity is arbitrary-depth.** If a `path`/`git`/`version` dependency's own `mira.toml` has a `[native]` table, those entries are resolved for the consumer automatically, without redeclaring them — and this keeps going through the whole dependency graph, however deep: if your game depends on a game engine, and the engine depends on a raylib wrapper package that declares `[native]`, your game gets the native jar without ever mentioning it. This is safe to do at unlimited depth (unlike source dependencies, which are intentionally _not_ walked transitively) because `[native]` entries are content-addressed by sha256 — there's no version to conflict on. The walk is cycle-safe, and a dependency's _other_, unrelated dependency that can't itself be resolved (missing directory, not installed, unreachable git remote) is silently skipped rather than failing your build — only its own `[native]` table matters here, not whether every one of its dependencies happens to be resolvable on your machine.

Nested git dependencies discovered this way (a dependency's dependency's `{ git = "..." }`) are re-resolved fresh on every `mira build` rather than pinned in your project's `mira.lock` — a transitive dependency's ref should be pinned by _its own_ project's lockfile, not by whichever downstream project happens to reach it first. In practice this only matters if native jars end up several git-dependency-hops away; `path` and `version` (local install) dependencies have no such cost.

### `mira deps`

`mira deps` prints the declared dependency graph — both `[dependencies]` and `[native]`, recursively — showing for each entry whether it's currently available locally (lockfile/cache hit) or still needs resolving. It is purely read-only: it never fetches, downloads, or clones anything, so it's safe to run offline to see what a `mira build` would need to do.

```
app (0.1.0)
├─ math-utils [path] ../math-utils                        ✓ available
├─ http [git] https://github.com/user/http @ tag v1.2.0   ✓ available (locked: a1b2c3d1e2)
│  └─ jaylib [native] https://…/jaylib.jar (sha256 9f2e1a4c1b)  ✗ missing — run 'mira build'
└─ json [git] https://github.com/user/json @ version ^2.0  ✗ missing (not yet resolved — run 'mira build')
```

Like the resolver's `[native]` transitivity, the tree shown by `mira deps` recurses through every available dependency's own `mira.toml` to arbitrary depth (a dependency that isn't available yet can't be expanded further, since its manifest isn't known locally) and detects cycles. The difference is what each walk _surfaces_: the resolver only ever adds a project's own directly-declared dependencies to the build's source roots (nested dependencies are walked only to find `[native]` tables, never added as source roots themselves), while `mira deps` displays every node in the graph — source and native — purely for inspection, without fetching anything.

## Tasks

Tasks are named automation steps defined in `mira.toml` under `[tasks]`. Each task runs either a shell command (`cmd`) or a Mira script (`script`) — not both.

```toml
[tasks.format]
cmd         = "prettier --write src/"
description = "Format source files"

[tasks.codegen]
script      = "scripts/codegen.mira"
description = "Generate code from schema"
```

Shorthand using a plain string — the type is inferred automatically:

- Ends with `.mira` → treated as `script`
- Anything else → treated as `cmd`

```toml
[tasks]
clean = "rm -rf out/"      # cmd  — shell command
demo  = "scripts/demo.mira" # script — Mira file
```

| Command            | Description                                    |
| ------------------ | ---------------------------------------------- |
| `mira task`        | List all defined tasks with their descriptions |
| `mira task <name>` | Run the task named `<name>`                    |

**Rules:**

- Exactly one of `cmd` or `script` must be set — specifying both or neither is an error
- `description` is optional
- `cmd` is executed via the system shell (`cmd.exe /c` on Windows, `sh -c` on Unix)
- `script` is resolved relative to `mira.toml` and executed as a Mira file
- A non-zero exit code from `cmd` results in a `[fail]` error

### Hooks

Tasks can be wired as automatic pre/post hooks for the built-in commands via fields in `[build]` and `[test]`. Every hook value is the name of a task defined in `[tasks]`.

| Field        | Runs before/after |
| ------------ | ----------------- |
| `pre-build`  | `mira build`      |
| `post-build` | `mira build`      |
| `pre-run`    | `mira run`        |
| `post-run`   | `mira run`        |
| `pre-test`   | `mira test`       |
| `post-test`  | `mira test`       |

Example — generate code before every build and send a notification afterwards:

```toml
[tasks.codegen]
cmd         = "python scripts/gen.py"
description = "Generate code from schema"

[tasks.notify]
cmd         = "curl -s https://hooks.example.com/build-done"
description = "Notify external service"

[build]
mode       = "compile"
pre-build  = "codegen"
post-build = "notify"
```

---

## Compilation

Mira scripts can be compiled to JVM bytecode instead of interpreted. The compiler produces standard `.class` files that run on any JVM without the Mira interpreter.

### Compile to `.class` files

```bash
java -jar mira-RELEASE.jar script.mira --compile
```

Writes `.class` files next to the source file. Use `-o`/`--output <dir>` to write them to a different directory:

```bash
java -jar mira-RELEASE.jar script.mira --compile -o out/
```

Run the compiled output directly with the JVM (Mira runtime required on the classpath):

```bash
java -cp mira-RELEASE.jar:out/ com.mira.compiled.Script
```

### Package into a standalone JAR

The `--package` flag (used together with `--compile`) bundles the compiled classes and the entire Mira runtime into a single self-contained fat JAR:

```bash
java -jar mira-RELEASE.jar script.mira --compile --package
```

The JAR is placed next to the source file (or in the `-o`/`--output` directory if specified) and named after the script:

```bash
java -jar Script.jar
```

No classpath setup is needed — the fat JAR is fully standalone and can be distributed as a single file.
Add `--slim` to bundle only the classes reachable from the program (excluding the
compiler/IDE toolchain) instead of the full distribution (`--full`, the default).

### Compile and run in memory

`--run` (only meaningful together with `--compile`) compiles the script and immediately executes it without writing any files to disk:

```bash
java -jar mira-RELEASE.jar script.mira --compile --run
```

### Compilation flags summary

| Flag                    | Description                                                               |
| ----------------------- | ------------------------------------------------------------------------- |
| `--compile`             | Compile to JVM bytecode and write `.class` files                          |
| `--package`             | Bundle `.class` files and the Mira runtime into a standalone fat JAR      |
| `--run`                 | (with `--compile`) Run in memory instead of writing files                 |
| `--slim` / `--full`     | (with `--package`) Bundle only reachable classes, or everything (default) |
| `-o`, `--output <dir>`  | Output directory for `.class` files and JAR (default: source directory)   |
| `-b`, `--dump-bytecode` | Dump disassembled bytecode of compiled classes to stdout                  |

### General flags

Flags available for both single-file and build-system usage:

| Flag              | Description                                                                                                                                                                                                                                                                                                                                                                                                                          |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `-m`, `--main`    | Call `main()` as the program entry point                                                                                                                                                                                                                                                                                                                                                                                             |
| `-- <a> <b> ...`  | Pass arguments to the program (everything after `--`)                                                                                                                                                                                                                                                                                                                                                                                |
| `--no-check`      | Skip the static check (linter / unused-variable analysis)                                                                                                                                                                                                                                                                                                                                                                            |
| `--no-warn`       | Suppress all warnings and hints produced by the static checker                                                                                                                                                                                                                                                                                                                                                                       |
| `--no-color`      | Disable colored/ANSI diagnostic output (also honors `NO_COLOR`)                                                                                                                                                                                                                                                                                                                                                                      |
| `--test`          | Run `test()` calls and print a pass/fail summary; exits 1 on fail                                                                                                                                                                                                                                                                                                                                                                    |
| `--coverage`      | With `--test`: print a line-coverage report for the test file(s) and every module they import, transitively                                                                                                                                                                                                                                                                                                                          |
| `--debug`         | Launch the interactive debugger                                                                                                                                                                                                                                                                                                                                                                                                      |
| `--profile`       | Print a function- and line-level timing report after execution (with `--compile`, requires `--run` — profiling a `.class` file written to disk has no effect)                                                                                                                                                                                                                                                                        |
| `--watch`         | Re-run the program whenever the source file or its imports change                                                                                                                                                                                                                                                                                                                                                                    |
| `-v`, `--verbose` | Report progress as it happens (module cache hits/parses, dependency resolution, static-check summary, compile phase timing); combine with `--imports` for extra detail there                                                                                                                                                                                                                                                         |
| `-t`, `--tokens`  | Dump the token stream to stdout                                                                                                                                                                                                                                                                                                                                                                                                      |
| `--check-only`    | Exit after parsing and static check, before interpretation                                                                                                                                                                                                                                                                                                                                                                           |
| `--ast`           | Print the AST to stdout                                                                                                                                                                                                                                                                                                                                                                                                              |
| `--imports`       | Show all loaded imports with their type and alias                                                                                                                                                                                                                                                                                                                                                                                    |
| `--version`       | Print the Mira version (no short form — `-v` is `--verbose`)                                                                                                                                                                                                                                                                                                                                                                         |
| `--stats`         | Print compiler/parser stats (line, token, and AST node counts, function/variable/import/enum counts — variables broken out as top-level vs. total including locals — warning count, [type-annotation coverage](language-guide.md#type-annotations), per-file tokenize/parse/static-check timing plus a total) for the entry file and every module it imports, transitively. With `--compile`, also reports bytecode-generation time. |
| `--strict-types`  | Require every top-level function's parameters and return type to carry an explicit [type annotation](language-guide.md#type-annotations) (`E328` if not); off by default and additive with `mira.toml`'s `strict-types` setting                                                                                                                                                                                                      |

`-v`/`--verbose` also works on the project subcommands (`build`, `run`,
`test`, `release`) — e.g. `mira build -v` prints which dependencies are
resolved from cache versus fetched over the network.

On an uncaught error, Mira always prints a crash dump (Mira call stack, Java
stack trace, and memory dump) to stderr — this is unconditional, no flag
needed.

---
