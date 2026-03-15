-- Lua API Demo Script
-- Демонстрирует все возможности DisplayLib Lua API

-- Глобальные переменные для демонстрации
local repeat_timer_id = nil
local text_variants = {
    "Изменяемый текст",
    "Новый текст!",
    "Lua работает!",
    "DisplayLib API",
    "Отлично! 🎉"
}
local current_text_index = 1

local color_variants = {
    {80, 80, 120},   -- синий
    {120, 80, 80},   -- красный
    {80, 120, 80},   -- зеленый
    {120, 120, 80},  -- желтый
    {120, 80, 120},  -- фиолетовый
}
local current_color_index = 1

-- =============================================================================
-- Жизненный цикл экрана
-- =============================================================================

function on_open()
    log.info("=== Lua Demo Screen Opened ===")
    log.info("Player: " .. player.name())
    log.info("Player is OP: " .. tostring(player.op()))
    log.info("Player gamemode: " .. player.gamemode())
    
    -- Приветствие
    player.message("§6Добро пожаловать в Lua API Demo!")
    player.sound("ui.button.click", 1.0, 1.2)
    
    -- Обновляем информацию об игроке
    update_player_info()
    
    -- Обновляем счетчик
    update_counter_display()
    
    -- Обновляем статистику
    update_statistics()
    
    -- Показываем уведомление
    show_notification("§aЭкран загружен! Исследуйте возможности Lua API", 80)
end

function on_close()
    log.info("Lua Demo Screen closed for " .. player.name())
    
    -- Отменяем все активные таймеры
    if repeat_timer_id then
        timer.cancel(repeat_timer_id)
        repeat_timer_id = nil
    end
    
    -- Сохраняем статистику
    local total_visits = storage.get("total_demo_visits", 0) + 1
    storage.set("total_demo_visits", total_visits)
    
    player.message("§7До свидания! Спасибо за тестирование Lua API")
end

-- =============================================================================
-- Функции управления игроком
-- =============================================================================

function btn_heal_click()
    local current_health = player.health()
    player.health(20)
    
    player.message("§a❤ Здоровье восстановлено! (" .. current_health .. " → 20)")
    player.sound("entity.player.levelup", 1.0, 1.0)
    
    update_player_info()
    show_notification("§aЗдоровье восстановлено!", 40)
    
    log.info("Player " .. player.name() .. " healed from " .. current_health .. " to 20")
end

function btn_gamemode_click()
    local current_mode = player.gamemode()
    local new_mode
    
    if current_mode == "survival" then
        new_mode = "creative"
    elseif current_mode == "creative" then
        new_mode = "adventure"
    elseif current_mode == "adventure" then
        new_mode = "spectator"
    else
        new_mode = "survival"
    end
    
    player.gamemode(new_mode)
    player.message("§bРежим игры изменен: §e" .. current_mode .. " §7→ §e" .. new_mode)
    player.sound("block.note_block.pling", 1.0, 1.5)
    
    update_player_info()
    show_notification("§bРежим: " .. new_mode, 40)
    
    log.info("Player " .. player.name() .. " gamemode changed: " .. current_mode .. " -> " .. new_mode)
end

function btn_sound_click()
    local sounds = {
        "ui.button.click",
        "block.note_block.pling",
        "entity.experience_orb.pickup",
        "block.anvil.hit",
        "entity.villager.yes"
    }
    
    local sound_index = 1 + math.floor(math.random() * 5)  -- 1-5
    local sound = sounds[sound_index]
    local pitch = 0.5 + math.random() * 1.5  -- 0.5 - 2.0
    
    player.sound(sound, 1.0, pitch)
    local pitch_str = tostring(math.floor(pitch * 100) / 100)  -- округляем до 2 знаков
    player.message("§d♪ Звук: §f" .. sound .. " §7(pitch: " .. pitch_str .. ")")
    
    show_notification("§d♪ " .. sound, 30)
end

-- =============================================================================
-- Функции счетчика и storage
-- =============================================================================

function btn_increment_click()
    local counter = storage.get("demo_counter", 0) + 1
    storage.set("demo_counter", counter)
    
    update_counter_display()
    player.sound("block.note_block.harp", 1.0, 1.0 + counter * 0.1)
    
    show_notification("§a+1", 20)
    
    -- Специальные сообщения для определенных значений
    if counter == 10 then
        player.message("§6🎉 Поздравляем! Вы достигли 10!")
        player.sound("entity.player.levelup")
    elseif counter == 50 then
        player.message("§5✨ Невероятно! 50 кликов!")
        player.sound("entity.firework_rocket.blast")
    elseif counter == 100 then
        player.message("§c🔥 ЛЕГЕНДА! 100 кликов!")
        player.sound("entity.ender_dragon.death", 0.5, 2.0)
    end
end

