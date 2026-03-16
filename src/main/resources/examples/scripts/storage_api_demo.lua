-- Storage API Demo Script
-- Демонстрация всех функций Storage API

function on_open()
    log.info("=== Storage API Demo opened ===")
    log.info("Storage API demo opened for " .. player.name())
    
    -- Приветственное сообщение
    player.message("§a📦 Добро пожаловать в демонстрацию Storage API!")
    player.message("§eИзучите функции для хранения данных:")
    player.message("§7- §aget() §7- получение данных")
    player.message("§7- §eset() §7- сохранение данных")
    player.message("§7- §ehas() §7- проверка существования")
    player.message("§7- §eremove() §7- удаление данных")
    player.message("§7- §eclear() §7- очистка всех данных")
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Обновляем счетчик и статистику
    update_counter()
    update_stats()
    
    log.info("=== Storage API Demo on_open completed ===")
end

function on_close()
    log.info("Storage API demo closed for " .. player.name())
    player.message("§7Storage API demo завершен!")
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
end

-- =============================================================================
-- Основной счетчик - демонстрация get() и set()
-- =============================================================================

function btn_increment_click()
    log.info("=== btn_increment_click called ===")
    
    -- Демонстрация storage.get() с дефолтным значением
    local counter = storage.get("counter", 0)
    counter = counter + 1
    
    -- Демонстрация storage.set()
    storage.set("counter", tostring(counter))
    
    player.message("§a+1 Счетчик увеличен до: " .. counter)
    player.sound("block.note_block.harp", 1.0, 1.0 + counter * 0.05)
    
    -- Особые события
    if counter == 10 then
        player.message("§6🎉 Достигли 10! Сохраняем достижение...")
        storage.set("achievement_10", "true")
    elseif counter == 25 then
        player.message("§5✨ Четверть сотни! Обновляем рекорд...")
        storage.set("best_score", tostring(counter))
    end
    
    update_counter()
    update_stats()
    
    log.info("Counter incremented to: " .. counter)
    log.info("=== btn_increment_click completed ===")
end

function btn_decrement_click()
    log.info("=== btn_decrement_click called ===")
    
    local counter = storage.get("counter", 0)
    
    if counter > 0 then
        counter = counter - 1
        storage.set("counter", tostring(counter))
        
        player.message("§c-1 Счетчик уменьшен до: " .. counter)
        player.sound("block.note_block.bass", 1.0, 0.8)
        
        -- Удаляем достижения при откате
        if counter < 10 and storage.has("achievement_10") then
            storage.remove("achievement_10")
            player.message("§7Достижение '10' удалено")
        end
        
        log.info("Counter decremented to: " .. counter)
    else
        player.message("§cСчетчик уже равен нулю!")
        player.sound("block.anvil.hit", 0.5, 0.5)
    end
    
    update_counter()
    update_stats()
    
    log.info("=== btn_decrement_click completed ===")
end

function btn_reset_click()
    log.info("=== btn_reset_click called ===")
    
    -- Демонстрация storage.remove()
    if storage.has("counter") then
        storage.remove("counter")
        player.message("§7🗑️ Счетчик удален из хранилища")
        player.sound("entity.item.break", 1.0, 1.0)
    else
        player.message("§7Счетчик уже отсутствует в хранилище")
    end
    
    update_counter()
    update_stats()
    
    log.info("Counter removed from storage")
    log.info("=== btn_reset_click completed ===")
end

function update_counter()
    local counter = storage.get("counter", 0)
    local label = screen.widget("counter_label")
    
    if label then
        local counter_text = "Счетчик: " .. counter
        label.text(counter_text)
        label.hoveredText("storage.get('counter', '0') = " .. counter)
        
        -- Меняем цвет в зависимости от значения
        if counter == 0 then
            label.bgColor(30, 60, 30)  -- зеленый
        elseif counter < 10 then
            label.bgColor(60, 60, 30)  -- желтый
        elseif counter < 25 then
            label.bgColor(60, 30, 60)  -- фиолетовый
        else
            label.bgColor(60, 30, 30)  -- красный
        end
    end
end

-- =============================================================================
-- Демонстрация has() - проверка существования ключей
-- =============================================================================

function btn_check_key_click()
    log.info("=== btn_check_key_click called ===")
    
    -- Демонстрация storage.has() для разных ключей
    local keys_to_check = {"counter", "achievement_10", "best_score", "demo_string", "demo_number", "demo_boolean"}
    
    player.message("§b🔍 Проверка ключей с помощью storage.has():")
    
    for _, key in ipairs(keys_to_check) do
        local exists = storage.has(key)
        local status = exists and "§a✓" or "§c✗"
        player.message("§7- " .. status .. " " .. key .. ": " .. tostring(exists))
    end
    
    player.sound("block.note_block.chime", 1.0, 1.0)
    
    log.info("Key existence check completed")
    log.info("=== btn_check_key_click completed ===")
end

