# DisplayLib - Minecraft UI Library с Lua API

DisplayLib — это библиотека для создания интерактивных UI экранов в Minecraft с поддержкой YAML конфигурации и Lua скриптов.

## Возможности

- 📋 **YAML конфигурация экранов** - простое описание интерфейсов
- 🔧 **Lua API** - мощная система скриптов для логики
- 🎮 **Интерактивные виджеты** - кнопки, текст, предметы
- 🔄 **Hot Reload** - автоматическая перезагрузка при изменении файлов
- 🎨 **Гибкая настройка** - цвета, размеры, позиции, анимации
- 💾 **Система хранения** - данные между сессиями
- ⏰ **Таймеры** - отложенные действия и анимации

## Быстрый старт

### 1. Установка

1. Скомпилируйте плагин: `./gradlew build`
2. Скопируйте JAR файл в папку `plugins/` сервера
3. Перезапустите сервер

### 2. Первый запуск

При первом запуске плагин автоматически создаст примеры экранов:

```
plugins/DisplayLib/
  screens/
    main_menu.yml     # Главное меню
    branch1.yml       # Простой экран без Lua
  scripts/
    main_menu.lua     # Скрипт главного меню
```

### 3. Тестирование

Используйте команды для тестирования:

```
/displaylib open main_menu    # Открыть главное меню
/displaylib list              # Список экранов
/displaylib close             # Закрыть экран
/displaylib reload            # Перезагрузить (для админов)
```

## Структура проекта

### YAML экраны (`screens/`)

Каждый экран описывается в отдельном YAML файле:

```yaml
id: main_menu
background:
  color: [0, 0, 0]
  alpha: 160
  scale: [10.0, 4.0, 1.0]
  text: " "

scripts:
  file: "main_menu.lua"

widgets:
  - id: btn_start
    type: ITEM_BUTTON
    material: COMPASS
    position: [-0.42, 0.30, 0.0]
    scale: [0.15, 0.15, 0.000001]
    tooltip: "Начать игру"
    onClick:
      action: RUN_SCRIPT
      function: "btn_start_click"
```

### Lua скрипты (`scripts/`)

Логика экранов реализуется в Lua:

```lua
function on_open()
    player.message("Добро пожаловать, " .. player.name() .. "!")
    
    local visits = storage.get("visits", 0) + 1
    storage.set("visits", visits)
    
    log.info("Экран открыт для " .. player.name())
end

function btn_start_click()
    player.sound("ui.button.click")
    screen.switch("game_menu")
end

function on_close()
    player.message("До свидания!")
end
```

## Lua API

### `player` - игрок

```lua
player.name()                    -- имя игрока
player.op()                      -- права оператора
player.gamemode()                -- текущий режим игры
player.gamemode("creative")      -- установить режим

player.message("текст")          -- сообщение в чат
player.message("текст", "#ff0000") -- с цветом

player.sound("ui.button.click")  -- звук
player.health()                  -- здоровье (0-20)
player.health(20)                -- установить здоровье

player.command("tp ~ ~10 ~")     -- выполнить команду
```

### `screen` - экран

```lua
screen.id()                      -- ID экрана
screen.close()                   -- закрыть экран
screen.switch("other_screen")    -- переключиться

screen.widget("widget_id")       -- получить виджет

-- Persistent data (живет пока экран открыт)
screen.data("key")               -- получить
screen.data("key", value)        -- установить
screen.data("key", nil)          -- удалить
```

### `widget` - виджет (в функциях клика)

```lua
-- Текст (только TEXT_BUTTON)
widget.text()                    -- получить текст
widget.text("новый текст")       -- установить текст
widget.hoveredText("при наведении")

-- Состояние
widget.visible()                 -- видимость
widget.visible(false)            -- скрыть
widget.enabled()                 -- активность
widget.enabled(false)            -- отключить

-- Tooltip
widget.tooltip()                 -- получить подсказку
widget.tooltip("новая подсказка") -- установить

-- Цвет фона (только TEXT_BUTTON)
widget.bgColor(40, 40, 40)       -- RGB
widget.bgAlpha(150)              -- прозрачность 0-255
```

