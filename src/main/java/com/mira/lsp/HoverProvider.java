package com.mira.lsp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.Parameter;
import com.mira.parser.nodes.statement.Statement;

public class HoverProvider {

    private static final Map<String, String> KEYWORD_DOCS = Map.ofEntries(
            Map.entry("fn", "**fn** — Function declaration"),
            Map.entry("var", "**var** — Mutable variable declaration"),
            Map.entry("const", "**const** — Immutable constant declaration"),
            Map.entry("pure", "**pure fn** — Pure function (result is cached for same arguments)"),
            Map.entry("async", "**async fn** — Asynchronous function"),
            Map.entry("spawn", "**spawn(fn)** — Starts an async task, returns a `Promise`"),
            Map.entry("await", "**await(promise)** — Waits for the result of an async task"),
            Map.entry("lock", "**lock(mutex) { ... }** — Exclusive access via mutex"),
            Map.entry("return", "**return** — Returns a value from a function"),
            Map.entry("if", "**if** — Conditional statement"),
            Map.entry("else", "**else** — Alternative branch of an if statement"),
            Map.entry("while", "**while** — Loop while condition is true"),
            Map.entry("for", "**for** — C-style loop"),
            Map.entry("foreach", "**foreach** — Iterate over a collection"),
            Map.entry("in", "**in** — Used in foreach to iterate over a collection"),
            Map.entry("break", "**break** — Exit the current loop"),
            Map.entry("continue", "**continue** — Skip to the next loop iteration"),
            Map.entry("switch", "**switch** — Pattern matching on a value"),
            Map.entry("case", "**case** — A branch in a switch statement"),
            Map.entry("default", "**default** — Default branch in a switch statement"),
            Map.entry("try", "**try** — Try block for error handling"),
            Map.entry("catch", "**catch** — Catch block for error handling"),
            Map.entry("finally", "**finally** — Always-executed block after try/catch"),
            Map.entry("throw", "**throw** — Throw an exception"),
            Map.entry("import", "**import** — Import a module or stdlib"),
            Map.entry("module", "**module** — Declare the module name for this file"),
            Map.entry("as", "**as** — Alias for an import"),
            Map.entry("enum", "**enum** — Declare an enumeration"),
            Map.entry("typeof", "**typeof(value)** — Returns the type of a value as a string"),
            Map.entry("true", "**true** — Boolean literal"),
            Map.entry("false", "**false** — Boolean literal"),
            Map.entry("null", "**null** — Null value")
    );

    private static final Map<String, String> STDLIB_DOCS;

