-- Simple Lua API Demo
-- Простая демонстрация возможностей Lua API

local timer_id = nil
local text_index = 1

function on_open()
    log.info("=== Simple Demo on_open called ===")
    log.info("Simple Demo opened for " .. player.name())
    player.message("§6Добро пожаловать в Lua API Demo!")
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Обновляем счетчик при открытии
    update_counter()
    
    -- Проверяем доступность API
    log.info("Player API available: " .. tostring(player ~= nil))
    log.info("Screen API available: " .. tostring(screen ~= nil))
    log.info("Storage API available: " .. tostring(storage ~= nil))
    log.info("Timer API available: " .. tostring(timer ~= nil))
    log.info("Log API available: " .. tostring(log ~= nil))
    
    log.info("=== Simple Demo on_open completed ===")
end

function on_close()
    log.info("Simple Demo closed for " .. player.name())
    
    -- Отменяем таймер если он активен
    if timer_id then
        timer.cancel(timer_id)
        timer_id = nil
    end
    
    player.message("§7До свидания!")
end

-- =============================================================================
-- Player API демонстрация
-- =============================================================================

function btn_heal_click()
    log.info("=== btn_heal_click called ===")
    local old_health = player.health()
    player.health(20)
    
    player.message("§a❤ Здоровье восстановлено! (" .. old_health .. " → 20)")
    player.sound("entity.player.levelup", 1.0, 1.0)
    
    log.info("Player healed: " .. old_health .. " -> 20")
    log.info("=== btn_heal_click completed ===")
end

function btn_gamemode_click()
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
    player.message("§bРежим изменен: §e" .. current .. " §7→ §e" .. new_mode)
    player.sound("block.note_block.pling", 1.0, 1.5)
    
    log.info("Gamemode changed: " .. current .. " -> " .. new_mode)
end

function btn_sound_click()
    -- Простой звук без случайности
    player.sound("block.note_block.harp", 1.0, 1.0)
    player.message("§d♪ Звук воспроизведен!")
    
    log.info("Sound played for " .. player.name())
end

-- =============================================================================
-- Storage API демонстрация
-- =============================================================================

function btn_increment_click()
    log.info("=== btn_increment_click called ===")
    local counter = tonumber(storage.get("counter", "0")) or 0
    counter = counter + 1
    storage.set("counter", tostring(counter))
    
    update_counter()
    player.sound("block.note_block.harp", 1.0, 1.0 + counter * 0.05)
    player.message("§a+1 Счетчик: " .. counter)
    
    -- Особые сообщения
    if counter == 10 then
        player.message("§6🎉 Достигли 10!")
        player.sound("entity.player.levelup", 1.0, 1.0)
    elseif counter == 25 then
        player.message("§5✨ Четверть сотни!")
        player.sound("entity.firework_rocket.blast", 1.0, 1.0)
    end
    
    log.info("Counter incremented to: " .. counter)
    log.info("=== btn_increment_click completed ===")
end

function btn_decrement_click()
    log.info("=== btn_decrement_click called ===")
    local counter = tonumber(storage.get("counter", "0")) or 0
    
    if counter > 0 then
        counter = counter - 1
        storage.set("counter", tostring(counter))
        
        update_counter()
        player.sound("block.note_block.bass", 1.0, 0.8)
        player.message("§c-1 Счетчик: " .. counter)
        
        log.info("Counter decremented to: " .. counter)
    else
        player.message("§cСчетчик уже равен нулю!")
        player.sound("block.anvil.hit", 0.5, 0.5)
    end
    log.info("=== btn_decrement_click completed ===")
end

function update_counter()
    local counter = tonumber(storage.get("counter", "0")) or 0
    local label = screen.widget("counter_label")
    
    if label then
        local counter_text = "Счетчик: " .. counter
        label.text(counter_text)
        label.hoveredText(counter_text)  -- Устанавливаем тот же текст для hover
        
        -- Меняем цвет в зависимости от значения
        if counter == 0 then
            label.bgColor(40, 80, 40)  -- зеленый
        elseif counter < 10 then
            label.bgColor(80, 80, 40)  -- желтый
        elseif counter < 25 then
            label.bgColor(80, 40, 80)  -- фиолетовый
        else
            label.bgColor(80, 40, 40)  -- красный
        end
    end
end

-- =============================================================================
-- Widget API демонстрация
-- =============================================================================

function btn_change_text_click()
    local demo_text = screen.widget("demo_text")
    if demo_text then
        text_index = text_index + 1
        if text_index > 4 then
            text_index = 1
        end
        
        local texts = {
            "Изменяемый текст",
            "Новый текст!",
            "Lua работает!",
            "DisplayLib API"
        }
        
        local new_text = texts[text_index]
        demo_text.text(new_text)
        demo_text.hoveredText(new_text .. " (hover)")
        
        player.sound("block.note_block.chime", 1.0, 1.0)
        player.message("§bТекст изменен: " .. new_text)
        
        log.info("Text changed to: " .. new_text)
    end
end

function btn_toggle_visibility_click()
    log.info("=== btn_toggle_visibility_click called ===")
    local demo_text = screen.widget("demo_text")
    if demo_text then
        local visible = demo_text.visible()
        log.info("Current visibility: " .. tostring(visible))
        
        demo_text.visible(not visible)
        
        -- Проверяем что изменение применилось
        local new_visible = demo_text.visible()
        log.info("New visibility: " .. tostring(new_visible))
        
        if visible then
            player.message("§7Текст скрыт")
        else
            player.message("§aТекст показан")
        end
        
        player.sound("entity.enderman.teleport", 0.5, 1.5)
        log.info("Text visibility toggled: " .. tostring(not visible))
    else
        log.info("demo_text widget not found!")
    end
    log.info("=== btn_toggle_visibility_click completed ===")
end

-- =============================================================================
-- Timer API демонстрация
-- =============================================================================

function btn_timer_click()
    local status = screen.widget("timer_status")
    
    if timer_id then
        -- Останавливаем таймер
        timer.cancel(timer_id)
        timer_id = nil
        
        if status then
            status.text("Готов к запуску")
            status.bgColor(80, 80, 60)
        end
        
        player.message("§cТаймер остановлен")
        player.sound("block.piston.contract", 1.0, 1.0)
        log.info("Timer stopped")
    else
        -- Запускаем простой таймер
        if status then
            status.text("Таймер: 5...")
            status.bgColor(100, 80, 60)
        end
        
        player.message("§eЗапущен таймер на 5 секунд...")
        player.sound("block.note_block.pling", 1.0, 1.0)
        
        -- Обратный отсчет
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
end