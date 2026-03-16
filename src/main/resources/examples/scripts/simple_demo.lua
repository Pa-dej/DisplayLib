-- Comprehensive Lua API Demo
-- Полная демонстрация всех возможностей Lua API

local timer_id = nil
local text_index = 1
local animation_timer = nil
local notification_timer = nil

function on_open()
    log.info("=== Comprehensive Demo on_open called ===")
    log.info("Demo opened for " .. player.name())
    
    -- Демонстрация Player API
    player.message("§6🎮 Добро пожаловать в полную демонстрацию Lua API!")
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Демонстрация Screen API - получаем ID экрана
    log.info("Current screen ID: " .. screen.id())
    
    -- Демонстрация Screen.data() - persistent данные экрана
    local session_count = screen.data("session_count", 0) + 1
    screen.data("session_count", session_count)
    log.info("Screen opened " .. session_count .. " times this session")
    
    -- Демонстрация Storage API - данные между сессиями
    local total_visits = storage.get("total_visits", 0) + 1
    storage.set("total_visits", total_visits)
    storage.set("last_visit_time", math.random(1000, 9999))
    
    -- Обновляем счетчик при открытии
    update_counter()
    update_visit_counter()
    
    -- Демонстрация всех доступных API
    log.info("=== API Availability Check ===")
    log.info("Player API available: " .. tostring(player ~= nil))
    log.info("Screen API available: " .. tostring(screen ~= nil))
    log.info("Storage API available: " .. tostring(storage ~= nil))
    log.info("Timer API available: " .. tostring(timer ~= nil))
    log.info("Log API available: " .. tostring(log ~= nil))
    
    -- Демонстрация Player API - информация об игроке
    log.info("Player name: " .. player.name())
    log.info("Player is OP: " .. tostring(player.op()))
    log.info("Player gamemode: " .. player.gamemode())
    log.info("Player health: " .. player.health())
    
    -- Запускаем приветственную анимацию
    start_welcome_animation()
    
    log.info("=== Comprehensive Demo on_open completed ===")
end

function on_close()
    log.info("Comprehensive Demo closed for " .. player.name())
    
    -- Демонстрация Storage API - сохраняем данные сессии
    storage.set("last_screen_closed", screen.id())
    storage.set("session_duration", screen.data("session_count", 0))
    
    -- Отменяем все активные таймеры
    if timer_id then
        timer.cancel(timer_id)
        timer_id = nil
    end
    if animation_timer then
        timer.cancel(animation_timer)
        animation_timer = nil
    end
    if notification_timer then
        timer.cancel(notification_timer)
        notification_timer = nil
    end
    
    -- Демонстрация Player API
    player.message("§7👋 До свидания! Спасибо за тестирование API!")
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
    
    -- Демонстрация Log API - разные уровни логирования
    log.info("Demo session completed successfully")
    log.warn("This is a warning message example")
    log.error("This is an error message example (not a real error)")
end

-- =============================================================================
-- Player API демонстрация - Полная демонстрация всех функций
-- =============================================================================

function btn_heal_click()
    log.info("=== btn_heal_click called ===")
    
    -- Демонстрация Player API - health()
    local old_health = player.health()
    player.health(20)
    local new_health = player.health()
    
    -- Демонстрация Player API - message() с цветом
    player.message("§a❤ Здоровье восстановлено! (" .. old_health .. " → " .. new_health .. ")")
    
    -- Демонстрация Player API - sound() с параметрами
    player.sound("entity.player.levelup", 1.0, 1.0)
    
    -- Демонстрация Storage API - сохраняем статистику
    local heal_count = storage.get("heal_count", 0) + 1
    storage.set("heal_count", heal_count)
    
    log.info("Player healed: " .. old_health .. " -> " .. new_health .. " (total heals: " .. heal_count .. ")")
    log.info("=== btn_heal_click completed ===")
end

