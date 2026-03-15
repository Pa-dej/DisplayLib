-- Simple Lua API Demo Script
-- Простая демонстрация Lua API без сложных конструкций

function on_open()
    log.info("Lua Demo opened for " .. player.name())
    player.message("§6Добро пожаловать в Lua API Demo!")
    player.sound("ui.button.click", 1.0, 1.2)
    update_player_info()
    update_counter_display()
end

function on_close()
    log.info("Lua Demo closed for " .. player.name())
    player.message("§7До свидания!")
end

function btn_heal_click()
    local current_health = player.health()
    player.health(20)
    player.message("§a❤ Здоровье восстановлено!")
    player.sound("entity.player.levelup", 1.0, 1.0)
    update_player_info()
end

function btn_gamemode_click()
    local current_mode = player.gamemode()
    local new_mode = "creative"
    if current_mode == "creative" then
        new_mode = "survival"
    end
    
    player.gamemode(new_mode)
    player.message("§bРежим игры изменен на: " .. new_mode)
    player.sound("block.note_block.pling", 1.0, 1.5)
    update_player_info()
end

function btn_sound_click()
    player.sound("block.note_block.harp", 1.0, 1.0)
    player.message("§d♪ Звук воспроизведен!")
end

function btn_increment_click()
    local counter = storage.get("demo_counter", 0) + 1
    storage.set("demo_counter", counter)
    update_counter_display()
    player.sound("block.note_block.harp", 1.0, 1.0)
    player.message("§a+1 Счетчик: " .. counter)
end

function btn_decrement_click()
    local counter = storage.get("demo_counter", 0)
    if counter > 0 then
        counter = counter - 1
        storage.set("demo_counter", counter)
        update_counter_display()
        player.sound("block.note_block.bass", 1.0, 0.8)
        player.message("§c-1 Счетчик: " .. counter)
    else
        player.message("§cСчетчик уже равен нулю!")
    end
end

function btn_reset_click()
    storage.set("demo_counter", 0)
    update_counter_display()
    player.message("§eСчетчик сброшен!")
    player.sound("block.lava.extinguish", 1.0, 1.0)
end

function btn_change_text_click()
    local demo_text = screen.widget("demo_text")
    if demo_text then
        demo_text.text("Текст изменен!")
        player.sound("block.note_block.chime", 1.0, 1.0)
        player.message("§bТекст изменен!")
    end
end

function btn_toggle_visibility_click()
    local demo_text = screen.widget("demo_text")
    if demo_text then
        local is_visible = demo_text.visible()
        demo_text.visible(not is_visible)
        if is_visible then
            player.message("§7Текст скрыт")
        else
            player.message("§aТекст показан")
        end
        player.sound("entity.enderman.teleport", 0.5, 1.5)
    end
end

function btn_change_color_click()
    local demo_text = screen.widget("demo_text")
    if demo_text then
        demo_text.bgColor(120, 80, 80)
        demo_text.bgAlpha(180)
        player.sound("block.note_block.bell", 1.0, 1.0)
        player.message("§dЦвет изменен!")
    end
end

function btn_timer_once_click()
    player.message("§eЗапущен таймер на 3 секунды...")
    player.sound("block.note_block.pling", 1.0, 1.0)
    
    timer.after(60, function()  -- 3 секунды
        player.message("§a✓ Таймер завершен!")
        player.sound("entity.player.levelup", 1.0, 1.0)
    end)
end

function btn_timer_repeat_click()
    player.message("§bЗапущен повторяющийся таймер")
    player.sound("block.note_block.pling", 1.0, 1.0)
    
    -- Простой повторяющийся таймер без сложной логики
    timer["repeat"](20, function()  -- каждую секунду
        player.sound("block.note_block.hat", 0.3, 1.0)
    end)
end

function btn_timer_animation_click()
    player.message("§dЗапуск анимации...")
    player.sound("entity.firework_rocket.launch", 1.0, 1.0)
    
    timer.times(20, 3, function(i)  -- 3 раза каждую секунду
        player.sound("block.note_block.chime", 1.0, 0.5 + i * 0.3)
        player.message("§aАнимация: " .. i .. "/3")
    end)
end

function update_player_info()
    local info = screen.widget("player_info")
    if info then
        local health = player.health()
        local mode = player.gamemode()
        local text = player.name() .. "\\nHP: " .. health .. "/20\\nMode: " .. mode
        info.text(text)
    end
end

function update_counter_display()
    local counter_val = storage.get("demo_counter", 0)
    local label = screen.widget("counter_label")
    if label then
        label.text("Счетчик: " .. counter_val)
    end
end