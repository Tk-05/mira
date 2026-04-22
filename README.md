# Mira

Interpreted programming language written in Java.

---

## Requirements

- Java 21
- Maven

---

## Build

```bash
mvn package
```

Produces `target/mira-RELEASE.jar`.

---

## Usage

**Run a script:**
```bash
java -jar target/mira-RELEASE.jar path/to/script.mira
```

**Start the interactive REPL:**
```bash
java -jar target/mira-RELEASE.jar
```

**Flags:**

| Flag | Description |
|------|-------------|
| `-h`, `-help` | Show help |
| `-t` | Print tokens (lexer output) |
| `-e` | Parse only, do not execute |
| `-m` | Use `main()` as entry point |
| `-li` | Show loaded library info |
| `-args <arg0,arg1,...>` | Pass arguments to the script |
| `-lint` | Run the static linter before execution |
| `-watch` | Enable hot reload |
| `-crash` | Print stacktrace and dump interpreter memory |
| `-ast` | Print asts |

**Example:**
```bash
java -jar target/mira-RELEASE.jar -m src/main/resources/demo/Debug.mira
```