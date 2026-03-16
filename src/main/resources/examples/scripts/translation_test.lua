-- Translation Test Screen Script
-- Демонстрирует работу параметра translation в YAML

function on_open()
    log.info("=== Translation Test opened ===")
    log.info("Этот экран демонстрирует новый параметр 'translation' в YAML")
    log.info("Теперь смещение виджетов полностью контролируется через YAML")
    log.info("Автоматическое смещение -scaleY/8 убрано")
    
    player.message("§6Translation Test")
    player.message("§7Сравните позиционирование разных кнопок:")
    player.message("§a• Зеленая: без translation (0,0,0)")
    player.message("§e• Желтая: с отрицательным Y (-0.125)")
    player.message("§c• Красная: с положительным Y (+0.125)")
    player.message("§b• Синяя: текстовая кнопка без смещения")
end

function on_close()
    log.info("=== Translation Test closed ===")
end