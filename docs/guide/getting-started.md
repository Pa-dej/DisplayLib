# Быстрый старт

## Возможности

- 📋 **Экраны как конфиги** (`screens/*.jmc`) - данные с выражениями, переменными и `if`, без кода
- 🔧 **Скрипты на Jumper** (`scripts/*.jmp`) - синтаксис Java, лёгкость Lua, модули через `import`
- 🔒 **Песочница** (`scripts.jma`) - белый список пакетов и классов; сторожевой таймер против зависших скриптов
- 🎮 **Виджеты** - текстовые и предметные кнопки, форматированный текст, подсказки, hover-анимации
- 👥 **Приватные и публичные экраны** - для одного игрока или для всех поблизости
- 🔄 **Hot Reload** - изменённый экран перечитывается сам, скрипт - при следующем открытии
- 💾 **Хранилище и таймеры** - данные на игрока, отложенные и повторяющиеся действия
- 🧰 **Поддержка редакторов** - `META-INF/jumper/host.jmc` описывает глобалы и хуки для плагинов Jumper
  (IntelliJ IDEA, VS Code, Neovim)

## 1. Установка

1. Скачайте jar из [релизов](https://github.com/Pa-dej/DisplayLib/releases) или соберите сами: `cd plugin && ./gradlew build` (нужен JDK 21), jar в `plugin/build/libs/`
2. Положите jar в папку `plugins/` сервера Paper 1.21.11
3. Перезапустите сервер

## 2. Первый запуск

Плагин создаст `plugins/DisplayLib/` с `config.jmc` и `scripts.jma`. Примеры выгружаются командой:

```
/displaylib examples
```

```
plugins/DisplayLib/
  config.jmc          # настройки плагина
  scripts.jma         # политика доступа скриптов
  screens/            # экраны (.jmc)
    demo_menu.jmc     # меню демонстраций: виджеты, наведение, кадры, скрипт, игрок, таймеры
    demo_widgets.jmc  demo_hover.jmc  demo_frames.jmc  demo_script.jmc  demo_player.jmc  demo_timers.jmc
    demo_public.jmc   # публичный экран (openpublic)
    morph_menu.jmc  morph_shop.jmc  morph_settings.jmc   # morph-переходы
    demo_pages.jmc    # страницы списка: morph на тот же экран, данные из onOpen
  scripts/            # скрипты (.jmp) - по одному на экран, плюс lib/util.jmp (import)
  particles/          # эффекты частиц: sparkle, confetti, poison, coin_fountain, embers, flame, water_splash, fireflies
  data/               # пишет плагин: storage скриптов, сохранённые публичные экраны
```

## 3. Тестирование

```
/displaylib open demo_menu    # Меню демонстраций (все демо связаны кнопками «Назад»)
/displaylib openpublic demo_public <x> <y> <z>   # Публичный экран в мире
/displaylib list              # Список экранов
/displaylib close             # Закрыть экран
/displaylib reload            # Перечитать экраны, политику и скрипты (админ)
```
