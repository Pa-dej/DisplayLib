# Команды

| Команда | Назначение | Права |
|---|---|---|
| `/displaylib open <screen_id> [player] [x y z] [yaw pitch]` | Открыть приватный экран | - |
| `/displaylib close` | Закрыть свой экран | - |
| `/displaylib list` | Список загруженных экранов | - |
| `/displaylib openpublic <screen_id> <x> <y> <z> [yaw] [pitch]` | Поставить публичный экран в мире; сохраняется и встаёт на место после перезапуска | `displaylib.admin` |
| `/displaylib closepublic <screen_id>` | Убрать публичный экран (и из сохранённых) | `displaylib.admin` |
| `/displaylib listpublic` | Список публичных экранов | `displaylib.admin` |
| `/displaylib particle <effect> [x y z] [player] [count]` | Запустить эффект частиц (`particles/*.jmc`); `particle list`, `particle stop` | `displaylib.admin` |
| `/displaylib reload` | Перечитать экраны, политику и сбросить кэш скриптов | `displaylib.admin` |
| `/displaylib validate` | Перечитать и показать проблемы экранов: дубли id, клики в никуда, нет скрипта/функции, число статичных виджетов | `displaylib.admin` |
| `/displaylib examples` | Выгрузить примеры в папку плагина | `displaylib.admin` |

Алиасы: `/dlib`, `/dl`.

## Java API для других плагинов

- `UIManager.getInstance()` - `openScreen`, `switchScreen`, `closeScreen`, `openPublicScreen`,
  `closePublicScreenById`, `getActiveScreen`, `getPublicScreens`.
- `DisplayClickEvent` - отменяемое событие клика по виджету приватного или публичного экрана:
  `getPlayer()`, `getScreenId()`, `getWidgetId()`, `isPublicScreen()`; отмена отменяет действие клика.
- `CloseReason` - причина закрытия: `closeScreen(player, reason)`, её получает `onClose` скрипта.
- `openPublicScreen(id, location, yaw, pitch, persistent)` - `persistent = true` (так делает
  `/displaylib openpublic`) запоминает экран в `data/public_screens.jmc`: после перезапуска он встаёт
  на то же место (экраны из миров, загруженных позже, - когда мир загрузится). `closePublicScreenById`
  убирает его и из сохранённых. Без флага (`openPublicScreen(id, location, yaw, pitch)`) экран, как и раньше,
  живёт до выключения - для плагинов, которые сами расставляют экраны при включении.
- `Particles` - эффекты частиц: `play(id, location[, owner])` → `ParticleEmission` (`stop()`, `cancel()`, `setCenter()`),
  `effect(id)`, `ids()`, `stopAll()`. Формат эффектов - справочник «Частицы».
- `ScreenInstance#getScriptContext()` / `GlobalScreenInstance#getScriptContext()` -
  `call("functionName", args...)` вызывает функцию скрипта экрана.

## Сборка

Jumper подключается через JitPack: `com.github.jumper-lang:jumper:<тег релиза>` (см. `build.gradle.kts`, сейчас `v0.11.2`).
Gradle 9.2 (wrapper), JDK 21+. Частицы-пакеты используют внутренние классы сервера: компиляция идёт против
dev bundle Paper через [paperweight-userdev](https://docs.papermc.io/paper/dev/userdev/) (`paperweight.paperDevBundle`
в `build.gradle.kts`; первая сборка скачивает и готовит сервер - несколько минут). Jar - на mojang-именах, как и сам
Paper 1.21.11, без переобфускации.
