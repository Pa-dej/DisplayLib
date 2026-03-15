-- Main Menu Script
-- Главное меню для выбора API демонстраций

function on_open()
    log.info("=== Main Menu opened ===")
    log.info("Main menu opened for " .. player.name())
    
    -- Приветственное сообщение
    player.message("§6🎮 Добро пожаловать в DisplayLib Lua API Demo!")
    player.message("§eВыберите API для изучения:")
    player.message("§7- §bPlayer API §7- управление игроком")
    player.message("§7- §aStorage API §7- хранение данных")
    player.message("§7- §dWidget API §7- управление виджетами")
    player.message("§7- §eTimer API §7- система таймеров")
    player.message("§7- §cScreen API §7- управление экранами")
    
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Анимация появления заголовка
    local title = screen.widget("title")
    if title then
        title.bgAlpha(0)
        timer.after(5, function()
            title.bgAlpha(100)
        end)
        timer.after(10, function()
            title.bgAlpha(200)
        end)
    end
    
    log.info("=== Main Menu on_open completed ===")
end

function on_close()
    log.info("Main menu closed for " .. player.name())
    player.message("§7До свидания!")
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
end