# Mira Standard Library Reference

[← Back to index](../Documentation.md) · [Language Guide](language-guide.md) · [Grammar Reference](grammar.md) · [Build System](build-system.md) · [IDE Integration](ide-integration.md)

Every module below is loaded with `import <name>` (or `import <name> as alias`). For always-available globals that need no import (`print`, `assert`, `eval`, …), see [Built-in Functions](language-guide.md#built-in-functions) in the Language Guide.

---

## Standard Libraries

### `string`

| Function                  | Description                                |
| ------------------------- | ------------------------------------------ |
| `charAt(str, index)`      | Returns the character at the given index   |
| `indexOf(str, char)`      | Returns the first index of a character     |
| `trim(str)`               | Removes leading and trailing whitespace    |
| `split(str, delimiter)`   | Splits string into an array                |
| `substr(str, start, end)` | Returns a substring                        |
| `strEqual(str1, str2)`    | Returns true if both strings are equal     |
| `replace(str, from, to)`  | Replaces all occurrences of a character    |
| `upper(str)`              | Returns `str` converted to uppercase       |
| `lower(str)`              | Returns `str` converted to lowercase       |
| `startsWith(str, prefix)` | Returns true if `str` starts with `prefix` |
| `endsWith(str, suffix)`   | Returns true if `str` ends with `suffix`   |
| `contains(str, sub)`      | Returns true if `str` contains `sub`       |
| `repeat(str, n)`          | Returns `str` repeated `n` times           |
| `toNumber(str)`           | Parses `str` as a number                   |
| `padLeft(str, width)`     | Left-pads `str` with spaces to `width`     |
| `padRight(str, width)`    | Right-pads `str` with spaces to `width`    |
| `isNumeric(str)`          | Returns true if `str` is a valid number    |

### `collection`

Works with lists and arrays unless noted otherwise.

| Function               | Description                                                   |
| ---------------------- | ------------------------------------------------------------- |
| `size(col)`            | Returns the number of elements                                |
| `push(list, value)`    | Appends a value to the end (mutates) — lists only             |
| `pop(list)`            | Removes the last element (mutates) — lists only               |
| `remove(list, index)`  | Removes the element at the given index (mutates) — lists only |
| `first(col)`           | Returns the first element                                     |
| `last(col)`            | Returns the last element                                      |
| `contains(col, value)` | Returns true if the value is in the collection                |
| `indexOf(col, value)`  | Returns the index of a value, or `-1`                         |
| `slice(col, from, to)` | Returns a sub-list                                            |
| `reverse(col)`         | Returns a reversed copy as a list                             |
| `concat(col1, col2)`   | Concatenates two collections into a new list                  |
| `flatten(col)`         | Flattens one level of nested lists/arrays                     |
| `join(col, separator)` | Joins elements into a string                                  |
| `newList()`            | Creates an empty mutable list                                 |
| `sort(col)`            | Sorts numerically, falls back to string comparison            |
| `unique(col)`          | Removes duplicates, preserves insertion order                 |
| `sum(col)`             | Returns the sum of all numeric elements                       |
| `avg(col)`             | Returns the average (throws if empty)                         |
| `min(col)`             | Returns the smallest numeric element                          |
| `max(col)`             | Returns the largest numeric element                           |
| `take(col, n)`         | Returns the first `n` elements                                |
| `drop(col, n)`         | Returns all elements except the first `n`                     |
| `zip(col1, col2)`      | Returns a list of `[a, b]` pairs                              |
| `fill(n, val)`         | Creates a list of `n` copies of `val`                         |

#### Higher-Order Functions

These functions accept a callback `fn` as their second argument.

```mira
import collection as col;

var doubled : col.map({1, 2, 3}, fn(x) { return eval($x * 2); });       // [2, 4, 6]
var evens   : col.filter({1,2,3,4}, fn(x) { return eval($x % 2 == 0); }); // [2, 4]
var total   : col.reduce({1,2,3}, fn(acc, x) { return eval($acc + $x); }, 0); // 6
```

| `map(col, fn)` | Applies `fn` to each element, returns a new list |
| `filter(col, fn)` | Keeps elements where `fn(element)` is truthy |
| `reduce(col, fn, init)` | Fold-left: `fn(accumulator, element)`, starting with `init` |
| `any(col, fn)` | True if at least one element satisfies `fn` |
| `all(col, fn)` | True if all elements satisfy `fn` |
| `count(col, fn)` | Counts elements where `fn(element)` is truthy |
| `sortBy(col, fn)` | Sorts by key extracted with `fn` |
| `findFirst(col, fn)` | Returns the first element where `fn(element)` is truthy |
| `chunk(col, size)` | Splits into sub-lists of `size` |
| `groupBy(col, fn)` | Groups into a map: key = `fn(element)`, value = list |

### `map`

| Function                     | Description                                                 |
| ---------------------------- | ----------------------------------------------------------- |
| `newMap()`                   | Creates an empty mutable map                                |
| `mapSize(map)`               | Returns the number of entries                               |
| `mapHas(map, key)`           | Returns true if the key exists                              |
| `mapRemove(map, key)`        | Removes the entry and returns the map                       |
| `mapKeys(map)`               | Returns a list of all keys                                  |
| `mapValues(map)`             | Returns a list of all values                                |
| `mapSet(map, key, value)`    | Sets `key` to `value`, returns the map                      |
| `mapGet(map, key)`           | Returns the value for `key`, or null if not found           |
| `mapEntries(map)`            | Returns a list of `[key, value]` pairs                      |
| `mapMerge(map1, map2)`       | Merges two maps; `map2` values overwrite `map1` on conflict |
| `mapFromLists(keys, values)` | Creates a map from two parallel lists                       |

### `math`

Constants: `pi`, `e`, `inf`, `nan`

| Function             | Description                    |
| -------------------- | ------------------------------ |
| `pow(base, exp)`     | Exponentiation                 |
| `sqrt(x)`            | Square root                    |
| `cbrt(x)`            | Cube root                      |
| `abs(x)`             | Absolute value                 |
| `round(x)`           | Round to nearest integer       |
| `floor(x)`           | Round down                     |
| `ceil(x)`            | Round up                       |
| `min(a, b)`          | Minimum of two values          |
| `max(a, b)`          | Maximum of two values          |
| `clamp(x, min, max)` | Clamp value to range           |
| `sign(x)`            | Sign: `-1`, `0`, or `1`        |
| `log(x)`             | Natural logarithm              |
| `log10(x)`           | Base-10 logarithm              |
| `log2(x)`            | Base-2 logarithm               |
| `sin(x)`             | Sine (radians)                 |
| `cos(x)`             | Cosine (radians)               |
| `tan(x)`             | Tangent (radians)              |
| `asin(x)`            | Arc sine                       |
| `acos(x)`            | Arc cosine                     |
| `atan(x)`            | Arc tangent                    |
| `atan2(y, x)`        | Arc tangent of y/x             |
| `toRad(deg)`         | Degrees to radians             |
| `toDeg(rad)`         | Radians to degrees             |
| `rand()`             | Random float in `[0, 1)`       |
| `randInt(min, max)`  | Random integer in `[min, max]` |
| `isNaN(x)`           | True if value is NaN           |
| `isInf(x)`           | True if value is infinite      |
| `gcd(a, b)`          | Greatest common divisor        |
| `lcm(a, b)`          | Least common multiple          |
| `factorial(n)`       | `n!` — n must be ≤ 20          |
| `trunc(x)`           | Truncates toward zero          |
| `hypot(a, b)`        | `sqrt(a² + b²)`                |

### `io`

| Function                    | Description                                               |
| --------------------------- | --------------------------------------------------------- |
| `readFile(path)`            | Reads a file and returns its content as a string          |
| `writeFile(path, content)`  | Writes a string to a file, creating directories if needed |
| `fileExists(path)`          | Returns true if the file exists                           |
| `appendFile(path, content)` | Appends content to a file (creates it if needed)          |
| `listDir(path)`             | Returns an array of file names in the directory           |
| `mkdir(path)`               | Creates a directory including all parents                 |
| `deleteFile(path)`          | Deletes the file at `path`                                |

### `net`

| Function                           | Description                          |
| ---------------------------------- | ------------------------------------ |
| `httpGet(url)`                     | Sends a GET request, returns body    |
| `httpPost(url, body, contentType)` | Sends a POST request, returns body   |
| `httpPut(url, body, contentType)`  | Sends a PUT request, returns body    |
| `httpDelete(url)`                  | Sends a DELETE request, returns body |
| `httpStatus(url)`                  | Returns the HTTP status code         |
| `httpHeader(url, header)`          | Returns a response header value      |
| `httpDownload(url, path)`          | Downloads a file to the given path   |
| `urlEncode(str)`                   | URL-encodes a string                 |
| `urlDecode(str)`                   | URL-decodes a string                 |

### `dateTime`

| Function                 | Description                                        |
| ------------------------ | -------------------------------------------------- |
| `now()`                  | Current date-time as ISO string                    |
| `timestamp()`            | Current Unix timestamp in seconds                  |
| `timestampMs()`          | Current Unix timestamp in milliseconds             |
| `dateFormat(date, fmt)`  | Formats a date string with a pattern               |
| `year()`                 | Current year                                       |
| `month()`                | Current month (1–12)                               |
| `day()`                  | Current day of month                               |
| `hour()`                 | Current hour (0–23)                                |
| `minute()`               | Current minute                                     |
| `second()`               | Current second                                     |
| `dayOfWeek()`            | Day name e.g. `"MONDAY"`                           |
| `dayOfYear()`            | Day of year (1–366)                                |
| `secondsSince(date)`     | Seconds elapsed since the given date string        |
| `fromEpoch(seconds)`     | Converts a Unix timestamp (seconds) to date string |
| `addDays(date, n)`       | Returns a new date `n` days after `date`           |
| `dateDiff(date1, date2)` | Returns the number of days between two dates       |
| `isLeapYear(year)`       | True if `year` is a leap year                      |

### `json`

| Function                        | Description                                                |
| ------------------------------- | ---------------------------------------------------------- |
| `jsonGet(json, key)`            | Gets a scalar value by key                                 |
| `jsonHas(json, key)`            | Returns true if the key exists                             |
| `jsonArray(json, key)`          | Returns a top-level array as a list                        |
| `jsonNested(json, parent, key)` | Returns a nested array by parent key and array key         |
| `jsonBuild(keys, values)`       | Builds a JSON string from two lists                        |
| `jsonFormat(json)`              | Pretty-prints a JSON string                                |
| `jsonIndexOf(list, value)`      | Returns the index of a value in a JSON list, or `-1`       |
| `jsonKeys(json)`                | Returns an array of top-level keys                         |
| `jsonSize(json)`                | Returns the number of top-level keys/elements              |
| `jsonSet(json, key, value)`     | Sets `key` to `value` in a JSON object, returns new string |

### `regex`

| Function                             | Description                                  |
| ------------------------------------ | -------------------------------------------- |
| `matches(input, pattern)`            | True if the whole string matches the pattern |
| `contains(input, pattern)`           | True if the pattern is found anywhere        |
| `findFirst(input, pattern)`          | Returns the first match, or `""`             |
| `findAll(input, pattern)`            | Returns all matches as a list                |
| `replaceAll(input, pattern, repl)`   | Replaces all matches                         |
| `replaceFirst(input, pattern, repl)` | Replaces the first match                     |
| `split(input, pattern)`              | Splits by regex pattern into a list          |
| `capture(input, pattern)`            | Returns capture groups of the first match    |
| `countMatches(input, pattern)`       | Returns the number of matches                |

### `shell`

| Function           | Description                                     |
| ------------------ | ----------------------------------------------- |
| `execute(cmd)`     | Runs a shell command and returns stdout         |
| `executeCode(cmd)` | Runs a shell command and returns the exit code  |
| `getenv(name)`     | Returns an environment variable value           |
| `hasenv(name)`     | Returns true if the environment variable exists |
| `osName()`         | Returns the OS name                             |
| `isWindows()`      | True if running on Windows                      |
| `isLinux()`        | True if running on Linux                        |
| `isMac()`          | True if running on macOS                        |
| `cwd()`            | Current working directory                       |
| `username()`       | Current OS username                             |
| `homedir()`        | Home directory path                             |

### `process`

| Function                 | Description                                                        |
| ------------------------ | ------------------------------------------------------------------ |
| `processStart(cmd)`      | Starts a background process, returns an ID                         |
| `processAlive(id)`       | True if the process is still running                               |
| `processDone(id)`        | True if the process has finished (returns true for unknown IDs)    |
| `processWait(id)`        | Waits for the process to finish, returns exit code                 |
| `processKill(id)`        | Terminates the process                                             |
| `processOutput(id)`      | Returns buffered stdout of the process                             |
| `processReadPartial(id)` | Reads available stdout non-blocking, returns `""` if nothing ready |
| `processExitCode(id)`    | Returns the exit code of a finished process                        |
| `pid()`                  | Returns the PID of the current process                             |
| `listProcesses()`        | Returns a list of all running PIDs                                 |
| `processInfo(pid)`       | Returns the command of a process by PID                            |
| `sleep(ms)`              | Pauses execution for the given number of milliseconds              |

### `bytes`

| Function               | Description                                            |
| ---------------------- | ------------------------------------------------------ |
| `newBytes(size)`       | Creates a zero-filled byte array                       |
| `fromString(str)`      | UTF-8 encodes a string into bytes                      |
| `fromList(list)`       | Creates bytes from a list of numbers (0–255)           |
| `fromHex(hex)`         | Parses a lowercase hex string into bytes               |
| `fromBase64(str)`      | Decodes a Base64 string into bytes                     |
| `size(b)`              | Returns the length of the byte array                   |
| `get(b, index)`        | Returns the byte at `index` as a number (0–255)        |
| `set(b, index, value)` | Returns a new byte array with one byte replaced        |
| `slice(b, start, end)` | Returns a sub-array from `start` to `end` (exclusive)  |
| `concat(b1, b2)`       | Concatenates two byte arrays                           |
| `copy(b)`              | Returns an independent copy                            |
| `fill(b, value)`       | Returns a new byte array with all bytes set to `value` |
| `toString(b)`          | UTF-8 decodes bytes into a string                      |
| `toList(b)`            | Converts bytes to a list of numbers (0–255)            |
| `toHex(b)`             | Returns the bytes as a lowercase hex string            |
| `toBase64(b)`          | Encodes bytes as a Base64 string                       |
| `readFile(path)`       | Reads a file as raw bytes                              |
| `writeFile(path, b)`   | Writes raw bytes to a file                             |

### `crypto`

Cryptographic hash functions and UUID generation. No external dependencies — uses Java's built-in `java.security` and `javax.crypto`.

| Function                   | Description                                             |
| -------------------------- | ------------------------------------------------------- |
| `md5(str)`                 | Returns the MD5 hex digest of `str`                     |
| `sha1(str)`                | Returns the SHA-1 hex digest                            |
| `sha256(str)`              | Returns the SHA-256 hex digest (64 hex characters)      |
| `sha512(str)`              | Returns the SHA-512 hex digest (128 hex characters)     |
| `hmacSha256(key, message)` | HMAC-SHA256 of `message` signed with `key`              |
| `uuid()`                   | Generates a random UUID v4 with dashes                  |
| `uuidNoDashes()`           | Generates a random UUID v4 as a 32-character hex string |

### `path`

Cross-platform path manipulation. All functions return strings — they do not access the filesystem.

| Function             | Description                                                   |
| -------------------- | ------------------------------------------------------------- |
| `join(...parts)`     | Joins path segments with the system separator (variadic)      |
| `normalize(path)`    | Resolves `.` and `..` in a path without filesystem access     |
| `resolve(base, rel)` | Resolves `rel` relative to `base`                             |
| `relative(from, to)` | Returns `to` expressed relative to `from`                     |
| `absolute(path)`     | Returns the absolute path (relative to the current directory) |
| `parent(path)`       | Returns the parent directory, or `""` if none                 |
| `fileName(path)`     | Returns the file name (last segment), or `""`                 |
| `stem(path)`         | File name without its extension                               |
| `extension(path)`    | Extension without the dot, or `""` if none                    |
| `isAbsolute(path)`   | True if the path is absolute                                  |
| `split(path)`        | Returns a list of all path segments                           |

### `csv`

CSV parsing and serialization. Handles quoted fields and embedded commas.

| Function                   | Description                                                    |
| -------------------------- | -------------------------------------------------------------- |
| `parse(csvStr)`            | Parses CSV into a list of lists (each row = list of strings)   |
| `parseWithHeaders(csvStr)` | Parses CSV into a list of maps; first row becomes the map keys |
| `stringify(data)`          | Serializes a list of lists back into a CSV string              |
| `column(data, index)`      | Extracts column `index` from all rows as a list                |
| `parseRow(line)`           | Parses a single CSV line into a list (quoted-field-aware)      |
| `rowCount(csvStr)`         | Returns the number of non-empty rows                           |

### `term`

ANSI terminal formatting. All functions wrap text in ANSI escape sequences and are pure string operations — they do not print anything themselves.

| Function          | Description                                      |
| ----------------- | ------------------------------------------------ |
| `red(text)`       | Red foreground                                   |
| `green(text)`     | Green foreground                                 |
| `yellow(text)`    | Yellow foreground                                |
| `blue(text)`      | Blue foreground                                  |
| `magenta(text)`   | Magenta foreground                               |
| `cyan(text)`      | Cyan foreground                                  |
| `white(text)`     | White foreground                                 |
| `bold(text)`      | Bold style                                       |
| `dim(text)`       | Dim / faint style                                |
| `italic(text)`    | Italic style                                     |
| `underline(text)` | Underline style                                  |
| `stripAnsi(text)` | Removes all ANSI escape codes from `text`        |
| `clear()`         | Returns the ANSI sequence that clears the screen |

Example:

```
import term as t;
println(t.green("OK") " — " t.bold("done"));
```

### `zip`

Compression and ZIP archive operations. Operates on `bytes` values.

| Function                         | Description                                     |
| -------------------------------- | ----------------------------------------------- |
| `gzipCompress(bytes)`            | Compresses a bytes value with GZIP              |
| `gzipDecompress(bytes)`          | Decompresses a GZIP-compressed bytes value      |
| `deflate(bytes)`                 | Compresses with DEFLATE (raw)                   |
| `inflate(bytes)`                 | Decompresses DEFLATE-compressed bytes           |
| `createZip(outputPath, paths)`   | Creates a ZIP archive from a list of file paths |
| `extractZip(zipPath, outputDir)` | Extracts a ZIP archive into the given directory |

### `toml`

Parses TOML configuration files. Uses Mira's built-in TOML parser — the same one used for `mira.toml`.

| Function             | Description                                                    |
| -------------------- | -------------------------------------------------------------- |
| `parse(tomlStr)`     | Parses a TOML string, returns a map                            |
| `parseFile(path)`    | Reads and parses a TOML file, returns a map                    |
| `get(map, key)`      | Gets a value by key, or `null` if missing                      |
| `getArray(map, key)` | Gets a value as a list, or an empty list if missing/wrong type |
| `has(map, key)`      | True if `key` exists in the map                                |

### `random`

Stateful random number generation. The `Random` instance persists across calls within the same import scope.

| Function              | Description                                               |
| --------------------- | --------------------------------------------------------- |
| `seed(n)`             | Seeds the RNG with integer `n` for reproducible sequences |
| `next()`              | Returns a random float in `[0.0, 1.0)`                    |
| `nextInt(min, max)`   | Returns a random integer in `[min, max)`                  |
| `nextFloat(min, max)` | Returns a random float in `[min, max)`                    |
| `nextBool()`          | Returns `true` or `false` with equal probability          |
| `nextGaussian()`      | Returns a Gaussian-distributed value (μ=0, σ=1)           |
| `shuffle(list)`       | Returns a randomly shuffled copy of the list              |
| `pick(list)`          | Returns one random element from the list                  |
| `sample(list, n)`     | Returns `n` unique random elements (without replacement)  |

### `url`

URL parsing, building, and encoding.

| Function                           | Description                                                                                     |
| ---------------------------------- | ----------------------------------------------------------------------------------------------- |
| `parse(urlStr)`                    | Parses a URL into a map with keys `scheme`, `host`, `port`, `path`, `query`, `fragment`, `user` |
| `build(scheme, host, path, query)` | Assembles a URL string from its parts                                                           |
| `getParam(urlStr, key)`            | Returns the value of query parameter `key`, or `null`                                           |
| `getParams(urlStr)`                | Returns all query parameters as a map                                                           |
| `encode(str)`                      | URL-encodes a string (percent-encoding)                                                         |
| `decode(str)`                      | Decodes a percent-encoded string                                                                |
| `isValid(str)`                     | True if `str` is a syntactically valid URL                                                      |

### `number`

Number formatting and base conversion.

| Function                    | Description                                                         |
| --------------------------- | ------------------------------------------------------------------- |
| `toFixed(n, decimals)`      | Returns `n` formatted with exactly `decimals` decimal places        |
| `toHex(n)`                  | Returns `n` as an uppercase hexadecimal string, e.g. `"FF"`         |
| `toBinary(n)`               | Returns `n` as a binary string, e.g. `"1010"`                       |
| `toOctal(n)`                | Returns `n` as an octal string, e.g. `"17"`                         |
| `toScientific(n, decimals)` | Returns `n` in scientific notation, e.g. `"3.14e+10"`               |
| `withCommas(n)`             | Returns `n` formatted with thousands separators, e.g. `"1,234,567"` |
| `fromHex(str)`              | Parses a hex string into a number                                   |
| `fromBinary(str)`           | Parses a binary string into a number                                |
| `fromOctal(str)`            | Parses an octal string into a number                                |
| `isInteger(n)`              | True if `n` has no fractional part                                  |

### `set`

Set operations on deduplicated lists. Sets are represented as plain lists with no duplicate elements. All mutating operations return a new set without modifying the input.

| Function                   | Description                                             |
| -------------------------- | ------------------------------------------------------- |
| `newSet()`                 | Creates an empty set                                    |
| `add(set, value)`          | Returns a new set with `value` added (no-op if present) |
| `remove(set, value)`       | Returns a new set without `value`                       |
| `has(set, value)`          | True if `value` is in the set                           |
| `size(set)`                | Returns the number of elements                          |
| `union(set1, set2)`        | Returns all elements from both sets (deduplicated)      |
| `intersection(set1, set2)` | Returns only elements in both sets                      |
| `difference(set1, set2)`   | Returns elements in `set1` that are not in `set2`       |
| `toList(set)`              | Returns the set as a list                               |
| `fromList(list)`           | Converts a list to a set (removes duplicates)           |

### `log`

Stateful logger with level filtering and optional file output. Each import scope has its own logger instance.

| Function          | Description                                                          |
| ----------------- | -------------------------------------------------------------------- |
| `debug(message)`  | Prints `[DEBUG] message` to stdout (only if level ≤ `debug`)         |
| `info(message)`   | Prints `[INFO] message` to stdout (only if level ≤ `info`)           |
| `warn(message)`   | Prints `[WARN] message` to stderr (only if level ≤ `warn`)           |
| `error(message)`  | Prints `[ERROR] message` to stderr (only if level ≤ `error`)         |
| `setLevel(level)` | Sets the minimum log level: `"debug"`, `"info"`, `"warn"`, `"error"` |
| `toFile(path)`    | Redirects all subsequent log output to the given file (append mode)  |

Default level is `debug` — all messages are shown. Calling `setLevel("warn")` suppresses `debug` and `info` messages.

Example:

```
import log as log;

log.setLevel("info");
log.debug("ignored");
log.info("server started");
log.warn("low memory");
```

### `time`

Millisecond-precision timing utilities.

| Function           | Description                                                     |
| ------------------ | --------------------------------------------------------------- |
| `now()`            | Returns the current time as milliseconds since the Unix epoch   |
| `elapsed(startMs)` | Returns the milliseconds elapsed since `startMs`                |
| `sleep(ms)`        | Pauses execution for `ms` milliseconds                          |
| `format(ms)`       | Formats a duration: `"42ms"`, `"30s"`, `"2m 30s"`, `"1h 0m 0s"` |
| `fromSeconds(s)`   | Converts seconds to milliseconds                                |
| `fromMinutes(m)`   | Converts minutes to milliseconds                                |
| `fromHours(h)`     | Converts hours to milliseconds                                  |

Example — measure how long an operation takes:

```
import time as time;

var start : time.now();
doWork();
println("took: " time.format(time.elapsed($start)));
```

---

