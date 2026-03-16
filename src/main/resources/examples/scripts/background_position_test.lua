-- Background Position Test Script
-- Демонстрирует параметр position для фона

function on_open()
    log.info("=== Background Position Test opened ===")
    log.info("Фон смещен через position: [-0.5, 0.3, 0]")
    log.info("Это отличается от translation тем, что position влияет на базовую позицию")
    
    player.message("§bBackground Position Test")
    player.message("§7Основной фон смещен влево и вверх")
    player.message("§7• §aЗеленая кнопка: центр экрана (0, 0, 0)")
    player.message("§7• §bАлмазная кнопка: позиция фона (-0.5, 0.3, 0)")
    player.message("§7• §6Оранжевый блок: дополнительный фон-виджет")
    player.message("§7")
    player.message("§eРазница между position и translation:")
    player.message("§7• position: базовое смещение от центра экрана")
    player.message("§7• translation: точная настройка трансформации")
end

function on_close()
    log.info("=== Background Position Test closed ===")
end