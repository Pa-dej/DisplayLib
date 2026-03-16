-- Auto Alignment Test Script
-- Демонстрирует автоматическое выравнивание всех виджетов

function on_open()
    log.info("=== Auto Alignment Test opened ===")
    log.info("Все виджеты автоматически выравниваются по формуле: translation_x = -scale_x / 80")
    
    player.message("§6Автоматическое выравнивание")
    player.message("§7")
    player.message("§eВсе виджеты теперь выравниваются автоматически!")
    player.message("§7")
    player.message("§aФормула: §ftranslation_x = -scale_x / 80 + user_x")
    player.message("§7")
    player.message("§bПреимущества:")
    player.message("§7• Не нужно вычислять магические числа")
    player.message("§7• Все виджеты идеально выровнены")
    player.message("§7• Можно добавлять свои смещения")
    player.message("§7• Работает для TEXT и ITEM виджетов")
    player.message("§7• Работает для фона")
end

function on_close()
    log.info("=== Auto Alignment Test closed ===")
end