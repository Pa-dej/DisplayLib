-- Player API Demo Script
-- Демонстрация всех функций Player API

local sound_index = 1

function on_open()
    log.info("=== Player API Demo opened ===")
    log.info("Player API demo opened for " .. player.name())
    
    -- Приветственное сообщение
    player.message("§c👤 Добро пожаловать в демонстрацию Player API!")
    player.message("§eИзучите все функции для работы с игроком:")
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Показываем информацию об игроке
    update_player_info()
    
    log.info("=== Player API Demo on_open completed ===")
end

function on_close()
    log.info("Player API demo closed for " .. player.name())
    player.message("§7Player API demo завершен!")
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
end

-- =============================================================================
-- Информация об игроке
-- =============================================================================

function btn_player_info_click()
    log.info("=== btn_player_info_click called ===")
    
    -- Демонстрация всех информационных функций Player API
    player.message("§c📊 Информация об игроке:")
    
    -- player.name()
    local name = player.name()
    player.message("§7- §ename(): §f" .. name)
    
    -- player.op()
    local is_op = player.op()
    player.message("§7- §eop(): §f" .. tostring(is_op))
    
    -- player.health()
    local health = player.health()
    player.message("§7- §ehealth(): §f" .. health .. "/20")
    
    -- player.gamemode()
    local gamemode = player.gamemode()
    player.message("§7- §egamemode(): §f" .. gamemode)
    
    player.sound("block.note_block.chime", 1.0, 1.0)
    
    -- Обновляем виджет с информацией
    update_player_info()
    
    log.info("Player info displayed: " .. name .. ", OP: " .. tostring(is_op) .. ", Health: " .. health .. ", Gamemode: " .. gamemode)
    log.info("=== btn_player_info_click completed ===")
end

function update_player_info()
    local info_widget = screen.widget("player_info")
    if info_widget then
        local name = player.name()
        local health = player.health()
        local gamemode = player.gamemode()
        local is_op = player.op()
        
        local info_text = name .. " | " .. health .. "❤ | " .. gamemode
        if is_op then
            info_text = info_text .. " | OP"
        end
        
        info_widget.text(info_text)
        info_widget.hoveredText("Игрок: " .. name .. ", Здоровье: " .. health .. ", Режим: " .. gamemode .. ", Оператор: " .. tostring(is_op))
    end
end

-- =============================================================================
-- Управление здоровьем
-- =============================================================================

function btn_heal_click()
    log.info("=== btn_heal_click called ===")
    
    local old_health = player.health()
    
    -- Демонстрация player.health() setter
    player.health(20)
    
    local new_health = player.health()
    
    player.message("§a❤ Здоровье восстановлено: " .. old_health .. " → " .. new_health)
    player.sound("entity.player.levelup", 1.0, 1.0)
    
    update_player_info()
    
    log.info("Player healed: " .. old_health .. " -> " .. new_health)
    log.info("=== btn_heal_click completed ===")
end

function btn_damage_click()
    log.info("=== btn_damage_click called ===")
    
    local old_health = player.health()
    
    -- Демонстрация player.health() setter с низким значением
    local new_health_value = math.max(1, old_health - 5)  -- Не меньше 1
    player.health(new_health_value)
    
    local new_health = player.health()
    
    player.message("§c💔 Здоровье уменьшено: " .. old_health .. " → " .. new_health)
    player.sound("entity.player.hurt", 1.0, 1.0)
    
    update_player_info()
    
    log.info("Player damaged: " .. old_health .. " -> " .. new_health)
    log.info("=== btn_damage_click completed ===")
end

-- =============================================================================
-- Управление режимом игры
-- =============================================================================

function btn_gamemode_click()
    log.info("=== btn_gamemode_click called ===")
    
    -- Демонстрация player.gamemode() getter и setter
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
    
    player.message("§b🎮 Режим игры изменен: §e" .. current .. " §7→ §e" .. new_mode)
    player.sound("block.note_block.pling", 1.0, 1.5)
    
    update_player_info()
    
    log.info("Gamemode changed: " .. current .. " -> " .. new_mode)
    log.info("=== btn_gamemode_click completed ===")
