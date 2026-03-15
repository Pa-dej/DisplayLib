# DisplayLib Lua API - Полное руководство

## Введение

DisplayLib предоставляет мощный Lua API для создания интерактивных UI экранов в Minecraft. Каждый экран может иметь свой Lua скрипт, который выполняется в безопасной изолированной среде.

## Архитектура

### Жизненный цикл экрана

```lua
-- Вызывается при открытии экрана
function on_open()
    -- Инициализация, настройка виджетов
end

-- Вызывается при закрытии экрана
function on_close()
    -- Очистка, сохранение данных
end

-- Вызывается при клике на виджет
function widget_click_function()
    -- widget доступен в контексте
end
```

### Контекст выполнения

Каждый экран имеет изолированный Lua контекст с предустановленными объектами:
- `player` - текущий игрок
- `screen` - текущий экран
- `widget` - виджет (только в функциях клика)
- `storage` - хранилище данных
- `timer` - система таймеров
- `log` - логирование

## API Reference

### 🎮 `player` - Игрок

Объект для работы с игроком, открывшим экран.

#### Информация об игроке

```lua
-- Получить имя игрока
local name = player.name()
-- Возвращает: string

-- Проверить права оператора
local isOp = player.op()
-- Возвращает: boolean
```

#### Режим игры

```lua
-- Получить текущий режим игры
local mode = player.gamemode()
-- Возвращает: "survival" | "creative" | "adventure" | "spectator"

-- Установить режим игры
player.gamemode("creative")
-- Параметры: "survival" | "creative" | "adventure" | "spectator"
```

#### Здоровье

```lua
-- Получить текущее здоровье (0-20)
local hp = player.health()
-- Возвращает: number (0.0 - 20.0)

-- Установить здоровье
player.health(20)
-- Параметры: number (0-20)
```

#### Сообщения

```lua
-- Отправить обычное сообщение
player.message("Привет!")

-- Отправить сообщение с цветом
player.message("Ошибка!", "#ff0000")  -- красный
player.message("Успех!", "#00ff00")   -- зеленый
```

#### Звуки

```lua
-- Простой звук
player.sound("ui.button.click")

-- Звук с громкостью и тоном
player.sound("block.note_block.pling", 1.0, 2.0)
-- Параметры: sound_name, volume (0.0-1.0), pitch (0.5-2.0)
```

#### Команды

```lua
-- Выполнить команду от имени игрока
player.command("tp ~ ~10 ~")
player.command("/gamemode creative")  -- / опционален
```

---

### 🖥️ `screen` - Экран

Объект для управления текущим экраном.

#### Информация об экране

```lua
-- Получить ID экрана
local screenId = screen.id()
-- Возвращает: string
```

#### Навигация

```lua
-- Закрыть текущий экран
screen.close()

-- Переключиться на другой экран
screen.switch("main_menu")
-- Сохраняет позицию и ориентацию экрана
```

#### Работа с виджетами

```lua
-- Получить виджет по ID
local widget = screen.widget("btn_start")
-- Возвращает: widget объект или nil

-- Пример использования
local button = screen.widget("btn_start")
if button then
    button.text("Новый текст")
    button.visible(true)
end
```

#### Persistent Data

Данные, которые живут пока экран открыт:

```lua
-- Получить значение
local value = screen.data("key")
-- Возвращает: любой тип или nil

-- Установить значение
screen.data("key", "value")
screen.data("counter", 42)
screen.data("flag", true)

-- Удалить значение
screen.data("key", nil)
```

---

### 🎛️ `widget` - Виджет

Объект доступен только в функциях клика виджетов.

#### Текст (только TEXT_BUTTON)

```lua
-- Получить текст
local text = widget.text()
-- Возвращает: string

-- Установить текст
widget.text("Новый текст")

-- Установить текст при наведении
widget.hoveredText("Текст при наведении")
```

#### Видимость и состояние

```lua
-- Проверить видимость
local visible = widget.visible()
-- Возвращает: boolean

-- Показать/скрыть виджет
widget.visible(true)   -- показать
widget.visible(false)  -- скрыть

-- Проверить активность
local enabled = widget.enabled()
-- Возвращает: boolean

-- Включить/отключить виджет
widget.enabled(true)   -- включить
widget.enabled(false)  -- отключить (не реагирует на клики)
```

#### Tooltip

```lua
-- Получить подсказку
local tip = widget.tooltip()
-- Возвращает: string или nil

-- Установить подсказку
widget.tooltip("Новая подсказка")
widget.tooltip(nil)  -- убрать подсказку
```

#### Цвет фона (только TEXT_BUTTON)

