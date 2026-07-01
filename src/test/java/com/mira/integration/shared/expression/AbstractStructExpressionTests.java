package com.mira.integration.shared.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.error.runtime.RuntimeError.NotAStructTemplateError;
import com.mira.error.runtime.RuntimeError.UnknownStructFieldError;

public abstract class AbstractStructExpressionTests {

    protected abstract String runForOutput(String source);

    @Test
    void templateFieldDefaultRead() {
        assertEquals("0,0", runForOutput("""
                var point : struct { var x : 0; var y : 0; };
                var p : $point{};
                print($p.x); print(","); print($p.y);
                """));
    }

    @Test
    void instantiationWithPartialOverride() {
        assertEquals("1,0", runForOutput("""
                var point : struct { var x : 0; var y : 0; };
                var p : $point{$x : 1};
                print($p.x); print(","); print($p.y);
                """));
    }

    @Test
    void instantiationWithAllFieldsOverridden() {
        assertEquals("1,2", runForOutput("""
                var point : struct { var x; var y; };
                var p : $point{$x : 1, $y : 2};
                print($p.x); print(","); print($p.y);
                """));
    }

    @Test
    void fieldWithoutInitializerDefaultsToNull() {
        assertEquals("null", runForOutput("""
                var point : struct { var x; };
                var p : $point{};
                print(toStr($p.x));
                """));
    }

    @Test
    void instancesAreIndependent() {
        assertEquals("1,2", runForOutput("""
                var point : struct { var x : 0; };
                var a : $point{$x : 1};
                var b : $point{$x : 2};
                print($a.x); print(","); print($b.x);
                """));
    }

    @Test
    void methodSurvivesInstantiationAndBindsToInstance() {
        assertEquals("2,100,101", runForOutput("""
                var counter : struct {
                    var count : 0;
                    fn increment() { $this.count : $this.count + 1; }
                    fn get() { return $this.count; }
                };
                var a : $counter{};
                var b : $counter{$count : 100};
                $a.increment();
                $a.increment();
                print($a.get()); print(","); print($b.get());
                print(",");
                $b.increment();
                print($b.get());
                """));
    }

    @Test
    void nestedStructField() {
        assertEquals("1,2,10", runForOutput("""
                var point : struct { var x : 0; var y : 0; };
                var rect : struct {
                    var topLeft : $point{$x : 1, $y : 2};
                    var width : 10;
                };
                var r : $rect{};
                print($r.topLeft.x); print(","); print($r.topLeft.y);
                print(","); print($r.width);
                """));
    }

    @Test
    void overridingUnknownFieldThrows() {
        assertThrows(UnknownStructFieldError.class, () -> runForOutput("""
                var point : struct { var x; var y; };
                var bad : $point{$z : 1};
                """));
    }

    @Test
    void instantiatingNonTemplateThrows() {
        assertThrows(NotAStructTemplateError.class, () -> runForOutput("""
                var notATemplate : { var a : 1; };
                var bad : $notATemplate{$a : 2};
                """));
    }

}
