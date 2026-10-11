package padej.displayLib.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MorphTextTest {
    @Test
    void endpoints() {
        assertEquals("text", MorphTransition.morphText("text", "example text", 0));
        assertEquals("example text", MorphTransition.morphText("text", "example text", 1));
        assertEquals("same", MorphTransition.morphText("same", "same", 0.4));
    }

    @Test
    void prefixAndSuffixSurvive() {
        for (double f = 0; f <= 1.0001; f += 0.1) {
            String s = MorphTransition.morphText("abcXYZdef", "abc12def", f);
            assertTrue(s.startsWith("abc") && s.endsWith("def"), s);
        }
    }

    @Test
    void lengthChangesSmoothly() {
        String prev = MorphTransition.morphText("Магазин", "Настройки", 0);
        for (int i = 1; i <= 10; i++) {
            String cur = MorphTransition.morphText("Магазин", "Настройки", i / 10.0);
            assertTrue(Math.abs(cur.length() - prev.length()) <= 3, prev + " -> " + cur);
            prev = cur;
        }
    }
}