function btn_decrement_click()
    local counter = storage.get("demo_counter", 0)
    if counter > 0 then
        counter = counter - 1
        storage.set("demo_counter", counter)
        
        update_counter_display()
        player.sound("block.note_block.bass", 1.0, 0.8)
        
        show_notification("§c-1", 20)
    else
        player.message("§cСчетчик уже равен нулю!")
        player.sound("block.anvil.hit", 0.5, 0.5)
        show_notification("§cНельзя меньше нуля!", 30)
    end
end

function btn_reset_click()
    local old_counter = storage.get("demo_counter", 0)
    storage.set("demo_counter", 0)
    
    update_counter_display()
    player.message("§eСчетчик сброшен! Было: " .. old_counter)
    player.sound("block.lava.extinguish", 1.0, 1.0)
    
    show_notification("§eСброшено!", 30)
end

function update_counter_display()
    local counter = storage.get("demo_counter", 0)
    local label = screen.widget("counter_label")
    
    if label then
        label.text("Счетчик: " .. counter)
        
        -- Изменяем цвет в зависимости от значения
        if counter == 0 then
            label.bgColor(40, 60, 40)  -- зеленый
        elseif counter < 10 then
            label.bgColor(60, 60, 40)  -- желтый
        elseif counter < 50 then
            label.bgColor(60, 40, 60)  -- фиолетовый
        else
            label.bgColor(60, 40, 40)  -- красный
        end
    end
end

-- =============================================================================
-- Функции демонстрации виджетов
-- =============================================================================

function btn_change_text_click()
    local demo_text = screen.widget("demo_text")
    if demo_text then
        current_text_index = current_text_index + 1
        if current_text_index > 5 then  -- text_variants has 5 elements
            current_text_index = 1
        end
        
        local new_text = text_variants[current_text_index]
        demo_text.text(new_text)
        demo_text.hoveredText(new_text .. " (наведение)")
        
        player.sound("block.note_block.chime", 1.0, 1.0 + current_text_index * 0.2)
        show_notification("§bТекст изменен!", 25)
    end
end

function btn_toggle_visibility_click()
    local demo_text = screen.widget("demo_text")
    if demo_text then
        local is_visible = demo_text.visible()
        demo_text.visible(not is_visible)
        
        if is_visible then
            player.message("§7Текст скрыт")
            show_notification("§7Скрыто", 25)
        else
            player.message("§aТекст показан")
            show_notification("§aПоказано", 25)
        end
        
        player.sound("entity.enderman.teleport", 0.5, 1.5)
    end
end

function btn_change_color_click()
    local demo_text = screen.widget("demo_text")
    if demo_text then
        current_color_index = current_color_index + 1
        if current_color_index > 5 then  -- color_variants has 5 elements
            current_color_index = 1
        end
        
        local color = color_variants[current_color_index]
        demo_text.bgColor(color[1], color[2], color[3])
        demo_text.bgAlpha(180)
        
        player.sound("block.note_block.bell", 1.0, 0.8 + current_color_index * 0.2)
        show_notification("§dЦвет изменен!", 25)
    end
end

-- =============================================================================
-- Функции таймеров и анимаций
-- =============================================================================

function btn_timer_once_click()
    local status = screen.widget("timer_status")
    if status then
        status.text("Таймер: 3...")
        status.bgColor(100, 100, 60)
    end
    
    player.message("§eЗапущен таймер на 3 секунды...")
    player.sound("block.note_block.pling", 1.0, 1.0)
    
    -- Обратный отсчет
    timer.after(20, function()  -- 1 секунда
        if status then status.text("Таймер: 2...") end
        player.sound("block.note_block.pling", 1.0, 1.2)
    end)
    
    timer.after(40, function()  -- 2 секунды
        if status then status.text("Таймер: 1...") end
        player.sound("block.note_block.pling", 1.0, 1.4)
    end)
    
    timer.after(60, function()  -- 3 секунды
        if status then 
            status.text("Готов к запуску")
            status.bgColor(80, 80, 60)
        end
        player.message("§a✓ Таймер завершен!")
        player.sound("entity.player.levelup", 1.0, 1.0)
        show_notification("§aТаймер завершен!", 40)
    end)
end

function btn_timer_repeat_click()
    local status = screen.widget("timer_status")
    
    if repeat_timer_id then
        -- Останавливаем таймер
        timer.cancel(repeat_timer_id)
        repeat_timer_id = nil
        
        if status then
            status.text("Готов к запуску")
            status.bgColor(80, 80, 60)
        end
        
        player.message("§cПовторяющийся таймер остановлен")
        player.sound("block.piston.contract", 1.0, 1.0)
        show_notification("§cТаймер остановлен", 30)
    else
        -- Запускаем таймер
        local tick_count = 0
        repeat_timer_id = timer["repeat"](10, function()  -- каждые 0.5 секунды
            tick_count = tick_count + 1
            
            if status then
                status.text("Тик: " .. tick_count)
                -- Мигающий цвет
                local remainder = tick_count - math.floor(tick_count / 2) * 2
                if remainder == 0 then
                    status.bgColor(100, 60, 60)
                else
                    status.bgColor(60, 100, 60)
                end
            end
            
            local sound_remainder = tick_count - math.floor(tick_count / 5) * 5
            player.sound("block.note_block.hat", 0.3, 1.0 + sound_remainder * 0.2)
        end)
        
        player.message("§bЗапущен повторяющийся таймер (каждые 0.5с)")
        show_notification("§bТаймер запущен", 30)
    end