end

-- =============================================================================
-- Демонстрация звуков
-- =============================================================================

function btn_sound_simple_click()
    log.info("=== btn_sound_simple_click called ===")
    
    -- Демонстрация простого player.sound()
    player.sound("ui.button.click")
    player.message("§d♪ Простой звук: player.sound('ui.button.click')")
    
    log.info("Simple sound played")
    log.info("=== btn_sound_simple_click completed ===")
end

function btn_sound_complex_click()
    log.info("=== btn_sound_complex_click called ===")
    
    -- Демонстрация player.sound() с параметрами
    local volume = 1.0
    local pitch = 1.5
    
    player.sound("block.note_block.pling", volume, pitch)
    player.message("§d♪ Звук с параметрами: volume=" .. volume .. ", pitch=" .. pitch)
    
    log.info("Complex sound played with volume=" .. volume .. ", pitch=" .. pitch)
    log.info("=== btn_sound_complex_click completed ===")
end

function btn_sound_random_click()
    log.info("=== btn_sound_random_click called ===")
    
    -- Демонстрация различных звуков
    local sounds = {
        {"block.note_block.harp", 1.0, 1.0},
        {"block.note_block.chime", 0.8, 2.0},
        {"entity.experience_orb.pickup", 1.0, 1.2},
        {"block.bell.use", 0.5, 1.5},
        {"entity.firework_rocket.blast", 0.7, 1.0}
    }
    
    sound_index = (sound_index % #sounds) + 1
    local sound_data = sounds[sound_index]
    
    player.sound(sound_data[1], sound_data[2], sound_data[3])
    player.message("§d♪ Случайный звук #" .. sound_index .. ": " .. sound_data[1])
    
    log.info("Random sound played: " .. sound_data[1])
    log.info("=== btn_sound_random_click completed ===")
end

-- =============================================================================
-- Демонстрация сообщений
-- =============================================================================

function btn_message_simple_click()
    log.info("=== btn_message_simple_click called ===")
    
    -- Демонстрация простого player.message()
    player.message("Это простое сообщение через player.message()")
    player.sound("ui.button.click", 0.5, 1.0)
    
    log.info("Simple message sent")
    log.info("=== btn_message_simple_click completed ===")
end

function btn_message_colored_click()
    log.info("=== btn_message_colored_click called ===")
    
    -- Демонстрация player.message() с цветами
    local colors = {
        "§cКрасное сообщение",
        "§aЗеленое сообщение", 
        "§bГолубое сообщение",
        "§eЖелтое сообщение",
        "§dРозовое сообщение"
    }
    
    -- Используем math.random вместо os.time()
    local color_index = math.random(1, #colors)
    local colored_message = colors[color_index]
    
    player.message(colored_message)
    player.message("§7Использован код цвета в тексте")
    player.sound("block.note_block.chime", 0.8, 1.2)
    
    log.info("Colored message sent: " .. colored_message)
    log.info("=== btn_message_colored_click completed ===")
end

-- =============================================================================
-- Демонстрация команд
-- =============================================================================

function btn_command_click()
    log.info("=== btn_command_click called ===")
    
    -- Демонстрация player.command() с проверкой прав
    if player.op() then
        -- Демонстрация player.command()
        player.command("time set day")
        player.message("§a⚡ Команда выполнена: /time set day")
        player.message("§7Использована функция player.command()")
        player.sound("block.beacon.activate", 1.0, 1.0)
        
        log.info("Command executed by OP player: time set day")
    else
        player.message("§c❌ Нет прав оператора для выполнения команд!")
        player.message("§7player.op() вернул false")
        player.sound("block.anvil.hit", 0.5, 0.5)
        
        log.warn("Non-OP player tried to execute command: " .. player.name())
    end
    
    log.info("=== btn_command_click completed ===")
end