-- Comprehensive Lua API Demo
-- Полная демонстрация всех возможностей Lua API

-- Глобальные переменные для демо
local timer_ids = {}
local text_variants = {"Изменяемый текст", "Новый текст!", "Lua работает!", "DisplayLib API", "Демонстрация"}
local text_index = 1
local animation_active = false
local repeat_timer_active = false

-- =============================================================================
-- Lifecycle функции
-- =============================================================================

function on_open()
    log.info("=== Lua Demo opened ===")
    log.info("Demo opened for " .. player.name())
    
    -- Приветствие
    player.message("§6Добро пожаловать в полную демонстрацию Lua API!")
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Инициализация счетчика
    update_counter()
    
    -- Показываем уведомление
    show_notification("§aДемо загружено!", 3)
    
    log.info("=== Lua Demo initialization completed ===")
end

function on_close()
    log.info("Lua Demo closed for " .. player.name())
    
    -- Отменяем все активные таймеры
    cancel_all_timers()
    
    -- Прощальное сообщение
    player.message("§7Спасибо за тестирование Lua API!")
    player.sound("block.note_block.chime", 1.0, 0.8)
end

-- =============================================================================
-- Player API демонстрация
-- =============================================================================

function btn_heal_click()
    log.info("Player heal requested")
    local old_health = player.health()
    player.health(20)
    
    local message = "§a❤ Здоровье восстановлено! (" .. old_health .. " → 20)"
    player.message(message)
    player.sound("entity.player.levelup", 1.0, 1.0)
    
    show_notification("§aИсцеление!", 2)
    log.info("Player healed: " .. old_health .. " -> 20")
end
function btn_gamemode_click()
    local current = player.gamemode()
    local modes = {"survival", "creative", "adventure", "spectator"}
    local mode_names = {
        survival = "§2Выживание",
        creative = "§6Творчество", 
        adventure = "§5Приключение",
        spectator = "§7Наблюдатель"
    }
    
    -- Находим следующий режим
    local next_mode = "survival"
    for i, mode in ipairs(modes) do
        if current == mode then
            next_mode = modes[i + 1] or modes[1]
            break
        end
    end
    
    player.gamemode(next_mode)
    local message = "§bРежим: " .. mode_names[current] .. " §7→ " .. mode_names[next_mode]
    player.message(message)
    player.sound("block.note_block.pling", 1.0, 1.5)
    
    show_notification("§bРежим изменен!", 2)
    log.info("Gamemode changed: " .. current .. " -> " .. next_mode)
end

function btn_sound_click()
    -- Демонстрация различных звуков
    local sounds = {
        "block.note_block.harp",
        "block.note_block.bass", 
        "block.note_block.bell",
        "block.note_block.chime",
        "block.note_block.flute"
    }
    
    local pitches = {1.0, 1.2, 1.5, 0.8, 0.5}
    
    -- Воспроизводим последовательность звуков
    for i, sound in ipairs(sounds) do
        timer.after(i * 5, function()
            player.sound(sound, 1.0, pitches[i])
        end)
    end
    
    player.message("§d♪ Музыкальная демонстрация!")
    show_notification("§d♪ Звуки!", 3)
    log.info("Sound demo played for " .. player.name())
end

-- =============================================================================
-- Storage API демонстрация  
-- =============================================================================

function btn_increment_click()
    log.info("Counter increment requested")
    local counter = tonumber(storage.get("counter", "0")) or 0
    counter = counter + 1
    storage.set("counter", tostring(counter))
    
    update_counter()
    player.sound("block.note_block.harp", 1.0, 1.0 + counter * 0.02)
    player.message("§a+1 Счетчик: " .. counter)
    
    -- Особые достижения
    if counter == 10 then
        player.message("§6🎉 Первая десятка!")
        player.sound("entity.player.levelup", 1.0, 1.0)
        show_notification("§6Достижение: 10!", 3)
    elseif counter == 50 then
        player.message("§5✨ Полсотни!")
        player.sound("entity.firework_rocket.blast", 1.0, 1.0)
        show_notification("§5Достижение: 50!", 3)
    elseif counter == 100 then
        player.message("§c🔥 СОТНЯ! 🔥")
        player.sound("entity.ender_dragon.death", 0.5, 2.0)
        show_notification("§c🔥 СОТНЯ! 🔥", 5)
    end
    
    log.info("Counter incremented to: " .. counter)
end
function btn_decrement_click()
    log.info("Counter decrement requested")
    local counter = tonumber(storage.get("counter", "0")) or 0
    
    if counter > 0 then
        counter = counter - 1
        storage.set("counter", tostring(counter))
        
        update_counter()
        player.sound("block.note_block.bass", 1.0, 0.8)
        player.message("§c-1 Счетчик: " .. counter)
        
        if counter == 0 then
            show_notification("§7Сброшено до нуля", 2)
        end
    else
        player.message("§cСчетчик уже равен нулю!")
        player.sound("block.anvil.hit", 0.5, 0.5)
        show_notification("§cНельзя меньше нуля!", 2)
    end
    
    log.info("Counter decremented to: " .. counter)
