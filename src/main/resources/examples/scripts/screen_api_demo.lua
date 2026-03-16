-- Screen API Demo Script
-- Демонстрация всех функций Screen API

function on_open()
    log.info("=== Screen API Demo opened ===")
    log.info("Screen API demo opened for " .. player.name())
    
    -- Приветственное сообщение
    player.message("§b🖥️ Добро пожаловать в демонстрацию Screen API!")
    player.message("§eИзучите функции для управления экранами:")
    player.message("§7- §eid() §7- получение ID экрана")
    player.message("§7- §edata() §7- persistent данные экрана")
    player.message("§7- §ewidget() §7- получение виджетов")
    player.message("§7- §eswitch() §7- переключение экранов")
    player.message("§7- §eclose() §7- закрытие экрана")
    player.sound("ui.button.click", 1.0, 1.0)
    
    -- Инициализируем счетчик открытий
    local current_count = screen.data("open_count") or 0
    local open_count = current_count + 1
    screen.data("open_count", open_count)
    
    -- Обновляем отображение
    update_screen_info()
    update_data_display()
    
    log.info("=== Screen API Demo on_open completed ===")
end

function on_close()
    log.info("Screen API demo closed for " .. player.name())
    
    -- Сохраняем время закрытия (используем случайное число вместо времени)
    screen.data("last_close_time", math.random(1000, 9999))
    
    player.message("§7Screen API demo завершен!")
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
end
-- =============================================================================
-- Информация об экране - screen.id()
-- =============================================================================

function btn_get_screen_info_click()
    log.info("=== btn_get_screen_info_click called ===")
    
    -- Демонстрация screen.id()
    local screen_id = screen.id()
    
    player.message("§b📋 Информация об экране:")
    player.message("§7- §escreen.id(): §f" .. screen_id)
    
    -- Дополнительная информация из данных экрана
    local open_count = screen.data("open_count") or 0
    local last_close = screen.data("last_close_time") or "никогда"
    
    player.message("§7- Открытий: §f" .. tostring(open_count))
    player.message("§7- Последнее закрытие: §f" .. tostring(last_close))
    
    player.sound("block.note_block.chime", 1.0, 1.0)
    
    update_screen_info()
    
    log.info("Screen info displayed: ID=" .. screen_id .. ", opens=" .. open_count)
    log.info("=== btn_get_screen_info_click completed ===")
end

function update_screen_info()
    local display_widget = screen.widget("screen_id_display")
    if display_widget then
        local screen_id = screen.id()
        local open_count = screen.data("open_count") or 0
        
        display_widget.text("ID: " .. screen_id .. " (" .. open_count .. ")")
        display_widget.hoveredText("Экран: " .. screen_id .. ", открытий: " .. open_count)
    end
end

-- =============================================================================
-- Управление данными экрана - screen.data()
-- =============================================================================

function btn_save_data_click()
    log.info("=== btn_save_data_click called ===")
    
    -- Демонстрация screen.data() setter
    local timestamp = math.random(1000, 9999)
    screen.data("demo_string", "Сохранено в " .. timestamp);
    screen.data("demo_number", math.random(1, 100));
    screen.data("demo_boolean", (timestamp % 2 == 0));
    local current_save_count = screen.data("save_count") or 0
    screen.data("save_count", current_save_count + 1)
    
    player.message("§b💾 Данные сохранены в screen.data():")
    player.message("§7- demo_string: сохранено")
    player.message("§7- demo_number: сохранено")
    player.message("§7- demo_boolean: сохранено")
    player.sound("block.note_block.pling", 1.0, 1.2)
    
    update_data_display()
    
    log.info("Screen data saved")
    log.info("=== btn_save_data_click completed ===")
end

function btn_load_data_click()
    log.info("=== btn_load_data_click called ===")
    
    -- Демонстрация screen.data() getter
    local demo_string = screen.data("demo_string") or "не найдено"
    local demo_number = screen.data("demo_number") or 0
    local demo_boolean = screen.data("demo_boolean") or false
    local save_count = screen.data("save_count") or 0
    
    player.message("§b📥 Загрузка данных из screen.data():")
    player.message("§7- demo_string: §f" .. tostring(demo_string))
    player.message("§7- demo_number: §f" .. tostring(demo_number))
    player.message("§7- demo_boolean: §f" .. tostring(demo_boolean))
    player.message("§7- save_count: §f" .. tostring(save_count))
    
    player.sound("block.chest.open", 1.0, 1.0)
    
    log.info("Screen data loaded and displayed")
    log.info("=== btn_load_data_click completed ===")