```lua
-- Установить цвет фона (RGB)
widget.bgColor(255, 0, 0)    -- красный
widget.bgColor(0, 255, 0)    -- зеленый
widget.bgColor(40, 40, 40)   -- темно-серый

-- Установить прозрачность фона (0-255)
widget.bgAlpha(150)  -- полупрозрачный
widget.bgAlpha(0)    -- полностью прозрачный
widget.bgAlpha(255)  -- непрозрачный
```

---

### 💾 `storage` - Хранилище данных

Система для хранения данных между открытиями экранов. Данные сбрасываются при выходе игрока с сервера.

#### Основные операции

```lua
-- Получить значение
local value = storage.get("key")
-- Возвращает: любой тип или nil

-- Получить с дефолтным значением
local visits = storage.get("visits", 0)
local name = storage.get("player_name", "Unknown")

-- Установить значение
storage.set("visits", 5)
storage.set("last_screen", "main_menu")
storage.set("settings", {sound = true, music = false})

-- Проверить наличие ключа
local exists = storage.has("visits")
-- Возвращает: boolean

-- Удалить ключ
storage.remove("old_data")

-- Очистить все данные игрока
storage.clear()
```

#### Поддерживаемые типы данных

```lua
-- Строки
storage.set("name", "Player123")

-- Числа
storage.set("level", 42)
storage.set("health", 19.5)

-- Булевы значения
storage.set("premium", true)
storage.set("muted", false)
```

---

### ⏰ `timer` - Система таймеров

Мощная система для создания отложенных действий и анимаций.

#### Одноразовые таймеры

```lua
-- Выполнить через N тиков (20 тиков = 1 секунда)
timer.after(20, function()
    player.message("Прошла секунда!")
end)

-- Закрыть экран через 5 секунд
timer.after(100, function()
    screen.close()
end)
```

#### Повторяющиеся таймеры

```lua
-- Повторять каждые N тиков
local timerId = timer.repeat(10, function()
    player.message("Каждые полсекунды")
end)

-- Остановить таймер
timer.cancel(timerId)
```

#### Ограниченные повторения

```lua
-- Выполнить N раз с интервалом
timer.times(20, 5, function(i)
    player.message("Отсчет: " .. i .. "/5")
    -- i = 1, 2, 3, 4, 5
end)

-- Анимация исчезновения
timer.times(5, 10, function(i)
    local alpha = 255 - (i * 25)
    screen.widget("title").bgAlpha(alpha)
end)
```

#### Управление таймерами

```lua
-- Все таймеры автоматически отменяются при закрытии экрана
-- Ручная отмена:
local id = timer.repeat(10, function() end)
timer.cancel(id)
```

---

### 📝 `log` - Логирование

Система для вывода отладочной информации в консоль сервера.

```lua
-- Информационное сообщение
log.info("Экран открыт для " .. player.name())

-- Предупреждение
log.warn("Игрок пытается получить доступ без прав")

-- Ошибка
log.error("Не удалось загрузить данные игрока")
```

Сообщения появляются в консоли с префиксом `[DisplayLib/Lua]`.

---

## Практические примеры

### Счетчик посещений

```lua
function on_open()
    local visits = storage.get("visits", 0) + 1
    storage.set("visits", visits)
    
    local title = screen.widget("title")
    title.text("Посещений: " .. visits)
    
    log.info("Игрок " .. player.name() .. " открыл экран " .. visits .. " раз")
end
```

### Система прав доступа

```lua
function on_open()
    if not player.op() then
        screen.widget("admin_button").visible(false)
        screen.widget("warning").text("Нет прав администратора")
    end
end

function admin_button_click()
    if not player.op() then
        player.message("§cДоступ запрещен!", "#ff0000")
        player.sound("block.note_block.bass", 1.0, 0.5)
        return
    end
    
    screen.switch("admin_panel")
end
```

### Анимированное меню

```lua
function on_open()
    -- Скрываем все кнопки
    for i = 1, 5 do
        screen.widget("btn_" .. i).visible(false)
    end
    
    -- Показываем их по очереди с задержкой
    for i = 1, 5 do
        timer.after(i * 10, function()
            screen.widget("btn_" .. i).visible(true)
            player.sound("ui.button.click", 0.5, 1.0 + i * 0.2)
        end)
    end
end
```

### Интерактивный переключатель

```lua
function sound_toggle_click()
    local muted = storage.get("sound_muted", false)
    storage.set("sound_muted", not muted)
    
    local button = screen.widget("sound_toggle")
    if muted then
        button.text("🔊 Звук ВКЛ")
        button.tooltip("Нажмите чтобы выключить звук")
        player.message("§aЗвук включен")
    else
        button.text("🔇 Звук ВЫКЛ")
        button.tooltip("Нажмите чтобы включить звук")
        player.message("§cЗвук выключен")
    end
    
    player.sound("ui.button.click")
end
```

### Система уведомлений

