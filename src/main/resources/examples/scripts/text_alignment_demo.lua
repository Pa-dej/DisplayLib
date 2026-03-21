-- Text Alignment Demo Script

function on_open()
    log.info("Text Alignment Demo opened")
    
    -- Показываем информацию о выравнивании текста
    player.message("§6Text Alignment Demo")
    player.message("§7Демонстрация различных вариантов выравнивания текста:")
    player.message("§c• LEFT §7- выравнивание по левому краю")
    player.message("§a• CENTERED §7- выравнивание по центру (по умолчанию)")
    player.message("§b• RIGHT §7- выравнивание по правому краю")
end

function on_close()
    log.info("Text Alignment Demo closed")
    player.message("§7Демо выравнивания текста закрыто")
end