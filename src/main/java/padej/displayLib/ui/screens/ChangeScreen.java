package padej.displayLib.ui.screens;

import padej.displayLib.ui.*;
import padej.displayLib.ui.annotations.AlwaysOnScreen;
import padej.displayLib.ui.widgets.Widget;
import padej.displayLib.utils.Animation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ChangeScreen {
    private static final Logger LOGGER = LogManager.getLogger(ChangeScreen.class);
    private final WidgetManager manager;
    private final List<Widget> persistentWidgets = new ArrayList<>();

    public ChangeScreen(WidgetManager manager) {
        this.manager = manager;
        savePersistentWidgets();
    }

    private void savePersistentWidgets() {
        Class<?> managerClass = manager.getClass();
        while (managerClass != null && WidgetManager.class.isAssignableFrom(managerClass)) {
            for (Field field : managerClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(AlwaysOnScreen.class)) {
                    AlwaysOnScreen annotation = field.getAnnotation(AlwaysOnScreen.class);
                    if (annotation.value().isAssignableFrom(manager.getClass())) {
                        field.setAccessible(true);
                        try {
                            Object widget = field.get(manager);
                            if (widget instanceof Widget) {
                                persistentWidgets.add((Widget) widget);
                            }
                        } catch (IllegalAccessException e) {
                            LOGGER.error("Ошибка при сохранении постоянных виджетов в классе {}",
                                    manager.getClass().getSimpleName(), e);
                        }
                    }
                }
            }
            managerClass = managerClass.getSuperclass();
        }
    }

    public static void switchTo(Player player, Class<? extends WidgetManager> from, Class<? extends WidgetManager> to) {
        WidgetManager currentManager = UIManager.getInstance().getActiveScreen(player);
        if (currentManager != null && currentManager.getClass() == from) {
            try {
                Location oldLocation = currentManager.getLocation();
                Color backgroundColor = null;
                float yaw = 0, pitch = 0;

                // Сохраняем параметры отображения, если доступны
                if (currentManager instanceof IDisplayable) {
                    IDisplayable displayable = (IDisplayable) currentManager;
                    if (displayable.getTextDisplay() != null) {
                        backgroundColor = displayable.getTextDisplay().getBackgroundColor();
                        yaw = displayable.getTextDisplay().getLocation().getYaw();
                        pitch = displayable.getTextDisplay().getLocation().getPitch();
                    }
                    displayable.softRemoveWithAnimation();
                } else {
                    currentManager.remove();
                }
                
                UIManager.getInstance().unregisterScreen(player);

                // Создаем новый менеджер
                WidgetManager newManager;
                try {
                    if (IDisplayable.class.isAssignableFrom(to)) {
                        // Пробуем создать с четырьмя параметрами
                        newManager = to.getConstructor(
                                Player.class,
                                Location.class,
                                String.class,
                                float.class
                        ).newInstance(
                                player,
                                oldLocation,
                                " ",
                                10.0f
                        );
                    } else {
                        // Пробуем создать с двумя параметрами
                        newManager = to.getConstructor(Player.class, Location.class)
                                .newInstance(player, oldLocation);
                    }
                } catch (NoSuchMethodException e) {
                    // Если не нашли нужный конструктор, пробуем другой вариант
                    if (IDisplayable.class.isAssignableFrom(to)) {
                        // Пробуем создать с двумя параметрами
                        newManager = to.getConstructor(Player.class, Location.class)
                                .newInstance(player, oldLocation);
                    } else {
                        // Пробуем создать с четырьмя параметрами
                        newManager = to.getConstructor(
                                Player.class,
                                Location.class,
                                String.class,
                                float.class
                        ).newInstance(
                                player,
                                oldLocation,
                                " ",
                                10.0f
                        );
                    }
                }

                // Восстанавливаем параметры отображения
                if (backgroundColor != null && newManager instanceof IDisplayable) {
                    IDisplayable displayable = (IDisplayable) newManager;
                    displayable.setBackgroundColor(backgroundColor);
                    displayable.updateDisplayPosition(oldLocation, yaw, pitch);
                }
                
                if (newManager instanceof Animatable) {
                    ((Animatable) newManager).createWithAnimation(player);
                } else {
                    UIManager.getInstance().registerScreen(player, newManager);
                }
            } catch (Exception e) {
                LOGGER.error("Ошибка при переключении с {} на {}", from.getSimpleName(), to.getSimpleName(), e);
            }
        }
    }

    public static void switchToParent(Player player, Class<? extends WidgetManager> managerClass) {
        try {
            WidgetManager tempManager = managerClass.getDeclaredConstructor().newInstance();
            
            if (tempManager instanceof IParentable) {
                Class<? extends WidgetManager> parentClass = ((IParentable) tempManager).getParentManager();
                if (parentClass != null) {
                    switchTo(player, managerClass, parentClass);
                } else {
                    LOGGER.warn("Попытка переключения на родительский менеджер для {} не удалась - родительский менеджер не найден",
                            managerClass.getSimpleName());
                }
            } else {
                LOGGER.warn("Попытка переключения на родительский менеджер для {} не удалась - тип не поддерживает иерархию",
                        managerClass.getSimpleName());
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка при получении родительского менеджера для {}", managerClass.getSimpleName(), e);
        }
    }
}