function btn_create_key_click()
    log.info("=== btn_create_key_click called ===")
    
    -- Создаем тестовый ключ
    local random_id = math.random(1000, 9999)
    local test_key = "test_key_" .. random_id
    local test_value = "created_at_" .. random_id
    
    -- Демонстрация storage.set()
    storage.set(test_key, test_value)
    
    player.message("§a📝 Создан новый ключ:")
    player.message("§7- Ключ: " .. test_key)
    player.message("§7- Значение: " .. test_value)
    
    -- Проверяем что ключ создался
    if storage.has(test_key) then
        player.message("§a✓ storage.has() подтверждает создание")
    end
    
    player.sound("block.note_block.pling", 1.0, 1.5)
    update_stats()
    
    log.info("Test key created: " .. test_key .. " = " .. test_value)
    log.info("=== btn_create_key_click completed ===")
end

-- =============================================================================
-- Демонстрация разных типов данных
-- =============================================================================

function btn_save_string_click()
    log.info("=== btn_save_string_click called ===")
    
    -- Демонстрация сохранения строки
    local random_id = math.random(1000, 9999)
    local string_value = "Hello World " .. random_id
    storage.set("demo_string", string_value)
    
    player.message("§e📄 Строка сохранена:")
    player.message("§7storage.set('demo_string', '" .. string_value .. "')")
    player.sound("block.note_block.harp", 1.0, 1.0)
    
    update_stats()
    
    log.info("String saved: " .. string_value)
    log.info("=== btn_save_string_click completed ===")
end

function btn_save_number_click()
    log.info("=== btn_save_number_click called ===")
    
    -- Демонстрация сохранения числа
    local number_value = math.random(1, 1000)
    storage.set("demo_number", tostring(number_value))
    
    player.message("§e🔢 Число сохранено:")
    player.message("§7storage.set('demo_number', '" .. number_value .. "')")
    player.sound("block.note_block.bass", 1.0, 1.2)
    
    update_stats()
    
    log.info("Number saved: " .. number_value)
    log.info("=== btn_save_number_click completed ===")
end

function btn_save_boolean_click()
    log.info("=== btn_save_boolean_click called ===")
    
    -- Демонстрация сохранения boolean
    local boolean_value = (math.random(1, 2) == 1)
    storage.set("demo_boolean", tostring(boolean_value))
    
    player.message("§e⚡ Boolean сохранен:")
    player.message("§7storage.set('demo_boolean', '" .. tostring(boolean_value) .. "')")
    player.sound("block.note_block.pling", 1.0, 1.8)
    
    update_stats()
    
    log.info("Boolean saved: " .. tostring(boolean_value))
    log.info("=== btn_save_boolean_click completed ===")
end

function btn_load_data_click()
    log.info("=== btn_load_data_click called ===")
    
    -- Демонстрация storage.get() для всех типов данных
    player.message("§b📥 Загрузка всех сохраненных данных:")
    
    -- Строка
    local demo_string = storage.get("demo_string", "не найдено")
    player.message("§7- demo_string: " .. demo_string)
    
    -- Число
    local demo_number = storage.get("demo_number", "0")
    player.message("§7- demo_number: " .. demo_number)
    
    -- Boolean
    local demo_boolean = storage.get("demo_boolean", "false")
    player.message("§7- demo_boolean: " .. demo_boolean)
    
    -- Счетчик
    local counter = storage.get("counter", "0")
    player.message("§7- counter: " .. counter)
    
    player.sound("block.chest.open", 1.0, 1.0)
    
    log.info("All data loaded and displayed")
    log.info("=== btn_load_data_click completed ===")
end

-- =============================================================================
-- Статистика и очистка
-- =============================================================================

function update_stats()
    local stats_widget = screen.widget("stats_label")
    if stats_widget then
        -- Подсчитываем количество ключей (приблизительно)
        local key_count = 0
        local test_keys = {"counter", "achievement_10", "best_score", "demo_string", "demo_number", "demo_boolean"}
        
        for _, key in ipairs(test_keys) do
            if storage.has(key) then
                key_count = key_count + 1
            end
        end
        
        -- Добавляем динамические ключи (приблизительная оценка)
        -- Убираем проверку динамических ключей, так как os.time() недоступен
        
        stats_widget.text("Статистика: ~" .. key_count .. " ключей")
        stats_widget.hoveredText("Приблизительное количество сохраненных ключей")
        
        -- Меняем цвет в зависимости от количества
        if key_count == 0 then
            stats_widget.bgColor(20, 40, 20)
        elseif key_count < 3 then
            stats_widget.bgColor(40, 40, 20)
        else
            stats_widget.bgColor(40, 20, 20)
        end
    end
end

function btn_clear_all_click()
    log.info("=== btn_clear_all_click called ===")
    
    -- Демонстрация storage.clear()
    player.message("§c💥 Внимание! Очистка всех данных...")
    player.sound("entity.generic.explode", 0.5, 0.5)
    
    -- Задержка для драматического эффекта
    timer.after(20, function()
        storage.clear()
        
        player.message("§c🗑️ Все данные удалены с помощью storage.clear()")
        player.sound("entity.item.break", 1.0, 0.5)
        
        -- Обновляем интерфейс
        update_counter()
        update_stats()
        
        log.info("All storage data cleared")
    end)
    
    log.info("Storage clear initiated")
    log.info("=== btn_clear_all_click completed ===")
end