end

function btn_timer_animation_click()
    local status = screen.widget("timer_status")
    
    player.message("§dЗапуск анимации (5 этапов)...")
    player.sound("entity.firework_rocket.launch", 1.0, 1.0)
    
    timer.times(20, 5, function(i)  -- каждые 1 секунду, 5 раз
        if status then
            status.text("Анимация: " .. i .. "/5")
            
            -- Радужные цвета
            local colors = {
                {255, 100, 100},  -- красный
                {255, 200, 100},  -- оранжевый
                {255, 255, 100},  -- желтый
                {100, 255, 100},  -- зеленый
                {100, 100, 255}   -- синий
            }
            
            local color = colors[i]
            status.bgColor(color[1] / 3, color[2] / 3, color[3] / 3)  -- приглушенные цвета
        end
        
        player.sound("block.note_block.chime", 1.0, 0.5 + i * 0.3)
        
        if i == 5 then
            -- Финальный эффект
            timer.after(10, function()
                if status then
                    status.text("Готов к запуску")
                    status.bgColor(80, 80, 60)
                end
                player.message("§a🎆 Анимация завершена!")
                player.sound("entity.firework_rocket.blast", 1.0, 1.0)
                show_notification("§a🎆 Анимация завершена!", 50)
            end)
        end
    end)
end

-- =============================================================================
-- Вспомогательные функции
-- =============================================================================

function update_player_info()
    local info = screen.widget("player_info")
    if info then
        local health = player.health()
        local mode = player.gamemode()
        local op_status = player.op() and "OP" or "Player"
        
        local text = player.name() .. " (" .. op_status .. ")\\n" ..
                    "HP: " .. health .. "/20\\n" ..
                    "Mode: " .. mode
        
        info.text(text)
        info.hoveredText(text)
        
        -- Цвет в зависимости от здоровья
        if health >= 15 then
            info.bgColor(40, 60, 40)  -- зеленый
        elseif health >= 10 then
            info.bgColor(60, 60, 40)  -- желтый
        else
            info.bgColor(60, 40, 40)  -- красный
        end
    end
end

function update_statistics()
    local stats = screen.widget("stats_label")
    if stats then
        local visits = storage.get("total_demo_visits", 0)
        local counter = storage.get("demo_counter", 0)
        
        local text = "Посещений: " .. visits .. " | Клики: " .. counter
        stats.text(text)
        stats.hoveredText("Статистика использования демо")
    end
end

function show_notification(text, duration)
    local notification = screen.widget("notification")
    if notification then
        notification.text(text)
        notification.visible(true)
        notification.bgAlpha(200)
        
        -- Скрыть через duration тиков
        timer.after(duration or 60, function()
            if notification then
                notification.visible(false)
                notification.bgAlpha(0)
            end
        end)
    end
end

-- =============================================================================
-- Дополнительные демонстрационные функции
-- =============================================================================

-- Функция для демонстрации работы с данными
function demonstrate_storage()
    log.info("=== Storage Demo ===")
    
    -- Сохраняем разные типы данных
    storage.set("string_data", "Hello, Lua!")
    storage.set("number_data", 42.5)
    storage.set("boolean_data", true)
    
    -- Читаем данные
    log.info("String: " .. tostring(storage.get("string_data")))
    log.info("Number: " .. tostring(storage.get("number_data")))
    log.info("Boolean: " .. tostring(storage.get("boolean_data")))
    
    -- Проверяем существование
    log.info("Has string_data: " .. tostring(storage.has("string_data")))
    log.info("Has nonexistent: " .. tostring(storage.has("nonexistent")))
    
    -- Получаем с дефолтным значением
    log.info("Default value: " .. tostring(storage.get("nonexistent", "default")))
end

-- Функция для демонстрации всех возможностей player API
function demonstrate_player_api()
    log.info("=== Player API Demo ===")
    log.info("Name: " .. player.name())
    log.info("OP: " .. tostring(player.op()))
    log.info("Gamemode: " .. player.gamemode())
    log.info("Health: " .. tostring(player.health()))
end

-- Автоматически вызываем демонстрации при открытии (для логов)
timer.after(5, function()
    demonstrate_storage()
    demonstrate_player_api()
end)