    static {
        STDLIB_DOCS = new HashMap<>();
        // string
        STDLIB_DOCS.put("charAt", "**string.charAt(str, index)** — Returns the character at the given index");
        STDLIB_DOCS.put("indexOf", "**string.indexOf(str, sub)** — Returns the index of `sub` in `str`, or -1");
        STDLIB_DOCS.put("trim", "**string.trim(str)** — Removes leading and trailing whitespace");
        STDLIB_DOCS.put("split", "**string.split(str, delimiter)** — Splits `str` by delimiter into an array");
        STDLIB_DOCS.put("substr", "**string.substr(str, start, end)** — Returns substring from `start` to `end`");
        STDLIB_DOCS.put("strEqual", "**string.strEqual(a, b)** — Returns `true` if strings are equal");
        STDLIB_DOCS.put("replace", "**string.replace(str, old, new)** — Replaces all occurrences of `old` with `new`");
        STDLIB_DOCS.put("upper", "**string.upper(str)** — Returns `str` converted to uppercase");
        STDLIB_DOCS.put("lower", "**string.lower(str)** — Returns `str` converted to lowercase");
        STDLIB_DOCS.put("startsWith", "**string.startsWith(str, prefix)** — Returns `true` if `str` starts with `prefix`");
        STDLIB_DOCS.put("endsWith", "**string.endsWith(str, suffix)** — Returns `true` if `str` ends with `suffix`");
        STDLIB_DOCS.put("contains", "**string.contains(str, sub) / collection.contains(col, val)** — Checks if value is present");
        STDLIB_DOCS.put("repeat", "**string.repeat(str, n)** — Returns `str` repeated `n` times");
        STDLIB_DOCS.put("toNumber", "**string.toNumber(str)** — Parses `str` as a number");
        STDLIB_DOCS.put("padLeft", "**string.padLeft(str, width)** — Left-pads `str` with spaces to `width`");
        STDLIB_DOCS.put("padRight", "**string.padRight(str, width)** — Right-pads `str` with spaces to `width`");
        STDLIB_DOCS.put("isNumeric", "**string.isNumeric(str)** — Returns `true` if `str` is a valid number");
        // collection
        STDLIB_DOCS.put("size", "**collection.size(col)** — Returns the number of elements");
        STDLIB_DOCS.put("push", "**collection.push(col, val)** — Appends `val` to the collection");
        STDLIB_DOCS.put("pop", "**collection.pop(col)** — Removes and returns the last element");
        STDLIB_DOCS.put("first", "**collection.first(col)** — Returns the first element");
        STDLIB_DOCS.put("last", "**collection.last(col)** — Returns the last element");
        STDLIB_DOCS.put("slice", "**collection.slice(col, start, end)** — Returns a sub-list from `start` to `end`");
        STDLIB_DOCS.put("reverse", "**collection.reverse(col)** — Returns a reversed copy");
        STDLIB_DOCS.put("concat", "**collection.concat(col1, col2)** — Concatenates two collections");
        STDLIB_DOCS.put("flatten", "**collection.flatten(col)** — Flattens one level of nesting");
        STDLIB_DOCS.put("join", "**collection.join(col, sep)** — Joins elements into a string with `sep`");
        STDLIB_DOCS.put("newList", "**collection.newList()** — Creates an empty list");
        STDLIB_DOCS.put("remove", "**collection.remove(col, index)** — Removes element at `index`");
        STDLIB_DOCS.put("map", "**collection.map(col, fn)** — Returns a new list with `fn` applied to each element");
        STDLIB_DOCS.put("filter", "**collection.filter(col, fn)** — Returns elements where `fn(element)` is truthy");
        STDLIB_DOCS.put("reduce", "**collection.reduce(col, fn, init)** — Folds left: `fn(accumulator, element)`");
        STDLIB_DOCS.put("any", "**collection.any(col, fn)** — Returns `true` if at least one element satisfies `fn`");
        STDLIB_DOCS.put("all", "**collection.all(col, fn)** — Returns `true` if all elements satisfy `fn`");
        STDLIB_DOCS.put("count", "**collection.count(col, fn)** — Counts elements where `fn(element)` is truthy");
        STDLIB_DOCS.put("sortBy", "**collection.sortBy(col, fn)** — Sorts by key extracted with `fn`");
        STDLIB_DOCS.put("sort", "**collection.sort(col)** — Sorts numerically, falls back to string comparison");
        STDLIB_DOCS.put("unique", "**collection.unique(col)** — Removes duplicates, preserves insertion order");
        STDLIB_DOCS.put("sum", "**collection.sum(col)** — Returns the sum of all numeric elements");
        STDLIB_DOCS.put("avg", "**collection.avg(col)** — Returns the average of all numeric elements");
        STDLIB_DOCS.put("zip", "**collection.zip(col1, col2)** — Returns a list of `[a, b]` pairs");
        STDLIB_DOCS.put("fill", "**collection.fill(n, val)** — Creates a list of `n` copies of `val`");
        STDLIB_DOCS.put("min", "**collection.min(col) / math.min(a, b)** — Returns the minimum value");
        STDLIB_DOCS.put("max", "**collection.max(col) / math.max(a, b)** — Returns the maximum value");
        STDLIB_DOCS.put("take", "**collection.take(col, n)** — Returns the first `n` elements");
        STDLIB_DOCS.put("drop", "**collection.drop(col, n)** — Returns all elements except the first `n`");
        STDLIB_DOCS.put("findFirst", "**collection.findFirst(col, fn)** — Returns the first element where `fn(element)` is truthy");
        STDLIB_DOCS.put("chunk", "**collection.chunk(col, size)** — Splits into sub-lists of `size`");
        STDLIB_DOCS.put("groupBy", "**collection.groupBy(col, fn)** — Groups elements into a map by key from `fn`");
        // map
        STDLIB_DOCS.put("newMap", "**map.newMap()** — Creates an empty map");
        STDLIB_DOCS.put("mapSize", "**map.mapSize(map)** — Returns the number of entries");
        STDLIB_DOCS.put("mapHas", "**map.mapHas(map, key)** — Returns `true` if `key` exists");
        STDLIB_DOCS.put("mapRemove", "**map.mapRemove(map, key)** — Removes `key` from the map");
        STDLIB_DOCS.put("mapKeys", "**map.mapKeys(map)** — Returns a list of all keys");
        STDLIB_DOCS.put("mapValues", "**map.mapValues(map)** — Returns a list of all values");
        STDLIB_DOCS.put("mapSet", "**map.mapSet(map, key, value)** — Sets `key` to `value`, returns the map");
        STDLIB_DOCS.put("mapGet", "**map.mapGet(map, key)** — Returns the value for `key`, or null");
        STDLIB_DOCS.put("mapEntries", "**map.mapEntries(map)** — Returns a list of `[key, value]` pairs");
        STDLIB_DOCS.put("mapMerge", "**map.mapMerge(map1, map2)** — Merges two maps; `map2` values overwrite `map1`");
        STDLIB_DOCS.put("mapFromLists", "**map.mapFromLists(keys, values)** — Creates a map from parallel key/value lists");
        // math
        STDLIB_DOCS.put("pow", "**math.pow(base, exp)** — Returns `base` raised to the power of `exp`");
        STDLIB_DOCS.put("abs", "**math.abs(x)** — Returns the absolute value");
        STDLIB_DOCS.put("rand", "**math.rand()** — Returns a random double in [0, 1)");
        STDLIB_DOCS.put("randInt", "**math.randInt(min, max)** — Returns a random integer in [min, max]");
        STDLIB_DOCS.put("round", "**math.round(x)** — Rounds to the nearest integer");
        STDLIB_DOCS.put("floor", "**math.floor(x)** — Rounds down to the nearest integer");
        STDLIB_DOCS.put("ceil", "**math.ceil(x)** — Rounds up to the nearest integer");
        STDLIB_DOCS.put("sqrt", "**math.sqrt(x)** — Returns the square root");
        STDLIB_DOCS.put("cbrt", "**math.cbrt(x)** — Returns the cube root");
        STDLIB_DOCS.put("log", "**math.log(x)** — Returns the natural logarithm");
        STDLIB_DOCS.put("log10", "**math.log10(x)** — Returns the base-10 logarithm");
        STDLIB_DOCS.put("log2", "**math.log2(x)** — Returns the base-2 logarithm");
        STDLIB_DOCS.put("sin", "**math.sin(x)** — Returns the sine of `x` (radians)");
        STDLIB_DOCS.put("cos", "**math.cos(x)** — Returns the cosine of `x` (radians)");
        STDLIB_DOCS.put("tan", "**math.tan(x)** — Returns the tangent of `x` (radians)");
        STDLIB_DOCS.put("asin", "**math.asin(x)** — Returns the arcsine");
        STDLIB_DOCS.put("acos", "**math.acos(x)** — Returns the arccosine");
        STDLIB_DOCS.put("atan", "**math.atan(x)** — Returns the arctangent");
        STDLIB_DOCS.put("atan2", "**math.atan2(y, x)** — Returns the angle in radians");
        STDLIB_DOCS.put("toRad", "**math.toRad(deg)** — Converts degrees to radians");
        STDLIB_DOCS.put("toDeg", "**math.toDeg(rad)** — Converts radians to degrees");
        STDLIB_DOCS.put("sign", "**math.sign(x)** — Returns -1, 0, or 1");
        STDLIB_DOCS.put("clamp", "**math.clamp(val, min, max)** — Clamps `val` to [min, max]");
        STDLIB_DOCS.put("isNaN", "**math.isNaN(x)** — Returns `true` if `x` is NaN");
        STDLIB_DOCS.put("isInf", "**math.isInf(x)** — Returns `true` if `x` is infinite");
        STDLIB_DOCS.put("gcd", "**math.gcd(a, b)** — Returns the greatest common divisor");
        STDLIB_DOCS.put("lcm", "**math.lcm(a, b)** — Returns the least common multiple");
        STDLIB_DOCS.put("factorial", "**math.factorial(n)** — Returns `n!` (n must be ≤ 20)");
        STDLIB_DOCS.put("trunc", "**math.trunc(x)** — Truncates decimal digits toward zero");
        STDLIB_DOCS.put("hypot", "**math.hypot(a, b)** — Returns `sqrt(a² + b²)`");
        // net
        STDLIB_DOCS.put("httpGet", "**net.httpGet(url)** — Sends a GET request, returns the response body");
        STDLIB_DOCS.put("httpPost", "**net.httpPost(url, body, contentType)** — Sends a POST request");
        STDLIB_DOCS.put("httpPut", "**net.httpPut(url, body, contentType)** — Sends a PUT request");
        STDLIB_DOCS.put("httpDelete", "**net.httpDelete(url)** — Sends a DELETE request, returns the response body");
        STDLIB_DOCS.put("httpStatus", "**net.httpStatus(url)** — Returns the HTTP status code as a number");
        STDLIB_DOCS.put("httpHeader", "**net.httpHeader(url, header)** — Returns the value of a response header");
        STDLIB_DOCS.put("httpDownload", "**net.httpDownload(url, path)** — Downloads a file to `path`");
        STDLIB_DOCS.put("urlEncode", "**net.urlEncode(str)** — URL-encodes a string");
        STDLIB_DOCS.put("urlDecode", "**net.urlDecode(str)** — URL-decodes a string");
        // io
        STDLIB_DOCS.put("readFile", "**io.readFile(path)** — Reads a file and returns its contents as a string");
        STDLIB_DOCS.put("writeFile", "**io.writeFile(path, content)** — Writes `content` to a file");
        STDLIB_DOCS.put("fileExists", "**io.fileExists(path)** — Returns `true` if the file exists");
        STDLIB_DOCS.put("appendFile", "**io.appendFile(path, content)** — Appends `content` to a file");
        STDLIB_DOCS.put("listDir", "**io.listDir(path)** — Returns an array of file names in the directory");
        STDLIB_DOCS.put("mkdir", "**io.mkdir(path)** — Creates a directory (including parents)");
        STDLIB_DOCS.put("deleteFile", "**io.deleteFile(path)** — Deletes the file at `path`");
        // dateTime
        STDLIB_DOCS.put("now", "**dateTime.now()** — Returns the current date/time as an ISO string");
        STDLIB_DOCS.put("timestamp", "**dateTime.timestamp()** — Returns the current Unix timestamp (seconds)");
        STDLIB_DOCS.put("timestampMs", "**dateTime.timestampMs()** — Returns the current Unix timestamp (milliseconds)");
        STDLIB_DOCS.put("dateFormat", "**dateTime.dateFormat(date, pattern)** — Formats a date string with the given pattern");
        STDLIB_DOCS.put("year", "**dateTime.year()** — Returns the current year");
        STDLIB_DOCS.put("month", "**dateTime.month()** — Returns the current month (1-12)");
        STDLIB_DOCS.put("day", "**dateTime.day()** — Returns the current day of month");
        STDLIB_DOCS.put("hour", "**dateTime.hour()** — Returns the current hour (0-23)");
        STDLIB_DOCS.put("minute", "**dateTime.minute()** — Returns the current minute");
        STDLIB_DOCS.put("second", "**dateTime.second()** — Returns the current second");
        STDLIB_DOCS.put("dayOfWeek", "**dateTime.dayOfWeek()** — Returns the day of week as a string (e.g. MONDAY)");
        STDLIB_DOCS.put("dayOfYear", "**dateTime.dayOfYear()** — Returns the day of year (1-366)");
        STDLIB_DOCS.put("secondsSince", "**dateTime.secondsSince(date)** — Seconds elapsed since `date`");
        STDLIB_DOCS.put("fromEpoch", "**dateTime.fromEpoch(seconds)** — Converts a Unix timestamp to a date/time string");
        STDLIB_DOCS.put("addDays", "**dateTime.addDays(date, n)** — Returns a new date string `n` days after `date`");
        STDLIB_DOCS.put("dateDiff", "**dateTime.dateDiff(date1, date2)** — Returns the number of days between two dates");
        STDLIB_DOCS.put("isLeapYear", "**dateTime.isLeapYear(year)** — Returns `true` if `year` is a leap year");
        // json
        STDLIB_DOCS.put("jsonGet", "**json.jsonGet(json, key)** — Returns the value for `key` in a JSON object string");
        STDLIB_DOCS.put("jsonHas", "**json.jsonHas(json, key)** — Returns `true` if `key` exists in the JSON string");
        STDLIB_DOCS.put("jsonArray", "**json.jsonArray(json, key)** — Returns the array at `key` as a list");
        STDLIB_DOCS.put("jsonBuild", "**json.jsonBuild(keys, values)** — Builds a JSON object string from two lists");
        STDLIB_DOCS.put("jsonFormat", "**json.jsonFormat(json)** — Pretty-prints a JSON string");
        STDLIB_DOCS.put("jsonNested", "**json.jsonNested(json, parent, key)** — Returns a nested array");
        STDLIB_DOCS.put("jsonIndexOf", "**json.jsonIndexOf(list, val)** — Returns the index of `val` in a JSON list");
        STDLIB_DOCS.put("jsonKeys", "**json.jsonKeys(json)** — Returns an array of top-level keys in a JSON object");
        STDLIB_DOCS.put("jsonSize", "**json.jsonSize(json)** — Returns the number of top-level keys/elements");
        STDLIB_DOCS.put("jsonSet", "**json.jsonSet(json, key, value)** — Sets `key` to `value` in a JSON object string");
        // thread
        STDLIB_DOCS.put("newMutex", "**thread.newMutex()** — Creates a new mutex for use with `lock`");
    }

