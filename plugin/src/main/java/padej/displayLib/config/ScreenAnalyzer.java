package padej.displayLib.config;

import me.padej.jumper.ast.FunctionNode;
import me.padej.jumper.ast.Stmt;
import me.padej.jumper.ast.Stmts;
import me.padej.jumper.runtime.Access;
import me.padej.jumper.interp.Interpreter;
import me.padej.jumper.parser.Parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Статический разбор экранов при загрузке (первая загрузка, {@code /displaylib reload}, hot reload):
 * для каждого экрана составляется <b>список не тикаемых виджетов</b> - тех, за взглядом игрока на
 * которые следить незачем. Экземпляр экрана ({@code ScreenInstance}/{@code GlobalScreenInstance})
 * создаёт такие виджеты неинтерактивными: они не попадают в список обсчитываемых на тике и не
 * перехватывают клики.
 *
 * <p>Виджет не тикается, если его {@code tolerance} по любой оси {@code <= 0} (в зону нулевого размера
 * не попасть взглядом), либо если выполнены <b>все</b> факторы:</p>
 * <ol>
 * <li><b>клик ничего не делает</b>: {@code onClick} не задан / {@code NONE}; {@code RUN_SCRIPT}, у которого
 *     нет скрипта, функция в скрипте не объявлена или объявлена с пустым телом ({@code void b() {}});
 *     {@code SWITCH_SCREEN} на несуществующий (или публичный) экран; на публичном экране также
 *     {@code SWITCH_SCREEN}/{@code CLOSE_SCREEN} - там они не работают;</li>
 * <li><b>нет реакции на наведение</b>: подсказки, hover-анимации (приватные экраны), другого текста /
 *     спрайта, свечения предмета;</li>
 * <li><b>фон при наведении не отличается</b>: RGBA совпадают, либо альфа обоих фонов 0 - фона не видно,
 *     и RGB не важен.</li>
 * </ol>
 *
 * <p>Скрипт для фактора 1 только разбирается (без выполнения верхнего уровня) - объявления функций,
 * в том числе из {@code import}, видны в AST. Если скрипт не разбирается, его функции считаются
 * рабочими (решит проверка при открытии экрана).</p>
 */
public final class ScreenAnalyzer {

    private ScreenAnalyzer() {
    }

    /**
     * Результат разбора одного скрипта: имя функции → тело пустое.
     * {@code hasImports}: функции могут приходить из {@code import} - их в AST файла нет,
     * поэтому необъявленная функция не считается отсутствующей.
     */
    private record ScriptInfo(Map<String, Boolean> emptyByName, boolean parsed, boolean hasImports) {
        boolean declares(String fn) { return emptyByName.containsKey(fn); }
        boolean isEmpty(String fn) { return Boolean.TRUE.equals(emptyByName.get(fn)); }
    }

    /**
     * Проставить {@link WidgetDefinition#isInert()} и {@link ScreenDefinition#getInertWidgetIds()} всем экранам.
     *
     * @param screens    все загруженные экраны (нужны для проверки целей {@code switchTo})
     * @param scriptsDir папка {@code plugins/DisplayLib/scripts}
     * @param access     политика доступа скриптов ({@code scripts.jma}) - для разбора; {@code null} = без ограничений
     */
    public static void analyze(Map<String, ScreenDefinition> screens, Path scriptsDir, Access access, Logger log) {
        Map<String, ScriptInfo> scripts = new HashMap<>();
        int inertTotal = 0, total = 0;
        for (ScreenDefinition screen : screens.values()) {
            Set<String> inert = new LinkedHashSet<>();
            if (screen.getWidgets() == null) { screen.setInertWidgetIds(inert); continue; }
            boolean publicScreen = screen.getScreenType() == ScreenDefinition.ScreenType.PUBLIC;
            ScriptInfo script = screen.getScript() == null ? null
                    : scripts.computeIfAbsent(screen.getScript(), f -> parseScript(scriptsDir, f, access, log));
            int index = 0;
            for (WidgetDefinition def : screen.getWidgets()) {
                index++;
                String reason = inertReason(def, screen, screens, script, publicScreen);
                def.setInert(reason != null);
                total++;
                if (reason != null) {
                    inertTotal++;
                    inert.add(def.getId() != null ? def.getId() : "#" + index);
                    log.fine("Screen " + screen.getId() + ": widget '" + (def.getId() != null ? def.getId() : "#" + index) + "' is static (" + reason + ")");
                }
            }
            screen.setInertWidgetIds(inert);
        }
        log.info("Screens analyzed: " + inertTotal + " of " + total + " widget(s) are static and will not be hover-tested");
    }

    /**
     * Почему виджет можно не тикать; {@code null}, если он интерактивен.
     */
    static String inertReason(WidgetDefinition def, ScreenDefinition screen, Map<String, ScreenDefinition> screens,
                              ScriptInfo script, boolean publicScreen) {
        // зона наведения нулевого размера: в неё нельзя попасть взглядом, что бы виджет ни умел
        float[] tol = def.getTolerance();
        if (tol == null || tol.length < 2 || tol[0] <= 0 || tol[1] <= 0) return "tolerance <= 0 (no hit area)";
        String click = clickReason(def, screens, script, publicScreen);
        if (click == null) return null;                       // клик работает
        if (hasHoverVisual(def, !publicScreen)) return null;  // есть реакция на наведение
        return click;
    }