end

function btn_reset_click()
    log.info("Counter reset requested")
    local old_counter = tonumber(storage.get("counter", "0")) or 0
    storage.set("counter", "0")
    
    update_counter()
    player.sound("block.anvil.use", 1.0, 1.0)
    player.message("§7Счетчик сброшен с " .. old_counter .. " до 0")
    show_notification("§7Счетчик сброшен!", 2)
    
    log.info("Counter reset from " .. old_counter .. " to 0")
end

function update_counter()
    local counter = tonumber(storage.get("counter", "0")) or 0
    local label = screen.widget("counter_label")
    
    if label then
        local counter_text = "Счетчик: " .. counter
        label.text(counter_text)
        label.hoveredText(counter_text .. " (Storage API)")
        
        -- Динамические цвета в зависимости от значения
        if counter == 0 then
            label.bgColor(40, 80, 40)  -- зеленый
        elseif counter < 10 then
            label.bgColor(80, 80, 40)  -- желтый
        elseif counter < 25 then
            label.bgColor(80, 40, 80)  -- фиолетовый
        elseif counter < 50 then
            label.bgColor(40, 80, 80)  -- голубой
        else
            label.bgColor(80, 40, 40)  -- красный
        end
    end
end

-- =============================================================================
-- Widget API демонстрация
-- =============================================================================

function btn_change_text_click()
    log.info("Text change requested")
    local demo_text = screen.widget("demo_text")
    if demo_text then
        text_index = text_index + 1
        if text_index > #text_variants then
            text_index = 1
        end
        
        local new_text = text_variants[text_index]
        demo_text.text(new_text)
        demo_text.hoveredText(new_text .. " (Widget API)")
        
        -- Меняем цвет фона в зависимости от текста
        local colors = {
            {80, 60, 120}, -- фиолетовый
            {60, 120, 80}, -- зеленый
            {120, 80, 60}, -- оранжевый
            {60, 80, 120}, -- синий
            {120, 60, 80}  -- розовый
        }
        
        local color = colors[text_index]
        demo_text.bgColor(color[1], color[2], color[3])
        
        player.sound("block.note_block.chime", 1.0, 1.0 + text_index * 0.1)
        player.message("§bТекст: " .. new_text)
        show_notification("§bТекст изменен!", 2)
        
        log.info("Text changed to: " .. new_text)
    end
end
function btn_toggle_visibility_click()
    log.info("Visibility toggle requested")
    local demo_text = screen.widget("demo_text")
    if demo_text then
        local visible = demo_text.visible()
        demo_text.visible(not visible)
        
        if visible then
            player.message("§7Текст скрыт")
            show_notification("§7Скрыто", 1)
        else
            player.message("§aТекст показан")
            show_notification("§aПоказано", 1)
        end
        
        player.sound("entity.enderman.teleport", 0.5, 1.5)
        log.info("Text visibility toggled to: " .. tostring(not visible))
    end
end

function btn_change_color_click()
    log.info("Color change requested")
    local demo_text = screen.widget("demo_text")
    if demo_text then
        -- Случайные цвета
        local r = math.random(40, 120)
        local g = math.random(40, 120) 
        local b = math.random(40, 120)
        
        demo_text.bgColor(r, g, b)
        
        player.sound("block.note_block.bit", 1.0, math.random(80, 150) / 100)
        player.message("§bЦвет изменен на RGB(" .. r .. ", " .. g .. ", " .. b .. ")")
        show_notification("§bНовый цвет!", 2)
        
        log.info("Color changed to RGB(" .. r .. ", " .. g .. ", " .. b .. ")")
    end
end

-- =============================================================================
-- Timer API демонстрация
-- =============================================================================

function btn_timer_once_click()
    log.info("One-time timer requested")
    local status = screen.widget("timer_status")
    
    if status then
        status.text("Таймер: 5 секунд...")
        status.bgColor(100, 80, 60)
    end
    
    player.message("§eЗапущен одноразовый таймер на 5 секунд")
    player.sound("block.note_block.pling", 1.0, 1.0)
    show_notification("§eТаймер запущен!", 2)
    
    -- Обратный отсчет
    for i = 1, 4 do
        timer.after(i * 20, function()
            if status then 
                status.text("Таймер: " .. (5-i) .. " секунд...")
            end
            player.sound("block.note_block.pling", 1.0, 1.0 + i * 0.1)
        end)
    end
    
    -- Завершение
    timer.after(100, function()
        if status then
            status.text("Готов к запуску")
            status.bgColor(80, 80, 60)
        end
        player.message("§a✓ Одноразовый таймер завершен!")
        player.sound("entity.player.levelup", 1.0, 1.0)
        show_notification("§a✓ Таймер завершен!", 3)
        log.info("One-time timer completed")
    end)
    
    log.info("One-time timer started (5 seconds)")
end