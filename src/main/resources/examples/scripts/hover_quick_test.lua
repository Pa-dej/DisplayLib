-- Быстрый тест hover анимаций
-- Простые реакции для тестирования

function on_open()
    log.info("Быстрый тест hover анимаций открыт для " .. player.name())
    player.message("⚡ Быстрый тест анимаций")
    player.message("Наведите курсор на виджеты для тестирования")
    player.message("🆕 Новинка: Непрерывные пульсирующие анимации!")
end

function on_close()
    log.info("Быстрый тест закрыт")
end

function testClick()
    player.message("✅ Тест пройден!")
    player.sound("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0, 1.5)
    
    -- Увеличиваем счетчик тестов
    local tests = screen.data("test_count") or 0
    tests = tests + 1
    screen.data("test_count", tests)
    
    if tests == 1 then
        player.message("Первый тест! 🎉")
        player.message("💡 Попробуйте оранжевую кнопку 'Пульсация' - она анимируется непрерывно!")
    elseif tests == 3 then
        player.message("3 теста! Попробуйте алмаз - он тоже пульсирует! 💎")
    elseif tests == 5 then
        player.message("5 тестов пройдено! 🏆")
        player.message("Непрерывные анимации работают пока курсор наведен!")
    elseif tests == 10 then
        player.message("10 тестов! Вы эксперт по анимациям! 🌟")
    elseif tests % 5 == 0 then
        player.message("Тестов пройдено: " .. tests)
    end
end