    /** Почему клик ничего не делает; {@code null}, если делает. */
    private static String clickReason(WidgetDefinition def, Map<String, ScreenDefinition> screens, ScriptInfo script, boolean publicScreen) {
        WidgetDefinition.ClickAction action = def.getOnClick();
        if (action == null) return "no onClick";
        switch (action.getAction()) {
            case NONE -> { return "onClick: none"; }
            case CLOSE_SCREEN -> { return publicScreen ? "close on a public screen" : null; }
            case SWITCH_SCREEN -> {
                if (publicScreen) return "switchTo on a public screen";
                String target = action.getTarget();
                if (target == null || target.isBlank()) return "switchTo without target";
                ScreenDefinition t = screens.get(target);
                if (t == null) return "switchTo unknown screen '" + target + "'";
                if (t.getScreenType() != ScreenDefinition.ScreenType.PRIVATE) return "switchTo public screen '" + target + "'";
                return null;
            }
            case RUN_SCRIPT -> {
                String fn = action.getFunction();
                if (fn == null || fn.isBlank()) return "function not specified";
                if (script == null) return "no script for function '" + fn + "'";
                if (!script.parsed()) return null;                      // не разобрался - решит рантайм
                if (!script.declares(fn)) return script.hasImports() ? null : "function '" + fn + "' not declared in script";
                if (script.isEmpty(fn)) return "function '" + fn + "' has empty body";
                return null;
            }
            default -> { return null; }
        }
    }

    /**
     * Есть ли у виджета видимая реакция на наведение: подсказка, hover-анимация, другой текст/спрайт,
     * свечение предмета, другой фон. Фон: RGBA совпадают - реакции нет; альфа обоих 0 - фона не видно,
     * RGB не важен.
     */
    public static boolean hasHoverVisual(WidgetDefinition def, boolean withHoverAnimation) {
        if (def.getTooltip() != null) return true;
        if (withHoverAnimation && def.getHoverAnimation() != null) return true;
        if (withHoverAnimation && def.getHoveredFrames() != null) return true;
        if (def.getType() == WidgetDefinition.WidgetType.ITEM_BUTTON) {
            return def.isGlowOnHover();
        }
        if (def.getType() == WidgetDefinition.WidgetType.SPRITE_BUTTON && def.getHoveredSprite() != null
                && !def.getHoveredSprite().equals(def.getSprite())) return true;
        if (def.getHoveredText() != null && !def.getHoveredText().isEmpty()
                && !def.getHoveredText().equals(def.getText())) return true;
        if (def.getFormattedHoveredText() != null && !def.getFormattedHoveredText().equals(def.getFormattedText())) return true;
        int a = def.getBackgroundAlpha(), ha = def.getHoveredBackgroundAlpha();
        if (a == 0 && ha == 0) return false;                 // фона не видно ни так, ни так
        if (a != ha) return true;
        return !Arrays.equals(def.getBackgroundColor(), def.getHoveredBackgroundColor());
    }

    // -------------------------------------------------------------------------
    // Разбор скрипта без выполнения
    // -------------------------------------------------------------------------

    private static final java.util.regex.Pattern IMPORT = java.util.regex.Pattern.compile("(?m)^\\s*import\\b");

    private static ScriptInfo parseScript(Path scriptsDir, String file, Access access, Logger log) {
        Map<String, Boolean> fns = new HashMap<>();
        Path path = scriptsDir.resolve(file).normalize();
        if (!path.startsWith(scriptsDir) || !Files.isRegularFile(path)) {
            // нет файла: функций нет; экземпляр экрана отдельно предупредит, что скрипт не найден
            return new ScriptInfo(fns, true, false);
        }
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ScreenAnalyzer.class.getClassLoader());
        try {
            String source = Files.readString(path);
            Interpreter interpreter = new Interpreter();
            if (access != null) interpreter.access(access);
            // те же глобалы, что у настоящего скрипта (ScriptContext): парсер должен знать эти имена
            for (String g : new String[]{"player", "screen", "storage", "timer", "log"}) interpreter.define(g, null);
            boolean hasImports = IMPORT.matcher(source).find();
            FunctionNode main = interpreter.parse(() -> new Parser(source, interpreter.globals())
                    .source(path.toAbsolutePath().getParent(), interpreter.modules())
                    .parseProgram());
            collect(main.body, fns);
            return new ScriptInfo(fns, true, hasImports);
        } catch (Exception e) {
            log.log(Level.FINE, "Script " + file + " could not be parsed for analysis: " + e);
            return new ScriptInfo(fns, false, true);
        } finally {
            thread.setContextClassLoader(previousLoader);
        }
    }

    /** Собрать объявления функций (и из импортов - они разворачиваются парсером в те же блоки). */
    private static void collect(Stmt stmt, Map<String, Boolean> out) {
        if (stmt == null) return;
        if (stmt instanceof Stmts.FuncDecl fd) {
            if (fd.fn != null && fd.fn.name != null) out.put(fd.fn.name, isEmptyBody(fd.fn.body));
        } else if (stmt instanceof Stmts.Block block) {
            for (Stmt s : block.stmts) collect(s, out);
        } else if (stmt instanceof Stmts.If i) {
            collect(i.then, out); collect(i.otherwise, out);
        } else if (stmt instanceof Stmts.Try t) {
            collect(t.body, out); collect(t.handler, out); collect(t.fin, out);
        }
    }

    /** Тело из одних пустых блоков / точек с запятой. */
    public static boolean isEmptyBody(Stmt stmt) {
        if (stmt == null || stmt instanceof Stmts.Empty) return true;
        if (stmt instanceof Stmts.Block block) {
            for (Stmt child : block.stmts) {
                if (!isEmptyBody(child)) return false;
            }
            return true;
        }
        return false;
    }
}
