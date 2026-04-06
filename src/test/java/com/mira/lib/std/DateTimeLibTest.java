package com.mira.lib.std;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class DateTimeLibTest {

    static DateTime dateTime = new DateTime();
    static Environment environment = new Environment();
    Interpreter interpreter = new Interpreter();

    @BeforeAll
    static void setup() {
        dateTime.loadLib(environment);
    }

    private Object call(String name, Object... args) {
        NativeFunction fn = (NativeFunction) environment.get(name);
        return fn.call(interpreter, List.of(args));
    }

    @Test
    void testNowReturnsString() {
        Object result = call("now");
        assertInstanceOf(String.class, result);
    }

    @Test
    void testNowIsParseable() {
        String result = (String) call("now");
        assertDoesNotThrow(() -> LocalDateTime.parse(result));
    }

    @Test
    void testNowIsCloseToCurrentTime() {
        String result = (String) call("now");
        LocalDateTime parsed = LocalDateTime.parse(result);
        assertTrue(ChronoUnit.SECONDS.between(parsed, LocalDateTime.now()) < 2);
    }

    @Test
    void testTimestampReturnsDouble() {
        assertInstanceOf(Double.class, call("timestamp"));
    }

    @Test
    void testTimestampIsReasonable() {
        double ts = (double) call("timestamp");
        assertTrue(ts > 1_700_000_000_000.0);
    }

    @Test
    void testTimestampIncreases() throws InterruptedException {
        double first = (double) call("timestamp");
        Thread.sleep(10);
        double second = (double) call("timestamp");
        assertTrue(second > first);
    }

    @Test
    void testTimestampSecIsLessThanTimestamp() {
        double ms = (double) call("timestamp");
        double sec = (double) call("timestampSec");
        assertTrue(sec < ms);
    }

    @Test
    void testTimestampSecIsReasonable() {
        double sec = (double) call("timestampSec");
        assertTrue(sec > 1_700_000_000.0);
    }

    @Test
    void testFormatDateWithPattern() {
        String now = (String) call("now");
        String result = (String) call("formatDate", now, "yyyy-MM-dd");
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void testFormatDateWithTimePattern() {
        String now = (String) call("now");
        String result = (String) call("formatDate", now, "HH:mm");
        assertTrue(result.matches("\\d{2}:\\d{2}"));
    }

    @Test
    void testFormatDateInvalidPatternThrows() {
        assertThrows(Exception.class, () -> call("formatDate", "not-a-date", "yyyy-MM-dd"));
    }

    @Test
    void testYearReturnsDouble() {
        assertInstanceOf(Double.class, call("year"));
    }

    @Test
    void testYearIsCurrentYear() {
        double year = (double) call("year");
        assertEquals((double) LocalDateTime.now().getYear(), year);
    }

    @Test
    void testMonthIsInRange() {
        double month = (double) call("month");
        assertTrue(month >= 1 && month <= 12);
    }

    @Test
    void testMonthIsCurrentMonth() {
        double month = (double) call("month");
        assertEquals((double) LocalDateTime.now().getMonthValue(), month);
    }

    @Test
    void testDayIsInRange() {
        double day = (double) call("day");
        assertTrue(day >= 1 && day <= 31);
    }

    @Test
    void testDayIsCurrentDay() {
        double day = (double) call("day");
        assertEquals((double) LocalDateTime.now().getDayOfMonth(), day);
    }

    @Test
    void testHourIsInRange() {
        double hour = (double) call("hour");
        assertTrue(hour >= 0 && hour <= 23);
    }

    @Test
    void testMinuteIsInRange() {
        double minute = (double) call("minute");
        assertTrue(minute >= 0 && minute <= 59);
    }

    @Test
    void testSecondIsInRange() {
        double second = (double) call("second");
        assertTrue(second >= 0 && second <= 59);
    }

    @Test
    void testDayOfWeekReturnsString() {
        assertInstanceOf(String.class, call("dayOfWeek"));
    }

    @Test
    void testDayOfWeekIsValidDay() {
        String day = (String) call("dayOfWeek");
        List<String> valid = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        assertTrue(valid.contains(day));
    }

    @Test
    void testDayOfWeekMatchesCurrentDay() {
        String result = (String) call("dayOfWeek");
        assertEquals(LocalDateTime.now().getDayOfWeek().toString(), result);
    }

    @Test
    void testDayOfYearIsInRange() {
        double doy = (double) call("dayOfYear");
        assertTrue(doy >= 1 && doy <= 366);
    }

    @Test
    void testDayOfYearMatchesCurrentDay() {
        double doy = (double) call("dayOfYear");
        assertEquals((double) LocalDateTime.now().getDayOfYear(), doy);
    }

    @Test
    void testTimeSinceReturnsPositive() {
        String past = LocalDateTime.now().minusSeconds(5).toString();
        double result = (double) call("timeSince", past);
        assertTrue(result >= 4);
    }

    @Test
    void testTimeSinceIsZeroForNow() {
        String now = LocalDateTime.now().toString();
        double result = (double) call("timeSince", now);
        assertTrue(result >= 0 && result < 2);
    }

    @Test
    void testTimeSinceInvalidDateThrows() {
        assertThrows(Exception.class, () -> call("timeSince", "not-a-date"));
    }

    @Test
    void testEpochToDateReturnsString() {
        assertInstanceOf(String.class, call("epochToDate", 0.0));
    }

    @Test
    void testEpochToDateEpochZero() {
        String result = (String) call("epochToDate", 0.0);
        assertEquals("1970-01-01T00:00", result);
    }

    @Test
    void testEpochToDateKnownValue() {
        String result = (String) call("epochToDate", 1_000_000_000.0);
        assertTrue(result.startsWith("2001-09-09"));
    }

    @Test
    void testSleepReturnsNull() {
        assertNull(call("sleep", 1.0));
    }

    @Test
    void testSleepActuallyWaits() {
        long before = System.currentTimeMillis();
        call("sleep", 100.0);
        long after = System.currentTimeMillis();
        assertTrue(after - before >= 90);
    }
}
