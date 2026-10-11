package padej.displayLib.script.api;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Лог сервера: глобал {@code log}.
 *
 * <pre>
 * log.info("экран открыт для " + player.name());
 * log.warn("баланс отрицательный: " + balance);
 * log.error("не удалось выдать предмет");
 * </pre>
 */
public final class LogAPI {
    private final Logger logger;
    private final String prefix;

    public LogAPI(Logger logger, String scriptName) {
        this.logger = logger;
        this.prefix = "[" + scriptName + "] ";
    }

    public void info(Object message) {
        logger.info(prefix + message);
    }

    public void warn(Object message) {
        logger.warning(prefix + message);
    }

    public void error(Object message) {
        logger.log(Level.SEVERE, prefix + message);
    }

    /** То же, что {@link #info(Object)}. */
    public void debug(Object message) {
        logger.fine(prefix + message);
    }
}
