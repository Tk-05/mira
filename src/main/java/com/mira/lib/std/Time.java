package com.mira.lib.std;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.values.NullValue;

public class Time implements Lib {

    @Override
    public void loadLib(Environment environment) {

        environment.define("now", new NativeFunction(0, args
                -> (double) System.currentTimeMillis()));

        environment.define("elapsed", new NativeFunction(1, "startMs", args -> {
            long start = (long) Double.parseDouble(String.valueOf(args.get(0)));
            return (double) (System.currentTimeMillis() - start);
        }));

        environment.define("sleep", new NativeFunction(1, "ms", args -> {
            try {
                long ms = (long) Double.parseDouble(String.valueOf(args.get(0)));
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return NullValue.INSTANCE;
        }));

        environment.define("format", new NativeFunction(1, "ms", args -> {
            long ms = (long) Double.parseDouble(String.valueOf(args.get(0)));
            if (ms < 1000) {
                return ms + "ms";
            }
            long seconds = ms / 1000;
            if (seconds < 60) {
                return seconds + "s";
            }
            long minutes = seconds / 60;
            long remSec = seconds % 60;
            if (minutes < 60) {
                return minutes + "m " + remSec + "s";
            }
            long hours = minutes / 60;
            long remMin = minutes % 60;
            return hours + "h " + remMin + "m " + remSec + "s";
        }));

        environment.define("fromSeconds", new NativeFunction(1, "s", args
                -> Double.parseDouble(String.valueOf(args.get(0))) * 1000));

        environment.define("fromMinutes", new NativeFunction(1, "m", args
                -> Double.parseDouble(String.valueOf(args.get(0))) * 60_000));

        environment.define("fromHours", new NativeFunction(1, "h", args
                -> Double.parseDouble(String.valueOf(args.get(0))) * 3_600_000));
    }
}