    public static Hover provide(List<Node> ast, String content, Position pos) {
        String word = wordAt(content, pos);
        if (word == null || word.isBlank()) return null;

        String stripped = word.startsWith("$") ? word.substring(1) : word;

        for (Node n : ast) {
            if (n instanceof Statement.FuncDecl f && f.getName().equals(stripped)) {
                String params = f.getParameters().stream()
                        .map(Parameter::name)
                        .collect(Collectors.joining(", "));
                String prefix = (f.isAsync() ? "async " : "") + (f.isPure() ? "pure " : "");
                String sig = prefix + "fn " + f.getName() + "(" + params + ")";
                return hover("```mira\n" + sig + "\n```");
            }
            if (n instanceof Statement.VarDecl v && v.getName().equals(stripped)) {
                String kind = v.isConst() ? "const" : "var";
                return hover("```mira\n" + kind + " $" + v.getName() + "\n```");
            }
        }

        String stdlibDoc = STDLIB_DOCS.get(word);
        if (stdlibDoc != null) {
            return hover(stdlibDoc);
        }

        String kwDoc = KEYWORD_DOCS.get(word);
        if (kwDoc != null) {
            return hover(kwDoc);
        }

        return null;
    }

    static String wordAt(String content, Position pos) {
        String[] lines = content.split("\n", -1);
        if (pos.getLine() >= lines.length) return null;
        String line = lines[pos.getLine()];
        int col = pos.getCharacter();
        if (col > line.length()) col = line.length();

        int start = col;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        if (start > 0 && line.charAt(start - 1) == '$') start--;

        int end = col;
        while (end < line.length() && isWordChar(line.charAt(end))) end++;

        if (start >= end) return null;
        return line.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static Hover hover(String markdown) {
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, markdown));
    }
}