end

function btn_clear_data_click()
    log.info("=== btn_clear_data_click called ===")
    
    -- Демонстрация screen.data() с nil для удаления
    screen.data("demo_string", nil);
    screen.data("demo_number", nil);
    screen.data("demo_boolean", nil)
    
    player.message("§c🗑️ Демонстрационные данные очищены")
    player.message("§7Использовано screen.data(key, nil)")
    player.sound("entity.item.break", 1.0, 1.0)
    
    update_data_display()
    
    log.info("Demo screen data cleared")
    log.info("=== btn_clear_data_click completed ===")
end

function update_data_display()
    local display_widget = screen.widget("data_display")
    if display_widget then
        local save_count = screen.data("save_count") or 0
        local open_count = screen.data("open_count") or 0
        
        if save_count > 0 then
            display_widget.text("Сохранений: " .. save_count)
            display_widget.hoveredText("Данные сохранены " .. save_count .. " раз")
            display_widget.bgColor(30, 50, 30)
        else
            display_widget.text("Данных нет")
            display_widget.hoveredText("Нет сохраненных данных")
            display_widget.bgColor(25, 25, 50)
        end
    end
end
-- =============================================================================
-- Управление виджетами - screen.widget()
-- =============================================================================

function btn_count_widgets_click()
    log.info("=== btn_count_widgets_click called ===")
    
    -- Демонстрация screen.widget() для подсчета виджетов
    local widget_names = {
        "title", "screen_info_label", "screen_id_display", "data_demo_label",
        "data_display", "widget_demo_label", "widget_count_display", 
        "navigation_label", "btn_get_screen_info", "btn_save_data", 
        "btn_load_data", "btn_clear_data", "btn_count_widgets",
        "btn_modify_widgets", "btn_widget_demo", "btn_switch_player",
        "btn_switch_storage", "btn_switch_widget", "btn_back", "btn_close"
    }
    
    local found_count = 0
    local missing_widgets = {}
    
    for _, name in ipairs(widget_names) do
        local widget = screen.widget(name)
        if widget then
            found_count = found_count + 1
        else
            table.insert(missing_widgets, name)
        end
    end
    
    player.message("§b🔍 Подсчет виджетов с помощью screen.widget():")
    player.message("§7- Найдено: §a" .. found_count .. "§7/" .. #widget_names)
    
    if #missing_widgets > 0 then
        player.message("§7- Не найдено: §c" .. #missing_widgets)
    end
    
    player.sound("block.note_block.harp", 1.0, 1.0)
    
    -- Обновляем отображение
    local count_widget = screen.widget("widget_count_display")
    if count_widget then
        count_widget.text("Виджетов: " .. found_count)
        count_widget.hoveredText("Найдено " .. found_count .. " из " .. #widget_names .. " виджетов")
        
        if found_count == #widget_names then
            count_widget.bgColor(30, 50, 30)
        else
            count_widget.bgColor(50, 30, 30)
        end
    end
    
    log.info("Widget count: " .. found_count .. "/" .. #widget_names)
    log.info("=== btn_count_widgets_click completed ===")
end

function btn_modify_widgets_click()
    log.info("=== btn_modify_widgets_click called ===")
    
    player.message("§b🎨 Демонстрация изменения виджетов через screen.widget():")
    
    -- Изменяем заголовок
    local title_widget = screen.widget("title")
    if title_widget then
        title_widget.text("🖥️ Screen API - Модификация!")
        title_widget.bgColor(60, 60, 100)
        player.message("§7- Заголовок изменен ✓")
    end
    
    -- Изменяем информационные виджеты
    local info_widget = screen.widget("screen_info_label")
    if info_widget then
        info_widget.bgColor(50, 30, 80)
        player.message("§7- Цвет info_label изменен ✓")
    end
    
    -- Возвращаем через 3 секунды
    timer.after(60, function()
        if title_widget then
            title_widget.text("🖥️ Screen API Demo")
            title_widget.bgColor(40, 40, 80)
        end
        if info_widget then
            info_widget.bgColor(30, 30, 60)
        end
        
        player.message("§7🔄 Виджеты восстановлены")
        player.sound("entity.experience_orb.pickup", 1.0, 1.0)
    end)
    
    player.sound("block.enchantment_table.use", 1.0, 1.0)
    
    log.info("Widgets modified via screen.widget()")
    log.info("=== btn_modify_widgets_click completed ===")
end

function btn_widget_demo_click()
    log.info("=== btn_widget_demo_click called ===")
    
    player.message("§b🎭 Комплексная демонстрация работы с виджетами:")
    
    -- Получаем несколько виджетов
    local widgets_to_animate = {
        {name = "data_display", original_color = {25, 25, 50}},
        {name = "widget_count_display", original_color = {25, 25, 50}},
        {name = "screen_id_display", original_color = {25, 25, 50}}
    }
    
    -- Анимация виджетов
    timer.times(6, 8, function(i)
        local colors = {
            {60, 30, 30}, {30, 60, 30}, {30, 30, 60},
            {60, 60, 30}, {60, 30, 60}, {30, 60, 60}
        }
        
        local color = colors[i]
        
        for _, widget_info in ipairs(widgets_to_animate) do
            local widget = screen.widget(widget_info.name)
            if widget then
                widget.bgColor(color[1], color[2], color[3])
            end
        end
        
        if i == 6 then
            player.message("§a✨ Анимация завершена!")
            
            -- Восстанавливаем цвета
            timer.after(20, function()
                for _, widget_info in ipairs(widgets_to_animate) do
                    local widget = screen.widget(widget_info.name)
                    if widget then
                        local orig = widget_info.original_color
                        widget.bgColor(orig[1], orig[2], orig[3])
                    end
                end
                
                player.message("§7Цвета восстановлены")
            end)
        end
    end)
    
    player.sound("entity.firework_rocket.launch", 1.0, 1.0)
    
    log.info("Widget animation demo started")
    log.info("=== btn_widget_demo_click completed ===")
end

-- =============================================================================
-- Навигация между экранами - screen.switch()
-- =============================================================================

function btn_switch_player_click()
    log.info("=== btn_switch_player_click called ===")
    
    player.message("§b🔄 Демонстрация screen.switch('player_api_demo')")
    player.sound("entity.enderman.teleport", 1.0, 1.0)
    
    -- Сохраняем данные перед переключением
    screen.data("switched_from", "screen_api_demo");
    screen.data("switch_time", math.random(1000, 9999))
    
    -- Демонстрация screen.switch()
    timer.after(20, function()  -- небольшая задержка для эффекта
        screen.switch("player_api_demo")
    end)
    
    log.info("Switching to player_api_demo")
    log.info("=== btn_switch_player_click completed ===")
end

function btn_switch_storage_click()
    log.info("=== btn_switch_storage_click called ===")
    
    player.message("§b🔄 Демонстрация screen.switch('storage_api_demo')")
    player.sound("entity.enderman.teleport", 1.0, 1.0)
    
    screen.data("switched_from", "screen_api_demo");
    screen.data("switch_time", math.random(1000, 9999))
    
    timer.after(20, function()
        screen.switch("storage_api_demo")
    end)
    
    log.info("Switching to storage_api_demo")
    log.info("=== btn_switch_storage_click completed ===")
end

function btn_switch_widget_click()
    log.info("=== btn_switch_widget_click called ===")
    
    player.message("§b🔄 Демонстрация screen.switch('widget_api_demo')")
    player.sound("entity.enderman.teleport", 1.0, 1.0)
    
    screen.data("switched_from", "screen_api_demo");
    screen.data("switch_time", math.random(1000, 9999))
    
    timer.after(20, function()
        screen.switch("widget_api_demo")
    end)
    
    log.info("Switching to widget_api_demo")
    log.info("=== btn_switch_widget_click completed ===")
end