function btn_gamemode_click()
    log.info("=== btn_gamemode_click called ===")
    
    -- Демонстрация Player API - gamemode() get/set
    local current = player.gamemode()
    local new_mode = "creative"
    
    if current == "creative" then
        new_mode = "survival"
    elseif current == "survival" then
        new_mode = "adventure"
    elseif current == "adventure" then
        new_mode = "spectator"
    else
        new_mode = "survival"
    end
    
    player.gamemode(new_mode)
    
    -- Демонстрация Player API - message() и sound()
    player.message("§b🎮 Режим изменен: §e" .. current .. " §7→ §e" .. new_mode)
    player.sound("block.note_block.pling", 1.0, 1.5)
    
    -- Демонстрация Storage API
    storage.set("last_gamemode", new_mode)
    local gamemode_changes = storage.get("gamemode_changes", 0) + 1
    storage.set("gamemode_changes", gamemode_changes)
    
    log.info("Gamemode changed: " .. current .. " -> " .. new_mode .. " (total changes: " .. gamemode_changes .. ")")
    log.info("=== btn_gamemode_click completed ===")
end

function btn_sound_click()
    log.info("=== btn_sound_click called ===")
    
    -- Демонстрация Player API - sound() с разными параметрами
    local sounds = {
        {"block.note_block.harp", 1.0, 1.0},
        {"block.note_block.pling", 1.0, 1.5},
        {"block.note_block.chime", 0.8, 2.0},
        {"entity.experience_orb.pickup", 1.0, 1.2},
        {"ui.button.click", 0.5, 0.8}
    }
    
    local sound_index = (storage.get("sound_index", 0) % #sounds) + 1
    storage.set("sound_index", sound_index)
    
    local sound_data = sounds[sound_index]
    player.sound(sound_data[1], sound_data[2], sound_data[3])
    
    -- Демонстрация Player API - message()
    player.message("§d♪ Звук #" .. sound_index .. ": " .. sound_data[1])
    
    log.info("Sound played: " .. sound_data[1] .. " (index: " .. sound_index .. ")")
    log.info("=== btn_sound_click completed ===")
end

function btn_command_click()
    log.info("=== btn_command_click called ===")
    
    -- Демонстрация Player API - op() проверка прав
    if player.op() then
        -- Демонстрация Player API - command()
        player.command("time set day")
        player.message("§a⚡ Команда выполнена: установлено дневное время")
        player.sound("block.beacon.activate", 1.0, 1.0)
        
        -- Демонстрация Storage API
        local commands_used = storage.get("commands_used", 0) + 1
        storage.set("commands_used", commands_used)
        
        log.info("Command executed by OP player (total: " .. commands_used .. ")")
    else
        player.message("§c❌ Нет прав для выполнения команд!")
        player.sound("block.anvil.hit", 0.5, 0.5)
        log.warn("Non-OP player tried to execute command: " .. player.name())
    end
    
    log.info("=== btn_command_click completed ===")
end

-- =============================================================================
-- Storage API демонстрация - Полная демонстрация всех функций
-- =============================================================================

function btn_increment_click()
    log.info("=== btn_increment_click called ===")
    
    -- Демонстрация Storage API - get() с дефолтным значением
    local counter = storage.get("counter", 0)
    counter = counter + 1
    
    -- Демонстрация Storage API - set()
    storage.set("counter", tostring(counter))
    
    update_counter()
    
    -- Демонстрация Player API - sound() с изменяющимся pitch
    player.sound("block.note_block.harp", 1.0, 1.0 + counter * 0.05)
    player.message("§a+1 Счетчик: " .. counter)
    
    -- Особые события с демонстрацией разных API функций
    if counter == 10 then
        player.message("§6🎉 Достигли 10! Демонстрация Timer API...")
        player.sound("entity.player.levelup", 1.0, 1.0)
        
        -- Демонстрация Timer API - after()
        timer.after(20, function()
            player.message("§e⏰ Прошла секунда после достижения 10!")
        end)
        
    elseif counter == 25 then
        player.message("§5✨ Четверть сотни! Демонстрация команд...")
        player.sound("entity.firework_rocket.blast", 1.0, 1.0)
        
        -- Демонстрация Player API - command() (если есть права)
        if player.op() then
            player.command("particle heart ~ ~2 ~ 0.5 0.5 0.5 0 10")
        end
        
    elseif counter == 50 then
        player.message("§c🔥 Полсотни! Демонстрация Storage.has()...")
        
        -- Демонстрация Storage API - has()
        if storage.has("achievement_50") then
            player.message("§7Вы уже получали это достижение ранее")
        else
            storage.set("achievement_50", "true")
            player.message("§a🏆 Новое достижение разблокировано!")
        end
    end
    
    log.info("Counter incremented to: " .. counter)
    log.info("=== btn_increment_click completed ===")
end

function btn_decrement_click()
    log.info("=== btn_decrement_click called ===")
    
    local counter = storage.get("counter", 0)
    
    if counter > 0 then
        counter = counter - 1
        storage.set("counter", tostring(counter))
        
        update_counter()
        player.sound("block.note_block.bass", 1.0, 0.8)
        player.message("§c-1 Счетчик: " .. counter)
        
        -- Демонстрация Storage API - remove() при достижении нуля
        if counter == 0 then
            player.message("§7Счетчик обнулен! Демонстрация Storage.remove()...")
            
            -- Удаляем временные данные
            if storage.has("temp_data") then
                storage.remove("temp_data")
                log.info("Temporary data removed from storage")
            end
        end
        
        log.info("Counter decremented to: " .. counter)
    else
        player.message("§cСчетчик уже равен нулю!")
        player.sound("block.anvil.hit", 0.5, 0.5)
        
        -- Демонстрация Storage API - set() временных данных
        storage.set("temp_data", "zero_attempt_" .. math.random(1000, 9999))
    end
    
    log.info("=== btn_decrement_click completed ===")
end

function btn_storage_demo_click()
    log.info("=== btn_storage_demo_click called ===")
    
    -- Демонстрация всех Storage API функций
    player.message("§b📦 Демонстрация Storage API:")
    
    -- get() с дефолтом
    local demo_value = storage.get("demo_key", "default_value")
    player.message("§7- get('demo_key'): " .. demo_value)
    
    -- set()
    storage.set("demo_key", "new_value_" .. math.random(1000, 9999))
    player.message("§7- set('demo_key', 'new_value')")
    
    -- has()
    local has_key = storage.has("demo_key")
    player.message("§7- has('demo_key'): " .. tostring(has_key))
    
    -- Демонстрация разных типов данных
    storage.set("number_data", 42)
    storage.set("boolean_data", true)
    storage.set("string_data", "Hello World")
    
    player.message("§7- Сохранены разные типы данных")
    
    -- Демонстрация Timer API с Storage
    timer.after(40, function()
        if storage.has("demo_key") then
            storage.remove("demo_key")
            player.message("§7⏰ demo_key удален через 2 секунды")
        end
    end)
    
    player.sound("block.chest.open", 1.0, 1.0)
    log.info("Storage API demonstration completed")
    log.info("=== btn_storage_demo_click completed ===")
end

function update_counter()
    local counter = storage.get("counter", 0)
    local label = screen.widget("counter_label")
    
    if label then
        local counter_text = "Счетчик: " .. counter
        
        -- Демонстрация Widget API - text() и hoveredText()
        label.text(counter_text)
        label.hoveredText(counter_text .. " (клики: " .. counter .. ")")
        
        -- Демонстрация Widget API - bgColor() в зависимости от значения
        if counter == 0 then
            label.bgColor(40, 80, 40)  -- зеленый
        elseif counter < 10 then
            label.bgColor(80, 80, 40)  -- желтый
        elseif counter < 25 then
            label.bgColor(80, 40, 80)  -- фиолетовый
        else
            label.bgColor(80, 40, 40)  -- красный
        end
        
        -- Демонстрация Widget API - bgAlpha()
        local alpha = math.min(255, 100 + counter * 3)
        label.bgAlpha(alpha)
    end
end

function update_visit_counter()
    local visits = storage.get("total_visits", 0)
    local visit_label = screen.widget("visit_counter")
    
    if visit_label then
        -- Демонстрация Widget API
        visit_label.text("Посещений: " .. visits)
        visit_label.hoveredText("Всего открытий экрана: " .. visits)
        
        -- Меняем цвет в зависимости от количества посещений
        if visits < 5 then
            visit_label.bgColor(60, 60, 100)  -- синий
        elseif visits < 15 then
            visit_label.bgColor(100, 60, 60)  -- красный
        else
            visit_label.bgColor(60, 100, 60)  -- зеленый
        end
    end
end

-- =============================================================================
-- Widget API демонстрация - Полная демонстрация всех функций
-- =============================================================================

function btn_change_text_click()
    log.info("=== btn_change_text_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        text_index = text_index + 1
        if text_index > 6 then
            text_index = 1
        end
        
        local texts = {
            "Изменяемый текст",
            "Новый текст!",
            "Lua работает!",
            "DisplayLib API",
            "Widget API Demo",
            "Comprehensive Test"
        }
        
        local new_text = texts[text_index]
        
        -- Демонстрация Widget API - text() и hoveredText()
        demo_text.text(new_text)
        demo_text.hoveredText(new_text .. " (hover #" .. text_index .. ")")
        
        -- Демонстрация Widget API - bgColor() с анимацией
        local colors = {
            {80, 60, 120}, -- фиолетовый
            {120, 80, 60}, -- оранжевый
            {60, 120, 80}, -- зеленый
            {120, 60, 80}, -- розовый
            {80, 120, 60}, -- лайм
            {60, 80, 120}  -- синий
        }
        
        local color = colors[text_index]
        demo_text.bgColor(color[1], color[2], color[3])
        
        -- Демонстрация Widget API - bgAlpha() с анимацией
        demo_text.bgAlpha(100)
        timer.after(5, function()
            demo_text.bgAlpha(180)
        end)
        
        player.sound("block.note_block.chime", 1.0, 1.0 + text_index * 0.1)
        player.message("§bТекст изменен: " .. new_text)
        
        log.info("Text changed to: " .. new_text .. " (index: " .. text_index .. ")")
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_change_text_click completed ===")
end

function btn_toggle_visibility_click()
    log.info("=== btn_toggle_visibility_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        -- Демонстрация Widget API - visible() get/set
        local visible = demo_text.visible()
        log.info("Current visibility: " .. tostring(visible))
        
        demo_text.visible(not visible)
        
        -- Проверяем что изменение применилось
        local new_visible = demo_text.visible()
        log.info("New visibility: " .. tostring(new_visible))
        
        if visible then
            player.message("§7👻 Текст скрыт")
            player.sound("entity.enderman.teleport", 0.5, 0.5)
        else
            player.message("§a✨ Текст показан")
            player.sound("entity.enderman.teleport", 0.5, 1.5)
        end
        
        log.info("Text visibility toggled: " .. tostring(not visible))
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_toggle_visibility_click completed ===")
end

function btn_widget_demo_click()
    log.info("=== btn_widget_demo_click called ===")
    
    -- Демонстрация Widget API - получение виджета через Screen API
    local demo_text = screen.widget("demo_text")
    if demo_text then
        player.message("§b🎛️ Демонстрация Widget API:")
        
        -- Демонстрация enabled()
        local enabled = demo_text.enabled()
        player.message("§7- enabled(): " .. tostring(enabled))
        
        -- Временно отключаем виджет
        demo_text.enabled(false)
        player.message("§7- Виджет отключен на 2 секунды...")
        
        timer.after(40, function()
            demo_text.enabled(true)
            player.message("§7- Виджет снова включен!")
        end)
        
        -- Демонстрация tooltip()
        local old_tooltip = demo_text.tooltip()
        demo_text.tooltip("Новая подсказка от Widget API!")
        player.message("§7- tooltip() изменен")
        
        timer.after(60, function()
            demo_text.tooltip(old_tooltip)
            player.message("§7- tooltip() восстановлен")
        end)
        
        -- Демонстрация анимации цвета
        local original_alpha = 180
        demo_text.bgAlpha(50)
        
        -- Анимация появления
        timer.times(10, 2, function(i)
            local alpha = 50 + (i * 13)
            demo_text.bgAlpha(alpha)
        end)
        
        player.sound("block.enchantment_table.use", 1.0, 1.0)
        log.info("Widget API demonstration started")
    else
        log.error("demo_text widget not found for Widget API demo!")
    end
    
    log.info("=== btn_widget_demo_click completed ===")
end

-- =============================================================================
-- Timer API демонстрация - Полная демонстрация всех функций
-- =============================================================================

function btn_timer_click()
    log.info("=== btn_timer_click called ===")
    
    local status = screen.widget("timer_status")
    
    if timer_id then
        -- Демонстрация Timer API - cancel()
        timer.cancel(timer_id)
        timer_id = nil
        
        if status then
            status.text("Готов к запуску")
            status.bgColor(80, 80, 60)
        end
        
        player.message("§c⏹️ Таймер остановлен")
        player.sound("block.piston.contract", 1.0, 1.0)
        log.info("Timer cancelled")
    else
        -- Демонстрация Timer API - after() с обратным отсчетом
        if status then
            status.text("Таймер: 5...")
            status.bgColor(100, 80, 60)
        end
        
        player.message("§e⏰ Запущен таймер на 5 секунд...")
        player.sound("block.note_block.pling", 1.0, 1.0)
        
        -- Демонстрация Timer API - множественные after()
        timer.after(20, function()  -- 1 сек
            if status then status.text("Таймер: 4...") end
            player.sound("block.note_block.pling", 1.0, 1.1)
        end)
        
        timer.after(40, function()  -- 2 сек
            if status then status.text("Таймер: 3...") end
            player.sound("block.note_block.pling", 1.0, 1.2)
        end)
        
        timer.after(60, function()  -- 3 сек
            if status then status.text("Таймер: 2...") end
            player.sound("block.note_block.pling", 1.0, 1.3)
        end)
        
        timer.after(80, function()  -- 4 сек
            if status then status.text("Таймер: 1...") end
            player.sound("block.note_block.pling", 1.0, 1.4)
        end)
        
        timer.after(100, function()  -- 5 сек
            if status then
                status.text("Готов к запуску")
                status.bgColor(80, 80, 60)
            end
            player.message("§a✓ Таймер завершен!")
            player.sound("entity.player.levelup", 1.0, 1.0)
            log.info("Timer completed")
        end)
        
        log.info("Timer started (5 seconds)")
    end
    
    log.info("=== btn_timer_click completed ===")
end

function btn_repeat_timer_click()
    log.info("=== btn_repeat_timer_click called ===")
    
    if animation_timer then
        -- Останавливаем повторяющийся таймер
        timer.cancel(animation_timer)
        animation_timer = nil
        
        player.message("§c🔄 Повторяющийся таймер остановлен")
        player.sound("block.piston.contract", 1.0, 0.8)
        
        local status = screen.widget("animation_status")
        if status then
            status.text("Анимация остановлена")
            status.bgColor(80, 60, 60)
        end
        
        log.info("Repeat timer cancelled")
    else
        -- Демонстрация Timer API - repeat()
        player.message("§a🔄 Запущена анимация (повторяющийся таймер)")
        player.sound("block.piston.extend", 1.0, 1.2)
        
        local counter = 0
        animation_timer = timer["repeat"](10, function()  -- каждые 0.5 секунды
            counter = counter + 1
            
            local status = screen.widget("animation_status")
            if status then
                status.text("Анимация: " .. counter)
                
                -- Меняем цвет циклично
                local colors = {
                    {100, 60, 60}, -- красный
                    {60, 100, 60}, -- зеленый
                    {60, 60, 100}, -- синий
                    {100, 100, 60}, -- желтый
                    {100, 60, 100}, -- фиолетовый
                    {60, 100, 100}  -- циан
                }
                
                local color_index = ((counter - 1) % #colors) + 1
                local color = colors[color_index]
                status.bgColor(color[1], color[2], color[3])
            end
            
            -- Звук каждые 5 итераций
            if counter % 5 == 0 then
                player.sound("block.note_block.chime", 0.3, 1.0 + counter * 0.05)
            end
        end)
        
        log.info("Repeat timer started")
    end
    
    log.info("=== btn_repeat_timer_click completed ===")
end

function btn_times_timer_click()
    log.info("=== btn_times_timer_click called ===")
    
    -- Демонстрация Timer API - times()
    player.message("§b🎯 Демонстрация Timer.times() - 10 итераций")
    player.sound("block.beacon.activate", 1.0, 1.0)
    
    local progress_widget = screen.widget("progress_bar")
    if progress_widget then
        progress_widget.text("Прогресс: 0/10")
        progress_widget.bgColor(60, 60, 100)
    end
    
    -- times(interval, count, function)
    timer.times(8, 10, function(i)  -- каждые 0.4 секунды, 10 раз
        if progress_widget then
            progress_widget.text("Прогресс: " .. i .. "/10")
            
            -- Меняем цвет от синего к зеленому
            local green_amount = 60 + (i * 4)
            progress_widget.bgColor(60, green_amount, 100 - (i * 4))
        end
        
        -- Звук с повышающимся тоном
        player.sound("block.note_block.harp", 0.5, 0.8 + i * 0.1)
        
        -- Сообщение на важных этапах
        if i == 5 then
            player.message("§e⚡ Половина пути пройдена!")
        elseif i == 10 then
            player.message("§a🎉 Times timer завершен!")
            player.sound("entity.firework_rocket.blast", 1.0, 1.0)
            
            -- Сбрасываем прогресс через 2 секунды
            timer.after(40, function()
                if progress_widget then
                    progress_widget.text("Готов к запуску")
                    progress_widget.bgColor(80, 80, 60)
                end
            end)
        end
        
        log.info("Times timer iteration: " .. i .. "/10")
    end)
    
    log.info("Times timer started (10 iterations)")
    log.info("=== btn_times_timer_click completed ===")
end

-- =============================================================================
-- Screen API демонстрация - Полная демонстрация всех функций
-- =============================================================================

function btn_screen_demo_click()
    log.info("=== btn_screen_demo_click called ===")
    
    -- Демонстрация Screen API
    player.message("§b🖥️ Демонстрация Screen API:")
    
    -- Демонстрация Screen API - id()
    local screen_id = screen.id()
    player.message("§7- screen.id(): " .. screen_id)
    
    -- Демонстрация Screen API - data() get/set
    local demo_data = screen.data("demo_value", "default")
    player.message("§7- screen.data('demo_value'): " .. demo_data)
    
    screen.data("demo_value", "updated_" .. math.random(1000, 9999))
    player.message("§7- screen.data() обновлен")
    
    -- Демонстрация Screen API - widget()
    local widget_count = 0
    local widget_names = {"title", "counter_label", "demo_text", "timer_status"}
    
    for _, name in ipairs(widget_names) do
        local widget = screen.widget(name)
        if widget then
            widget_count = widget_count + 1
        end
    end
    
    player.message("§7- Найдено виджетов: " .. widget_count .. "/" .. #widget_names)
    
    -- Демонстрация с таймером
    timer.after(40, function()
        player.message("§7⏰ Screen API demo завершен через 2 секунды")
    end)
    
    player.sound("block.enchantment_table.use", 1.0, 1.0)
    log.info("Screen API demonstration completed")
    log.info("=== btn_screen_demo_click completed ===")
end

function btn_switch_demo_click()
    log.info("=== btn_switch_demo_click called ===")
    
    -- Демонстрация Screen API - switch() (возврат к главному меню)
    player.message("§e🔄 Демонстрация screen.switch() - возврат к главному меню через 3 секунды...")
    player.sound("block.portal.ambient", 1.0, 1.0)
    
    -- Обратный отсчет
    timer.after(20, function()
        player.message("§e3...")
        player.sound("block.note_block.pling", 1.0, 1.0)
    end)
    
    timer.after(40, function()
        player.message("§e2...")
        player.sound("block.note_block.pling", 1.0, 1.2)
    end)
    
    timer.after(60, function()
        player.message("§e1...")
        player.sound("block.note_block.pling", 1.0, 1.4)
    end)
    
    timer.after(80, function()
        player.message("§a🚀 Переключение экрана!")
        player.sound("entity.enderman.teleport", 1.0, 1.0)
        
        -- Демонстрация Screen API - switch()
        screen.switch("main_menu")
    end)
    
    log.info("Screen switch demonstration started")
    log.info("=== btn_switch_demo_click completed ===")
end

-- =============================================================================
-- Дополнительные демонстрационные функции
-- =============================================================================

function start_welcome_animation()
    log.info("Starting welcome animation")
    
    -- Анимация заголовка
    local title = screen.widget("title")
    if title then
        -- Анимация появления
        title.bgAlpha(0)
        timer.times(5, 4, function(i)
            title.bgAlpha(i * 40)
        end)
        
        -- Меняем цвет заголовка
        timer.after(40, function()
            title.bgColor(100, 120, 80)
            title.text("🎮 Comprehensive Lua API Demo")
        end)
    end
    
    -- Звуковое сопровождение
    timer.after(10, function()
        player.sound("block.note_block.chime", 0.5, 1.0)
    end)
    
    timer.after(20, function()
        player.sound("block.note_block.chime", 0.5, 1.2)
    end)
    
    timer.after(30, function()
        player.sound("block.note_block.chime", 0.5, 1.5)
    end)
end

function btn_notification_demo_click()
    log.info("=== btn_notification_demo_click called ===")
    
    -- Демонстрация системы уведомлений
    show_notification("🔔 Это демонстрация уведомлений!", 60)
    
    timer.after(80, function()
        show_notification("⚡ Второе уведомление с анимацией", 40)
    end)
    
    timer.after(140, function()
        show_notification("✅ Система уведомлений работает!", 60)
    end)
    
    player.sound("block.bell.use", 1.0, 1.0)
    log.info("Notification demo started")
    log.info("=== btn_notification_demo_click completed ===")
end

function show_notification(text, duration)
    local notification = screen.widget("notification")
    if notification then
        notification.text(text)
        notification.visible(true)
        notification.bgAlpha(0)
        
        -- Анимация появления
        timer.times(5, 3, function(i)
            notification.bgAlpha(i * 40)
        end)
        
        -- Скрыть через duration тиков
        timer.after(duration or 60, function()
            -- Анимация исчезновения
            timer.times(5, 3, function(i)
                notification.bgAlpha(200 - i * 40)
            end)
            
            timer.after(15, function()
                notification.visible(false)
            end)
        end)
    end
end

function btn_comprehensive_test_click()
    log.info("=== btn_comprehensive_test_click called ===")
    
    -- Комплексный тест всех API
    player.message("§c🧪 Запуск комплексного теста всех API...")
    player.sound("block.beacon.power_select", 1.0, 1.0)
    
    -- Player API тест
    local player_name = player.name()
    local player_health = player.health()
    local player_gamemode = player.gamemode()
    local player_op = player.op()
    
    log.info("Player API test - Name: " .. player_name .. ", Health: " .. player_health .. ", Gamemode: " .. player_gamemode .. ", OP: " .. tostring(player_op))
    
    -- Storage API тест
    storage.set("test_key", "test_value")
    local has_test = storage.has("test_key")
    local test_value = storage.get("test_key", "default")
    
    log.info("Storage API test - Has key: " .. tostring(has_test) .. ", Value: " .. test_value)
    
    -- Screen API тест
    local screen_id = screen.id()
    screen.data("test_screen_data", "screen_value")
    local screen_data = screen.data("test_screen_data", "default")
    
    log.info("Screen API test - ID: " .. screen_id .. ", Data: " .. screen_data)
    
    -- Widget API тест
    local test_widget = screen.widget("demo_text")
    if test_widget then
        local widget_visible = test_widget.visible()
        local widget_enabled = test_widget.enabled()
        log.info("Widget API test - Visible: " .. tostring(widget_visible) .. ", Enabled: " .. tostring(widget_enabled))
    end
    
    -- Timer API тест
    timer.after(20, function()
        player.message("§a✅ Timer API тест пройден")
        player.sound("entity.experience_orb.pickup", 1.0, 1.0)
    end)
    
    timer.after(40, function()
        player.message("§a🎉 Комплексный тест завершен успешно!")
        player.sound("entity.player.levelup", 1.0, 1.0)
        
        -- Очищаем тестовые данные
        storage.remove("test_key")
        screen.data("test_screen_data", nil)
    end)
    
    log.info("Comprehensive test completed")
    log.info("=== btn_comprehensive_test_click completed ===")
end