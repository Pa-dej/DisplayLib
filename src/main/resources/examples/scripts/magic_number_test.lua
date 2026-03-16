-- Magic Number Test Script
-- Демонстрирует формулу для выравнивания фона

function on_open()
    log.info("=== Magic Number Test opened ===")
    log.info("Формула для выравнивания фона: translation_x = -scale_x / 80")
    
    player.message("§6Magic Number Formula Test")
    player.message("§7")
    player.message("§eФормула выравнивания фона:")
    player.message("§f  translation_x = -scale_x / 80")
    player.message("§7")
    player.message("§aПримеры:")
    player.message("§7• scale: [8, 4, 1] → translation: [-0.1, 0, 0]")
    player.message("§7• scale: [6, 3, 1] → translation: [-0.075, 0, 0]")
    player.message("§7• scale: [2, 1, 1] → translation: [-0.025, 0, 0]")
    player.message("§7")
    player.message("§bЭта формула идеально выравнивает фон!")
end

function on_close()
    log.info("=== Magic Number Test closed ===")
end