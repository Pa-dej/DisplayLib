-- Приватный экран - тест радиуса взаимодействия
-- Взаимодействие: 3 блока, Автозакрытие: 20 блоков

-- Счетчик кликов
local click_count = 0

function on_open()
    log.info("=== ТЕСТ ЭКРАН ОТКРЫТ ===")
    log.info("Игрок: " .. player.name())
    log.info("Настройки: взаимодействие 3 блока, автозакрытие 20 блоков")
    log.info("========================")
    
    -- Загружаем счетчик из storage
    click_count = storage.get("click_count", 0)
    update_counter_display()
end

function on_close()
    log.info("=== ТЕСТ ЭКРАН ЗАКРЫТ ===")
    log.info("Игрок: " .. player.name())
    log.info("Финальный счетчик: " .. click_count)
    log.info("========================")
    
    -- Сохраняем счетчик в storage
    storage.set("click_count", click_count)
end

-- Тест взаимодействия
function test_interaction()
    log.info(">>> ТЕСТ ВЗАИМОДЕЙСТВИЯ вызван игроком: " .. player.name())
    
    player.message("§a✓ Взаимодействие работает! Вы находитесь в радиусе 3 блоков.")
    player.sound("entity.experience_orb.pickup", 1.0, 1.2)
    
    -- Обновляем информацию
    local info_widget = screen.widget("settings_info")
    if info_widget then
        info_widget.text("§aВзаимодействие работает! §7(3 блока | 20 блоков)")
        
        -- Возвращаем обычный текст через 3 секунды
        timer.after(60, function()
            if info_widget then
                info_widget.text("Взаимодействие: 3 блока | Закрытие: 20 блоков")
            end
        end)
    end
end

-- Увеличить счетчик
function increment_counter()
    click_count = click_count + 1
    log.info(">>> СЧЕТЧИК УВЕЛИЧЕН до " .. click_count .. " игроком: " .. player.name())
    update_counter_display()
    
    player.message("§6Счетчик увеличен: §f" .. click_count)
    player.sound("block.note_block.harp", 1.0, 1.0 + (click_count * 0.1))
end

-- Сбросить счетчик
function reset_counter()
    local old_count = click_count
    click_count = 0
    log.info(">>> СЧЕТЧИК СБРОШЕН с " .. old_count .. " до 0 игроком: " .. player.name())
    update_counter_display()
    
    player.message("§cСчетчик сброшен!")
    player.sound("block.note_block.bass", 1.0, 0.8)
end

-- Воспроизвести звук
function play_sound()
    log.info(">>> ЗВУК воспроизведен игроком: " .. player.name())
    player.message("§d♪ Воспроизводится звук...")
    player.sound("block.note_block.pling", 1.0, 2.0)
    
    -- Играем мелодию
    timer.after(5, function()
        player.sound("block.note_block.pling", 1.0, 1.5)
    end)
    
    timer.after(10, function()
        player.sound("block.note_block.pling", 1.0, 1.8)
    end)
    
    timer.after(15, function()
        player.sound("block.note_block.pling", 1.0, 2.2)
    end)
end

-- Обновить отображение счетчика
function update_counter_display()
    local counter_widget = screen.widget("click_counter")
    if counter_widget then
        counter_widget.text("📊 Кликов: " .. click_count)
        
        -- Меняем цвет в зависимости от количества кликов
        if click_count == 0 then
            counter_widget.hoveredText("📊 Кликов: " .. click_count)
        elseif click_count < 5 then
            counter_widget.hoveredText("📊 Кликов: " .. click_count .. " §7(мало)")
        elseif click_count < 10 then
            counter_widget.hoveredText("📊 Кликов: " .. click_count .. " §e(средне)")
        else
            counter_widget.hoveredText("📊 Кликов: " .. click_count .. " §a(много!)")
        end
        
        log.info("Счетчик обновлен: " .. click_count)
    else
        log.warning("Виджет счетчика не найден!")
    end
end