### `storage` - хранение данных

```lua
storage.get("key")               -- получить
storage.get("key", 0)            -- с дефолтным значением
storage.set("key", value)        -- установить
storage.has("key")               -- проверить наличие
storage.remove("key")            -- удалить
storage.clear()                  -- очистить все
```

### `timer` - таймеры

```lua
-- Выполнить один раз через N тиков
timer.after(20, function()
    screen.close()
end)

-- Повторять каждые N тиков
local t = timer.repeat(10, function()
    -- анимация
end)
timer.cancel(t)

-- Повторить N раз
timer.times(5, 10, function(i)   -- каждые 5 тиков, 5 раз
    widget.text("Осталось: " .. (5 - i))
end)
```

### `log` - логирование

```lua
log.info("информация")
log.warn("предупреждение")
log.error("ошибка")
```

## Типы виджетов

### TEXT_BUTTON - текстовая кнопка

```yaml
- id: label_title
  type: TEXT_BUTTON
  text: "Заголовок"
  hoveredText: "Заголовок (наведение)"
  position: [0.0, 0.85, 0.0]
  scale: [0.5, 0.5, 0.5]
  tolerance: [0.08, 0.04]
  backgroundColor: [40, 40, 40]
  backgroundAlpha: 150
  hoveredBackgroundColor: [60, 60, 60]
  hoveredBackgroundAlpha: 180
  onClick:
    action: NONE
```

### ITEM_BUTTON - кнопка с предметом

```yaml
- id: btn_settings
  type: ITEM_BUTTON
  material: COMPASS
  position: [-0.42, 0.30, 0.0]
  scale: [0.15, 0.15, 0.000001]
  tolerance: [0.06, 0.06]
  tooltip: "Настройки"
  glowOnHover: true
  glowColor: [255, 255, 0]
  onClick:
    action: SWITCH_SCREEN
    target: settings
```

## Действия onClick

- `NONE` - ничего не делать
- `SWITCH_SCREEN` - переключиться на другой экран
- `CLOSE_SCREEN` - закрыть текущий экран
- `RUN_SCRIPT` - выполнить Lua функцию

## Система координат

Позиция виджета задается относительно центра фона:

```
        Y+
        |   
 -X ----+---- +X    (смотришь на экран)
        |
        Y-
```

**Примеры позиций:**
- `[0.0, 0.85, 0.0]` - верх по центру (заголовок)
- `[-0.42, 0.30, 0.0]` - левая колонка
- `[0.35, 0.8, 0.0]` - правый верхний угол
- `[0.0, -0.8, 0.0]` - низ по центру

## Команды

- `/displaylib open <screen_id>` - открыть экран
- `/displaylib close` - закрыть текущий экран
- `/displaylib list` - список доступных экранов
- `/displaylib reload` - перезагрузить экраны и скрипты (админ)
- `/displaylib examples` - создать примеры экранов (админ)

## Разработка

### Hot Reload

При изменении YAML или Lua файлов они автоматически перезагружаются. Для ручной перезагрузки используйте `/displaylib reload`.

### Отладка

Используйте `log.info()`, `log.warn()`, `log.error()` в Lua скриптах для вывода отладочной информации в консоль сервера.

### Структура файлов

```
plugins/DisplayLib/
  screens/           # YAML описания экранов
    main_menu.yml
    settings.yml
    ...
  scripts/           # Lua скрипты
    main_menu.lua
    settings.lua
    shared/          # Общие функции
      utils.lua
```

## Примеры

Полные примеры экранов и скриптов создаются автоматически при первом запуске или командой `/displaylib examples`.

## Документация

Подробная документация доступна в папке `docs/`:
- `LUA_API_GUIDE.md` - полное описание Lua API
- `YAML_DESIGN_GUIDE.md` - руководство по созданию экранов
- `LINK_LUA_AND_YAML.md` - связь между YAML и Lua

## Лицензия

Этот проект распространяется под лицензией MIT.