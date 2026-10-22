package padej.displayLib.pack;

import me.padej.jumper.runtime.Access;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;

/**
 * Набор содержимого DisplayLib в одной папке: {@code screens/} (экраны {@code .jmc}), {@code scripts/} (скрипты
 * {@code .jmp}), {@code particles/} (эффекты частиц) и своя политика доступа скриптов {@code scripts.jma}.
 *
 * <p>Основной набор - папка самого DisplayLib. Другой плагин регистрирует свою папку
 * ({@link ContentPacks#register(Plugin)}) - и его экраны, скрипты и частицы живут у него, а скрипты подчиняются
 * <b>его</b> {@code scripts.jma}, а не политике DisplayLib. Скрипт экрана ищется только в {@code scripts/}
 * того же набора.</p>
 *
 * <p>id экранов и эффектов частиц общие для всех наборов: при совпадении остаётся тот, чей набор
 * зарегистрирован раньше (DisplayLib - первым), в консоли предупреждение.</p>
 */
public final class ContentPack {
    public static final String POLICY_FILE = "scripts.jma";

    private final String name;
    private final Plugin owner;
    private final Path root;
    private volatile Access access;

    ContentPack(String name, Plugin owner, Path root) {
        this.name = name;
        this.owner = owner;
        this.root = root.toAbsolutePath().normalize();
    }

    /** Имя набора (имя плагина-владельца). */
    public String name() {
        return name;
    }

    /** Плагин, которому принадлежит набор. */
    public Plugin owner() {
        return owner;
    }

    /** Корень набора. */
    public Path root() {
        return root;
    }

    public Path screens() {
        return root.resolve("screens");
    }

    public Path scripts() {
        return root.resolve("scripts");
    }

    public Path particles() {
        return root.resolve("particles");
    }

    public Path policyFile() {
        return root.resolve(POLICY_FILE);
    }

    /** Политика доступа скриптов этого набора ({@code scripts.jma} в его корне). */
    public Access access() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    /** Путь для сообщений: {@code WordBomb/screens/x.jmc}. */
    public String describe(Path file) {
        Path f = file.toAbsolutePath().normalize();
        return name + "/" + (f.startsWith(root) ? root.relativize(f).toString().replace('\\', '/') : f.getFileName().toString());
    }

    @Override
    public String toString() {
        return "ContentPack[" + name + " @ " + root + "]";
    }
}
