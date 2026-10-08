package padej.displayLib.script.api;

import padej.displayLib.ui.widgets.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран, с точки зрения скрипта: глобал {@code screen}.
 *
 * <pre>
 * screen.id();                      // "main_menu"
 * screen.widget("title").text("Привет");
 * screen.data("page", 2);           // живёт, пока экран открыт
 * int page = screen.data("page");
 * screen.switchTo("settings");      // только приватный экран
 * screen.close();                   // только приватный экран
 * </pre>
 */
public final class ScreenAPI {
    /** Что экран предоставляет скрипту; реализуют приватный и публичный экземпляры. */
    public interface Host {
        String getScreenId();

        Widget getWidget(String id);

        /** Все виджеты с id, в порядке объявления. */
        Map<String, Widget> getWidgets();

        boolean isPublic();

        /** Закрыть экран; у публичного ничего не делает. */
        void closeFromScript();

        /** Переключиться на другой экран; у публичного ничего не делает. */
        void switchFromScript(String screenId);
    }

    private final Host host;
    private final Map<String, Object> data = new HashMap<>();
    private final Map<Widget, WidgetAPI> widgetApis = new IdentityHashMap<>();

    public ScreenAPI(Host host) {
        this.host = host;
    }

    /** ID экрана. */
    public String id() {
        return host.getScreenId();
    }

    /** Публичный ли это экран. */
    public boolean isPublic() {
        return host.isPublic();
    }

    /** Закрыть экран (приватный). */
    public void close() {
        host.closeFromScript();
    }

    /** Переключиться на другой экран в той же позиции (приватный). */
    public void switchTo(String screenId) {
        if (screenId != null) host.switchFromScript(screenId);
    }

    /** Виджет по ID или {@code null}. */
    public WidgetAPI widget(String id) {
        Widget widget = host.getWidget(id);
        return widget != null ? widgetApi(id, widget) : null;
    }

    /** Все виджеты экрана, у которых есть ID. */
    public List<WidgetAPI> widgets() {
        List<WidgetAPI> list = new ArrayList<>();
        for (Map.Entry<String, Widget> e : host.getWidgets().entrySet()) {
            list.add(widgetApi(e.getKey(), e.getValue()));
        }
        return list;
    }

    /** Данные экрана: живут, пока экран открыт. */
    public Object data(String key) {
        return data.get(key);
    }

    /** Записать данные экрана; {@code null} удаляет ключ. */
    public void data(String key, Object value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
    }

    /** Внутреннее: обёртка виджета, одна на виджет на всё время жизни экрана. */
    public WidgetAPI widgetApi(String id, Widget widget) {
        return widgetApis.computeIfAbsent(widget, w -> new WidgetAPI(id, w));
    }

    /** Внутреннее: очистка при закрытии. */
    public void cleanup() {
        data.clear();
        widgetApis.clear();
    }

    @Override
    public String toString() {
        return "screen " + host.getScreenId();
    }
}