```lua
function show_notification(text, duration)
    local notification = screen.widget("notification")
    notification.text(text)
    notification.visible(true)
    notification.bgAlpha(200)
    
    -- Анимация появления
    timer.times(2, 10, function(i)
        notification.bgAlpha(i * 100)
    end)
    
    -- Скрыть через duration тиков
    timer.after(duration or 60, function()
        -- Анимация исчезновения
        timer.times(2, 10, function(i)
            notification.bgAlpha(200 - i * 100)
        end)
        
        timer.after(20, function()
            notification.visible(false)
        end)
    end)
end

function some_button_click()
    show_notification("§aДействие выполнено!", 40)
end
```

### Мини-игра: Угадай число

```lua
local secret_number = 0

function on_open()
    secret_number = math.random(1, 10)
    screen.data("attempts", 0)
    screen.widget("hint").text("Угадайте число от 1 до 10")
    log.info("Загадано число: " .. secret_number)
end

function guess_button_click()
    local attempts = screen.data("attempts", 0) + 1
    screen.data("attempts", attempts)
    
    local guess = attempts  -- В реальности можно получить от игрока
    local hint = screen.widget("hint")
    
    if guess == secret_number then
        hint.text("§aПоздравляем! Угадали за " .. attempts .. " попыток!")
        player.sound("entity.player.levelup")
        
        timer.after(60, function()
            screen.close()
        end)
    elseif guess < secret_number then
        hint.text("§eЗагаданное число больше. Попытка " .. attempts)
        player.sound("block.note_block.pling", 1.0, 0.8)
    else
        hint.text("§eЗагаданное число меньше. Попытка " .. attempts)
        player.sound("block.note_block.pling", 1.0, 1.2)
    end
end
```

---

## Лучшие практики

### 1. Проверка существования виджетов

```lua
function safe_widget_update()
    local widget = screen.widget("my_button")
    if widget then
        widget.text("Новый текст")
    else
        log.warn("Виджет my_button не найден")
    end
end
```

### 2. Обработка ошибок

```lua
function safe_operation()
    local success, error = pcall(function()
        -- Потенциально опасная операция
        local data = storage.get("complex_data")
        -- обработка данных
    end)
    
    if not success then
        log.error("Ошибка в safe_operation: " .. tostring(error))
        player.message("§cПроизошла ошибка")
    end
end
```

### 3. Очистка ресурсов

```lua
function on_close()
    -- Сохранить важные данные
    storage.set("last_position", screen.data("current_pos"))
    
    -- Очистить временные данные
    screen.data("temp_data", nil)
    
    log.info("Экран закрыт, данные сохранены")
end
```

### 4. Модульность

```lua
-- Вынесение общих функций
function play_ui_sound()
    if not storage.get("sound_muted", false) then
        player.sound("ui.button.click")
    end
end

function update_counter_display()
    local counter = storage.get("counter", 0)
    screen.widget("counter_label").text("Счетчик: " .. counter)
end

function increment_button_click()
    local counter = storage.get("counter", 0) + 1
    storage.set("counter", counter)
    update_counter_display()
    play_ui_sound()
end
```

---

## Ограничения и безопасность

### Отключенные функции

Для безопасности следующие Lua функции недоступны:
- `io.*` - файловые операции
- `os.*` - системные вызовы
- `package.*` - загрузка модулей
- `require()` - импорт модулей
- `dofile()`, `loadfile()`, `load()` - выполнение кода

### Доступные стандартные функции

- `math.*` - математические функции
- `string.*` - работа со строками
- `table.*` - работа с таблицами
- `ipairs()`, `pairs()` - итераторы
- `type()`, `tostring()`, `tonumber()` - конвертация типов
- `pcall()`, `error()` - обработка ошибок

### Изоляция

- Каждый экран имеет свой изолированный контекст
- Глобальные переменные одного скрипта не видны другому
- Автоматическая очистка ресурсов при закрытии экрана

---

## Отладка

### Логирование

```lua
function debug_info()
    log.info("=== Debug Info ===")
    log.info("Player: " .. player.name())
    log.info("Screen: " .. screen.id())
    log.info("Storage keys: " .. table.concat(storage.keys or {}, ", "))
end
```

### Проверка состояния

```lua
function on_open()
    log.info("Screen opened for " .. player.name())
    log.info("Player is OP: " .. tostring(player.op()))
    log.info("Player gamemode: " .. player.gamemode())
end
```

### Hot Reload

При изменении Lua файлов используйте `/displaylib reload` для перезагрузки без перезапуска сервера.

---

## Заключение

Lua API DisplayLib предоставляет все необходимые инструменты для создания интерактивных и динамичных UI экранов. Комбинируя простоту YAML конфигурации с мощностью Lua скриптов, вы можете создавать сложные пользовательские интерфейсы для своих Minecraft проектов.

Помните о безопасности, используйте логирование для отладки и следуйте лучшим практикам для создания надежных и производительных скриптов.