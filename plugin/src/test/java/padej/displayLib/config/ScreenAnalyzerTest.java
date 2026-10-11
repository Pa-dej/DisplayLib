package padej.displayLib.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Правила «статичный виджет» без сервера: только определения экранов. */
class ScreenAnalyzerTest {
    private static ScreenDefinition screen(String id, ScreenDefinition.ScreenType type) {
        ScreenDefinition s = new ScreenDefinition();
        s.setId(id); s.setScreenType(type);
        return s;
    }

    private static WidgetDefinition text(String id) {
        WidgetDefinition w = new WidgetDefinition();
        w.setId(id); w.setType(WidgetDefinition.WidgetType.TEXT_BUTTON);
        w.setTolerance(new float[]{0.1f, 0.1f});
        return w;
    }

    private static WidgetDefinition.ClickAction click(WidgetDefinition.ClickAction.ActionType t, String target) {
        WidgetDefinition.ClickAction a = new WidgetDefinition.ClickAction();
        a.setAction(t); a.setTarget(target); a.setFunction(target);
        return a;
    }

    @Test
    void plainLabelIsStatic() {
        ScreenDefinition s = screen("a", ScreenDefinition.ScreenType.PRIVATE);
        assertNotNull(ScreenAnalyzer.inertReason(text("t"), s, Map.of("a", s), null, false));
    }

    @Test
    void zeroToleranceIsAlwaysStatic() {
        ScreenDefinition s = screen("a", ScreenDefinition.ScreenType.PRIVATE);
        WidgetDefinition w = text("t");
        w.setTooltip("hint");
        w.setTolerance(new float[]{0f, 0.1f});
        assertNotNull(ScreenAnalyzer.inertReason(w, s, Map.of("a", s), null, false));
    }

    @Test
    void hoverReactionsMakeItInteractive() {
        ScreenDefinition s = screen("a", ScreenDefinition.ScreenType.PRIVATE);
        WidgetDefinition w = text("t"); w.setTooltip("hint");
        assertNull(ScreenAnalyzer.inertReason(w, s, Map.of("a", s), null, false));
        w = text("t"); w.setHoveredText("other");
        assertNull(ScreenAnalyzer.inertReason(w, s, Map.of("a", s), null, false));
        w = text("t"); w.setBackgroundAlpha(0); w.setHoveredBackgroundAlpha(120);
        assertNull(ScreenAnalyzer.inertReason(w, s, Map.of("a", s), null, false));
        w = text("t"); w.setHoverSound(new SoundSpec(null, 1, 1)); // выключенный звук - не реакция
        assertNotNull(ScreenAnalyzer.inertReason(w, s, Map.of("a", s), null, false));
    }

    @Test
    void switchToTargets() {
        ScreenDefinition a = screen("a", ScreenDefinition.ScreenType.PRIVATE);
        ScreenDefinition b = screen("b", ScreenDefinition.ScreenType.PRIVATE);
        ScreenDefinition pub = screen("p", ScreenDefinition.ScreenType.PUBLIC);
        Map<String, ScreenDefinition> all = new HashMap<>(Map.of("a", a, "b", b, "p", pub));
        WidgetDefinition w = text("go"); w.setOnClick(click(WidgetDefinition.ClickAction.ActionType.SWITCH_SCREEN, "b"));
        assertNull(ScreenAnalyzer.inertReason(w, a, all, null, false));
        w.setOnClick(click(WidgetDefinition.ClickAction.ActionType.SWITCH_SCREEN, "missing"));
        assertNotNull(ScreenAnalyzer.inertReason(w, a, all, null, false));
        w.setOnClick(click(WidgetDefinition.ClickAction.ActionType.SWITCH_SCREEN, "p"));
        assertNotNull(ScreenAnalyzer.inertReason(w, a, all, null, false), "switch to a public screen");
        w.setOnClick(click(WidgetDefinition.ClickAction.ActionType.SWITCH_SCREEN, "b"));
        assertNotNull(ScreenAnalyzer.inertReason(w, pub, all, null, true), "switch from a public screen");
    }

    @Test
    void runScriptWithoutScript() {
        ScreenDefinition a = screen("a", ScreenDefinition.ScreenType.PRIVATE);
        WidgetDefinition w = text("buy"); w.setOnClick(click(WidgetDefinition.ClickAction.ActionType.RUN_SCRIPT, "buy"));
        assertNotNull(ScreenAnalyzer.inertReason(w, a, Map.of("a", a), null, false), "no script loaded -> nothing to call");
    }
}
