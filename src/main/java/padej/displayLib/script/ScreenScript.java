package padej.displayLib.script;

import me.padej.jumper.ast.FunctionNode;
import me.padej.jumper.ast.VarType;
import me.padej.jumper.interp.Frame;
import me.padej.jumper.interp.Interpreter;
import me.padej.jumper.parser.ParseError;
import me.padej.jumper.parser.Parser;
import me.padej.jumper.runtime.JFunction;
import me.padej.jumper.runtime.JmpCancelled;
import me.padej.jumper.runtime.JmpError;
import me.padej.jumper.runtime.ScriptSecurityException;

import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Скрипт одного экземпляра экрана: разобранный файл с доступом к его функциям
 * верхнего уровня.
 *
 * <p>То же, что {@link Interpreter#script(String)}, но с каталогом файла, чтобы
 * {@code import "lib/util.jmp"} разрешался относительно самого скрипта, а не рабочей
 * папки сервера. Верхний уровень выполняется один раз при создании; затем функции
 * ({@code onOpen}, обработчики кликов) берутся по имени и вызываются с перехватом
 * ошибок и под сторожевым таймером.</p>
 */
public final class ScreenScript {
    private final JumperEngine engine;
    private final Interpreter interpreter;
    private final String file;
    private final FunctionNode main;
    private final FunctionNode.ScriptFunction function;
    private final Logger log;

    private ScreenScript(JumperEngine engine, Interpreter interpreter, String file,
                         FunctionNode main, FunctionNode.ScriptFunction function) {
        this.engine = engine;
        this.interpreter = interpreter;
        this.file = file;
        this.main = main;
        this.function = function;
        this.log = engine.plugin().getLogger();
    }

    /**
     * Разобрать и выполнить верхний уровень скрипта.
     *
     * @return скрипт или {@code null}, если он не разбирается или падает при загрузке
     */
    static ScreenScript create(JumperEngine engine, Interpreter interpreter, String file, Path path, String source) {
        Logger log = engine.plugin().getLogger();
        Path dir = path.toAbsolutePath().getParent();
        FunctionNode main;
        // Jumper ищет классы (import, new, Foo.class) через context class loader потока;
        // у основного потока сервера это загрузчик сервера, который не видит классы плагина
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ScreenScript.class.getClassLoader());
        try {
            main = interpreter.parse(() -> new Parser(source, interpreter.globals())
                    .source(dir, interpreter.modules())
                    .parseProgram());
        } catch (ParseError e) {
            log.warning("Script " + file + " does not parse (" + e.line + ":" + e.col + "): " + e.getMessage());
            return null;
        } catch (ScriptSecurityException e) {
            log.warning("Script " + file + " is denied by " + JumperEngine.POLICY_FILE + ": " + e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Script " + file + " failed to load", e);
            return null;
        } finally {
            thread.setContextClassLoader(previousLoader);
        }

        main.forceFrame = true;
        ScreenScript script = new ScreenScript(engine, interpreter, file, main,
                new FunctionNode.ScriptFunction(main, null));
        // Верхний уровень: объявления функций, переменные, import
        if (!script.run("<top level>", () -> script.function.call(new Object[0]))) {
            return null;
        }
        return script;
    }

    /** Путь скрипта относительно {@code scripts/}. */
    public String file() {
        return file;
    }

    /** Имена верхнего уровня (функции и переменные). */
    public Set<String> names() {
        return main.topLevel.keySet();
    }

    /** Объявлена ли в скрипте функция с таким именем. */
    public boolean hasFunction(String name) {
        return get(name) instanceof JFunction;
    }

    /** Значение переменной или функции верхнего уровня; {@code null}, если такого имени нет. */
    public Object get(String name) {
        int[] info = main.topLevel.get(name);
        Frame frame = function.lastFrame;
        if (info == null || frame == null) return null;
        VarType type = VarType.values()[info[2]];
        return info[1] == 1 ? type.fromBits(frame.p[info[0]]) : frame.slots[info[0]];
    }

    /**
     * Вызвать функцию верхнего уровня.
     *
     * @param required писать ли предупреждение, если функции нет
     * @return true, если функция найдена и выполнена без ошибок
     */
    public boolean invoke(String name, boolean required, Object... args) {
        Object value = get(name);
        if (!(value instanceof JFunction fn)) {
            if (value != null) {
                log.warning("'" + name + "' in " + file + " is not a function");
            } else if (required) {
                log.warning("Function '" + name + "' not found in " + file);
            }
            return false;
        }
        return run(name, () -> fn.call(args));
    }

    /**
     * Выполнить скриптовый колбэк (таймер) с тем же перехватом ошибок и сторожевым
     * таймером, что и у прямых вызовов.
     */
    public void guarded(Runnable callback) {
        run("callback", callback);
    }

    private boolean run(String what, Runnable body) {
        Watchdog watchdog = engine.watchdog();
        Interpreter previous = watchdog.enter(interpreter);
        boolean timedOut = false;
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ScreenScript.class.getClassLoader());
        try {
            body.run();
            return true;
        } catch (JmpCancelled e) {
            timedOut = true;
            return false;
        } catch (ScriptSecurityException e) {
            log.warning("Script " + file + ", " + what + ": " + e.getMessage());
            return false;
        } catch (JmpError e) {
            log.warning("Script error in " + what + " (" + file + ":" + e.line() + "): " + e.message());
            return false;
        } catch (ParseError e) {
            log.warning("Script " + file + " (" + e.line + ":" + e.col + "): " + e.getMessage());
            return false;
        } catch (StackOverflowError e) {
            log.warning("Stack overflow in " + what + " (" + file + ")");
            return false;
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Error in " + what + " (" + file + ")", e);
            return false;
        } finally {
            thread.setContextClassLoader(previousLoader);
            if (watchdog.exit(interpreter, previous) || timedOut) {
                log.warning("Script " + file + ", " + what + ": stopped after "
                        + watchdog.timeoutMillis() + " ms (scriptTimeoutMs in config.jmc)");
            }
        }
    }

    @Override
    public String toString() {
        return "script " + file;
    }
}
