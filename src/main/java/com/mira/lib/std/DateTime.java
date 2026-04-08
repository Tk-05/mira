package com.mira.lib.std;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class DateTime implements Lib {

    @Override
    public void loadLib(Environment environment) {

        environment.define("now", new NativeFunction(0, args -> {
            return LocalDateTime.now().toString();
        }));

        environment.define("timestamp", new NativeFunction(0, args -> {
            return (double) (System.currentTimeMillis() / 1000L);
        }));

        environment.define("timestampMs", new NativeFunction(0, args -> {
            return (double) System.currentTimeMillis();
        }));

        environment.define("dateFormat", new NativeFunction(2, args -> {
            String dateStr = String.valueOf(args.get(0));
            String pattern = String.valueOf(args.get(1));
            LocalDateTime dt = LocalDateTime.parse(dateStr);
            return dt.format(DateTimeFormatter.ofPattern(pattern));
        }));

        environment.define("year", new NativeFunction(0, args -> {
            return (double) LocalDateTime.now().getYear();
        }));

        environment.define("month", new NativeFunction(0, args -> {
            return (double) LocalDateTime.now().getMonthValue();
        }));

        environment.define("day", new NativeFunction(0, args -> {
            return (double) LocalDateTime.now().getDayOfMonth();
        }));

        environment.define("hour", new NativeFunction(0, args -> {
            return (double) LocalDateTime.now().getHour();
        }));

        environment.define("minute", new NativeFunction(0, args -> {
            return (double) LocalDateTime.now().getMinute();
        }));

        environment.define("second", new NativeFunction(0, args -> {
            return (double) LocalDateTime.now().getSecond();
        }));

        environment.define("dayOfWeek", new NativeFunction(0, args -> {
            return LocalDateTime.now().getDayOfWeek().toString();
        }));

        environment.define("dayOfYear", new NativeFunction(0, args -> {
            return (double) LocalDateTime.now().getDayOfYear();
        }));

        environment.define("secondsSince", new NativeFunction(1, args -> {
            String dateStr = String.valueOf(args.get(0));
            LocalDateTime then = LocalDateTime.parse(dateStr);
            return (double) ChronoUnit.SECONDS.between(then, LocalDateTime.now());
        }));

        environment.define("fromEpoch", new NativeFunction(1, args -> {
            long epoch = (long) Double.parseDouble(String.valueOf(args.get(0)));
            return LocalDateTime.ofEpochSecond(epoch, 0, ZoneOffset.UTC).toString();
        }));
    }
}
