-- Main Menu Script
-- Главное меню без команд

function on_open()
    log.info("Main menu opened for " .. player.name())
    player.message("§6Добро пожаловать в DisplayLib!")
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Показываем информацию об игроке
    local health = player.health()
    local mode = player.gamemode()
    local op_status = player.op() and "OP" or "Player"
    
    log.info("Player info - Name: " .. player.name() .. ", Health: " .. health .. ", Mode: " .. mode .. ", OP: " .. tostring(player.op()))
end

function on_close()
    log.info("Main menu closed for " .. player.name())
    player.message("§7До свидания!")
end

function btn_info_click()
    player.message("§b=== DisplayLib Info ===")
    player.message("§7Плагин для создания 3D интерфейсов")
    player.message("§7С поддержкой Lua скриптов")
    player.message("§7Версия: 2.0.0")
    
    player.sound("block.note_block.chime", 1.0, 1.0)
    log.info("Info button clicked by " .. player.name())
end

function btn_test_click()
    player.message("§e=== Тест функций ===")
    
    -- Тестируем Player API
    local health = player.health()
    local mode = player.gamemode()
    player.message("§aЗдоровье: " .. health .. "/20")
    player.message("§aРежим: " .. mode)
    player.message("§aОП: " .. (player.op() and "Да" or "Нет"))
    
    -- Тестируем Storage API
    local test_value = storage.get("test_counter", 0) + 1
    storage.set("test_counter", test_value)
    player.message("§aТест счетчик: " .. test_value)
    
    -- Тестируем звуки
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
    
    log.info("Test functions executed for " .. player.name())
end