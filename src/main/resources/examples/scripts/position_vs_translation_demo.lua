-- Position vs Translation Demo Script
-- Демонстрирует разницу между position и translation

function on_open()
    log.info("=== Position vs Translation Demo opened ===")
    log.info("Демонстрация разницы между position и translation параметрами")
    
    player.message("§6Position vs Translation Demo")
    player.message("§7")
    player.message("§e• §6Золотой блок: §7position: [-1.0, 0.5, 0.0]")
    player.message("§e• §bАлмазный блок: §7translation: [1.0, -0.5, 0.0]")
    player.message("§e• §aИзумрудный блок: §7position + translation")
    player.message("§e• §cКрасный блок: §7центр экрана (0, 0, 0)")
    player.message("§7")
    player.message("§fРазница:")
    player.message("§7• position: базовое смещение от центра экрана")
    player.message("§7• translation: точная настройка на уровне матрицы")
    player.message("§7• Можно комбинировать для максимальной точности")
end

function on_close()
    log.info("=== Position vs Translation Demo closed ===")
end