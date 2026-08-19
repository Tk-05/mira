package com.mira.lsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.mira.parser.nodes.Node;
import com.mira.parser.nodes.expression.Expression.ObjectExpression;
import com.mira.parser.nodes.expression.Expression.StructExpression;
import com.mira.parser.nodes.statement.Statement;
import com.mira.parser.nodes.statement.Statement.ComptimeBlock;

public class HoverProvider {

    static final Map<String, String> STDLIB_DOCS;

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
        // process
        STDLIB_DOCS.put("installCrashLog", "**process.installCrashLog(path)** — Tees this process's stderr (including the crash dump) to an append-mode file, in addition to the console. Returns `false` instead of throwing if the file can't be opened");
        STDLIB_DOCS.put("uninstallCrashLog", "**process.uninstallCrashLog()** — Restores stderr to what it was before `installCrashLog`. Returns `false` if no crash log is currently installed");
        // thread
        STDLIB_DOCS.put("newMutex", "**thread.newMutex()** — Creates a new mutex for use with `lock`");
        // keywords
        STDLIB_DOCS.put("static_assert", "**static_assert(condition)**  \n**static_assert(condition, message)**\n\nEvaluates `condition` at the point of execution and throws error **E308** if it is falsy. At the top level this runs before user code starts (after `comptime` constants are available), making it a compile-time guard. Inside functions it runs on every call.\n\n```mira\nstatic_assert(SIZE > 0, \"SIZE must be positive\");\n```");
        // bytes
        STDLIB_DOCS.put("newBytes", "**bytes.newBytes(size)** — Creates a zero-filled byte array of the given size");
        STDLIB_DOCS.put("fromString", "**bytes.fromString(str)** — Encodes a string to bytes (UTF-8)");
        STDLIB_DOCS.put("fromList", "**bytes.fromList(list)** — Creates a byte array from a list of numbers (0–255)");
        STDLIB_DOCS.put("fromHex", "**bytes.fromHex(hex)** — Parses a hex string into a byte array");
        STDLIB_DOCS.put("fromBase64", "**bytes.fromBase64(str)** — Decodes a Base64 string into a byte array");
        STDLIB_DOCS.put("toList", "**bytes.toList(b)** — Converts a byte array to a list of numbers (0–255)");
        STDLIB_DOCS.put("toHex", "**bytes.toHex(b)** — Returns the byte array as a lowercase hex string");
        STDLIB_DOCS.put("toBase64", "**bytes.toBase64(b)** — Encodes a byte array as a Base64 string");
    }

    public static Hover provide(List<Node> ast, String content, Position pos) {
        String word = wordAt(content, pos);
        if (word == null || word.isBlank()) {
            return null;
        }

        if (isFieldAccess(content, pos)) {
            String objectName = DefinitionProvider.objectBefore(content, pos);
            Hover fieldHover = hoverForField(ast, word, objectName, pos.getLine() + 1);
            if (fieldHover != null) {
                return fieldHover;
            }
            return hover("**." + word + "** — field access");
        }

        Hover found = hoverScoped(ast, word, pos.getLine() + 1);
        if (found != null) {
            return found;
        }

        String stdlibDoc = STDLIB_DOCS.get(word);
        if (stdlibDoc != null) {
            return hover(stdlibDoc);
        }

        return null;
    }

    /**
     * A lexical scope: the container statement that introduces it (null for
     * top-level) and its body.
     */
    private record Scope(Node owner, List<Node> body) {

    }

    /**
     * Resolves a plain (non-field) identifier reference at {@code cursorLine}
     * by walking outward through the chain of lexical scopes actually enclosing
     * the cursor - innermost first - so an inner declaration correctly shadows
     * an unrelated same-named declaration elsewhere in the file (e.g. in a
     * sibling branch, or at the top level), instead of returning whichever
     * declaration happens to appear first in AST traversal order regardless of
     * scope.
     */
    private static Hover hoverScoped(List<Node> ast, String name, int cursorLine) {
        for (Scope scope : buildScopeChain(ast, cursorLine)) {
            Hover found = hoverInScopeLevel(scope, name, cursorLine);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Scope> buildScopeChain(List<Node> ast, int cursorLine) {
        List<Scope> chain = new ArrayList<>();
        Scope current = new Scope(null, ast);
        chain.add(current);
        Scope child;
        while ((child = enclosingChild(current, cursorLine)) != null) {
            chain.add(0, child);
            current = child;
        }
        return chain;
    }

    private static Scope enclosingChild(Scope scope, int cursorLine) {
        for (Node n : scope.body()) {
            if (!(n instanceof Statement s) || s.line <= 0 || s.endLine <= 0
                    || cursorLine < s.line || cursorLine > s.endLine) {
                continue;
            }
            List<Node> child = childBodyAt(n, cursorLine);
            if (child != null) {
                return new Scope(n, child);
            }
        }
        return null;
    }

    private static List<Node> childBodyAt(Node n, int cursorLine) {
        if (n instanceof Statement.FuncDecl f) {
            return f.getBody();
        }
        if (n instanceof Statement.If stmt) {
            List<Node> branch = branchContaining(stmt.getThenBody(), cursorLine);
            return branch != null ? branch : branchContaining(stmt.getElseBody(), cursorLine);
        }
        if (n instanceof Statement.Loop stmt) {
            return stmt.getBody();
        }
        if (n instanceof Statement.While stmt) {
            return stmt.getBody();
        }
        if (n instanceof Statement.Block stmt) {
            return stmt.getBody();
        }
        if (n instanceof Statement.Switch stmt) {
            for (Statement.SwitchCase sc : stmt.getCases()) {
                List<Node> branch = branchContaining(sc.getBody(), cursorLine);
                if (branch != null) {
                    return branch;
                }
            }
            return branchContaining(stmt.getDefaultBody(), cursorLine);
        }
        if (n instanceof Statement.TryCatch stmt) {
            List<Node> branch = branchContaining(stmt.getTryBody(), cursorLine);
            if (branch != null) {
                return branch;
            }
            for (Statement.CatchClause cc : stmt.getCatchClauses()) {
                branch = branchContaining(cc.getBody(), cursorLine);
                if (branch != null) {
                    return branch;
                }
            }
            return branchContaining(stmt.getFinallyBody(), cursorLine);
        }
        if (n instanceof Statement.Lock stmt) {
            return stmt.getBody();
        }
        if (n instanceof ComptimeBlock stmt) {
            return stmt.getBody();
        }
        return null;
    }

    /**
     * Whether {@code cursorLine} falls within the line span actually covered by
     * this specific body's statements.
     */
    private static List<Node> branchContaining(List<Node> body, int cursorLine) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (Node n : body) {
            if (n instanceof Statement s && s.line > 0) {
                min = Math.min(min, s.line);
                max = Math.max(max, s.endLine > 0 ? s.endLine : s.line);
            }
        }
        if (min == Integer.MAX_VALUE || max < 0) {
            return null;
        }
        return (cursorLine >= min && cursorLine <= max) ? body : null;
    }

    private static Hover hoverInScopeLevel(Scope scope, String name, int cursorLine) {
        if (scope.owner() instanceof Statement.Loop loop) {
            if (loop.isForeach()) {
                Statement.VarDecl iter = loop.getIterator();
                if (iter.getName().equals(name)) {
                    return hover("```mira\nvar " + iter.getName() + "\n```");
                }
            } else {
                Hover h = findDirectHoverInBody(loop.getVarDecls(), name, cursorLine);
                if (h != null) {
                    return h;
                }
            }
        }
        return findDirectHoverInBody(scope.body(), name, cursorLine);
    }

    /**
     * Searches only the direct statements of {@code body} (not nested blocks)
     * for a declaration of {@code name}, preferring the one closest to (and at
     * or before) {@code cursorLine} - the nearest enclosing declaration -
     * falling back to the nearest one after it if none precede.
     */
    private static Hover findDirectHoverInBody(List<Node> body, String name, int cursorLine) {
        Hover before = null;
        int beforeLine = -1;
        Hover after = null;
        int afterLine = Integer.MAX_VALUE;
        for (Node n : body) {
            Hover candidate = null;
            int declLine = -1;
            if (n instanceof Statement.FuncDecl f && f.getName().equals(name)) {
                candidate = hoverForFuncDeclSelf(f);
                declLine = f.line;
            } else if (n instanceof Statement.VarDecl v && v.getName().equals(name)) {
                candidate = hoverForVarDeclSelf(v);
                declLine = v.line;
            } else if (n instanceof Statement.VarDestructure vd && vd.getNames().contains(name)) {
                candidate = hover("```mira\nvar " + name + "\n```\n*destructured*");
                declLine = vd.line;
            } else if (n instanceof ComptimeBlock comptime) {
                for (Node bodyNode : comptime.getBody()) {
                    if (bodyNode instanceof Statement.VarDecl v && v.getName().equals(name)) {
                        candidate = hover("```mira\ncomptime const " + v.getName()
                                + "\n```\n*compile-time constant*");
                        declLine = v.line;
                        break;
                    }
                }
            }
            if (candidate == null || declLine <= 0) {
                continue;
            }
            if (declLine <= cursorLine && declLine > beforeLine) {
                before = candidate;
                beforeLine = declLine;
            } else if (declLine > cursorLine && declLine < afterLine) {
                after = candidate;
                afterLine = declLine;
            }
        }
        return before != null ? before : after;
    }

    private static Hover hoverForFuncDeclSelf(Statement.FuncDecl f) {
        String params = f.getParameters().stream()
                .map(p -> p.name() + (p.type() != null ? " : " + p.type() : ""))
                .collect(Collectors.joining(", "));
        String prefix = (f.isAsync() ? "async " : "") + (f.isPure() ? "pure " : "");
        String returnPart = f.getReturnType() != null ? " -> " + f.getReturnType() : "";
        String sig = prefix + "fn " + f.getName() + "(" + params + ")" + returnPart;
        return hover("```mira\n" + sig + "\n```");
    }

    private static Hover hoverForVarDeclSelf(Statement.VarDecl v) {
        String kind = v.isConst() ? "const" : "var";
        if (v.getInitializer() instanceof com.mira.parser.nodes.expression.Expression.ObjectExpression obj) {
            StringBuilder sb = new StringBuilder("```mira\n")
                    .append(kind).append(" ").append(v.getName()).append(" {\n");
            for (Statement.VarDecl f : obj.getVarDecls()) {
                sb.append("    ").append(f.isConst() ? "const" : "var")
                        .append(" ").append(f.getName())
                        .append(f.getType() != null ? " : " + f.getType() : "").append("\n");
            }
            for (Statement.FuncDecl m : obj.getMethods()) {
                String params = m.getParameters().stream()
                        .map(p -> p.name() + (p.type() != null ? " : " + p.type() : ""))
                        .collect(Collectors.joining(", "));
                sb.append("    fn ").append(m.getName())
                        .append("(").append(params).append(")\n");
            }
            sb.append("}\n```");
            return hover(sb.toString());
        }
        if (v.getInitializer() instanceof StructExpression st) {
            StringBuilder sb = new StringBuilder("```mira\n")
                    .append(kind).append(" ").append(v.getName()).append(" struct {\n");
            for (Statement.VarDecl f : st.getVarDecls()) {
                sb.append("    ").append(f.isConst() ? "const" : "var")
                        .append(" ").append(f.getName())
                        .append(f.getType() != null ? " : " + f.getType() : "").append("\n");
            }
            for (Statement.FuncDecl m : st.getMethods()) {
                String params = m.getParameters().stream()
                        .map(p -> p.name() + (p.type() != null ? " : " + p.type() : ""))
                        .collect(Collectors.joining(", "));
                sb.append("    fn ").append(m.getName())
                        .append("(").append(params).append(")\n");
            }
            sb.append("}\n```");
            return hover(sb.toString());
        }
        String typePart = v.getType() != null ? " : " + v.getType() : "";
        return hover("```mira\n" + kind + " " + v.getName() + typePart + "\n```");
    }

    static boolean isFieldAccess(String content, Position pos) {
        String[] lines = content.split("\n", -1);
        if (pos.getLine() >= lines.length) {
            return false;
        }
        String line = lines[pos.getLine()];
        int col = Math.min(pos.getCharacter(), line.length());
        int start = col;
        while (start > 0 && isWordChar(line.charAt(start - 1))) {
            start--;
        }
        if (start == 0) {
            return false;
        }
        char before = line.charAt(start - 1);
        if (before == '?') {
            return true;
        }
        if (before != '.') {
            return false;
        }
        return start < 2 || line.charAt(start - 2) != '.';
    }

    private static Hover hoverForField(List<Node> ast, String fieldName, String objectName, int cursorLine) {
        if (objectName != null) {
            Node type = DefinitionProvider.resolveObjectType(ast, objectName, cursorLine);
            if (type != null) {
                // objectName resolved to a specific, known declaration in scope -
                // trust that resolution rather than falling through to a blind
                // whole-file field search, which could land on an unrelated
                // same-named field on a completely different (shadowed) object.
                return searchNodeForField(type, fieldName);
            }
        }
        for (Node n : ast) {
            Hover h = searchNodeForField(n, fieldName);
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    private static Hover searchNodeForField(Node n, String fieldName) {
        if (n instanceof ObjectExpression obj) {
            for (Statement.VarDecl f : obj.getVarDecls()) {
                if (f.getName().equals(fieldName)) {
                    String kind = f.isConst() ? "const" : "var";
                    String typePart = f.getType() != null ? " : " + f.getType() : "";
                    return hover("```mira\n" + kind + " " + f.getName() + typePart + "\n```\n*object field*");
                }
            }
            for (Statement.FuncDecl m : obj.getMethods()) {
                if (m.getName().equals(fieldName)) {
                    String params = m.getParameters().stream()
                            .map(p -> p.name() + (p.type() != null ? " : " + p.type() : ""))
                            .collect(Collectors.joining(", "));
                    String returnPart = m.getReturnType() != null ? " -> " + m.getReturnType() : "";
                    return hover("```mira\nfn " + m.getName() + "(" + params + ")" + returnPart
                            + "\n```\n*object method*");
                }
            }
        }
        if (n instanceof StructExpression st) {
            for (Statement.VarDecl f : st.getVarDecls()) {
                if (f.getName().equals(fieldName)) {
                    String kind = f.isConst() ? "const" : "var";
                    String typePart = f.getType() != null ? " : " + f.getType() : "";
                    return hover("```mira\n" + kind + " " + f.getName() + typePart + "\n```\n*struct field*");
                }
            }
            for (Statement.FuncDecl m : st.getMethods()) {
                if (m.getName().equals(fieldName)) {
                    String params = m.getParameters().stream()
                            .map(p -> p.name() + (p.type() != null ? " : " + p.type() : ""))
                            .collect(Collectors.joining(", "));
                    String returnPart = m.getReturnType() != null ? " -> " + m.getReturnType() : "";
                    return hover("```mira\nfn " + m.getName() + "(" + params + ")" + returnPart
                            + "\n```\n*struct method*");
                }
            }
        }
        if (n instanceof Statement.VarDecl vd && vd.getInitializer() != null) {
            return searchNodeForField(vd.getInitializer(), fieldName);
        }
        if (n instanceof Statement.FuncDecl f) {
            for (Node bodyNode : f.getBody()) {
                Hover h = searchNodeForField(bodyNode, fieldName);
                if (h != null) {
                    return h;
                }
            }
        }
        return null;
    }

    static Range wordRangeAt(String content, Position pos) {
        String[] lines = content.split("\n", -1);
        if (pos.getLine() >= lines.length) {
            return null;
        }
        String line = lines[pos.getLine()];
        int col = Math.min(pos.getCharacter(), line.length());

        int start = col;
        while (start > 0 && isWordChar(line.charAt(start - 1))) {
            start--;
        }

        int end = col;
        while (end < line.length() && isWordChar(line.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }
        return new Range(new Position(pos.getLine(), start), new Position(pos.getLine(), end));
    }

    static String wordAt(String content, Position pos) {
        String[] lines = content.split("\n", -1);
        if (pos.getLine() >= lines.length) {
            return null;
        }
        String line = lines[pos.getLine()];
        int col = pos.getCharacter();
        if (col > line.length()) {
            col = line.length();
        }

        int start = col;
        while (start > 0 && isWordChar(line.charAt(start - 1))) {
            start--;
        }

        int end = col;
        while (end < line.length() && isWordChar(line.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }
        return line.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static Hover hover(String markdown) {
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, markdown));
    }
}
