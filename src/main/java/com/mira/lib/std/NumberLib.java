package com.mira.lib.std;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mira.lib.Lib;
import com.mira.lib.NativeMethodProvider;
import com.mira.lib.NativeType;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class NumberLib implements Lib, NativeMethodProvider {

    @Override
    public Map<NativeType, List<String>> nativeMethods() {
        return Map.of(NativeType.NUMBER,
                List.of("toFixed", "toHex", "toBinary", "toOctal", "toScientific", "withCommas", "isInteger"),
                NativeType.STRING, List.of("fromHex", "fromBinary", "fromOctal"));
    }

    private static double toDouble(Object arg) {
        return Double.parseDouble(String.valueOf(arg));
    }

    private static long toLong(Object arg) {
        return (long) toDouble(arg);
    }

    @Override
    public void loadLib(Environment environment) {

        environment.define("toFixed", new NativeFunction(2, "n, decimals", args -> {
            double n = toDouble(args.get(0));
            int decimals = (int) toDouble(args.get(1));
            return String.format(Locale.US, "%." + decimals + "f", n);
        }));

        environment.define("toHex",
                new NativeFunction(1, "n", args -> Long.toHexString(toLong(args.get(0))).toUpperCase(Locale.US)));

        environment.define("toBinary", new NativeFunction(1, "n", args -> Long.toBinaryString(toLong(args.get(0)))));

        environment.define("toOctal", new NativeFunction(1, "n", args -> Long.toOctalString(toLong(args.get(0)))));

        environment.define("toScientific", new NativeFunction(2, "n, decimals", args -> {
            double n = toDouble(args.get(0));
            int decimals = (int) toDouble(args.get(1));
            return String.format(Locale.US, "%." + decimals + "e", n);
        }));

        environment.define("withCommas", new NativeFunction(1, "n", args -> {
            double n = toDouble(args.get(0));
            DecimalFormat df = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
            return df.format(n);
        }));

        environment.define("fromHex",
                new NativeFunction(1, "str", args -> (double) Long.parseLong(String.valueOf(args.get(0)), 16)));

        environment.define("fromBinary",
                new NativeFunction(1, "str", args -> (double) Long.parseLong(String.valueOf(args.get(0)), 2)));

        environment.define("fromOctal",
                new NativeFunction(1, "str", args -> (double) Long.parseLong(String.valueOf(args.get(0)), 8)));

        environment.define("isInteger", new NativeFunction(1, "n", args -> {
            double n = toDouble(args.get(0));
            return n == java.lang.Math.floor(n) && !Double.isInfinite(n);
        }));
    }
}
