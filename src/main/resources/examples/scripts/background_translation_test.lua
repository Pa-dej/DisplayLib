-- Background Translation Test Script
-- Демонстрирует параметр translation для фона

function on_open()
    log.info("=== Background Translation Test opened ===")
    log.info("Фон смещен вверх на 0.5 единицы через translation: [0.0, 0.5, 0.0]")
    
    player.message("§5Background Translation Test")
    player.message("§7Фон экрана смещен вверх на 0.5 единицы")
    player.message("§7Виджеты остаются на своих позициях")
    player.message("§7Это демонстрирует независимое позиционирование фона и виджетов")
end

function on_close()
    log.info("=== Background Translation Test closed ===")
end