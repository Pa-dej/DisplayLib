# Команды

| Команда | Назначение | Права |
|---|---|---|
| `/displaylib open <screen_id> [player] [x y z] [yaw pitch]` | Открыть приватный экран | - |
| `/displaylib close` | Закрыть свой экран | - |
| `/displaylib list` | Список загруженных экранов | - |
| `/displaylib openpublic <screen_id> <x> <y> <z> [yaw] [pitch]` | Поставить публичный экран в мире | `displaylib.admin` |
| `/displaylib closepublic <screen_id>` | Убрать публичный экран | `displaylib.admin` |
| `/displaylib listpublic` | Список публичных экранов | `displaylib.admin` |
| `/displaylib reload` | Перечитать экраны, политику и сбросить кэш скриптов | `displaylib.admin` |
| `/displaylib examples` | Выгрузить примеры в папку плагина | `displaylib.admin` |

Алиасы: `/dlib`, `/dl`.

## Java API для других плагинов

- `UIManager.getInstance()` - `openScreen`, `switchScreen`, `closeScreen`, `openPublicScreen`,
  `closePublicScreenById`, `getActiveScreen`, `getPublicScreens`.
- `DisplayClickEvent` - отменяемое событие клика по виджету приватного экрана.
- `ScreenInstance#getScriptContext()` / `GlobalScreenInstance#getScriptContext()` -
  `call("functionName", args...)` вызывает функцию скрипта экрана.

## Сборка

Jumper подключается через JitPack: `com.github.jumper-lang:jumper:<тег релиза>` (см. `build.gradle.kts`, сейчас `v0.11.2`).
Gradle 8.8 запускайте на JDK 21.
