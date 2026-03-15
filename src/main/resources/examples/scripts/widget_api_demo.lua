-- Widget API Demo Script
-- Демонстрация всех функций Widget API

local text_index = 1
local color_index = 1
local animation_timer = nil

function on_open()
    log.info("=== Widget API Demo opened ===")
    log.info("Widget API demo opened for " .. player.name())
    
    -- Приветственное сообщение
    player.message("§d🎛️ Добро пожаловать в демонстрацию Widget API!")
    player.message("§eИзучите функции для управления виджетами:")
    player.message("§7- §etext() §7- изменение текста")
    player.message("§7- §ehoveredText() §7- текст при наведении")
    player.message("§7- §evisible() §7- видимость виджета")
    player.message("§7- §eenabled() §7- активность виджета")
    player.message("§7- §ebgColor() §7- цвет фона")
    player.message("§7- §ebgAlpha() §7- прозрачность фона")
    player.message("§7- §etooltip() §7- подсказка виджета")
    player.sound("ui.button.click", 1.0, 1.0)
    
    log.info("=== Widget API Demo on_open completed ===")
end

function on_close()
    log.info("Widget API demo closed for " .. player.name())
    
    -- Отменяем анимацию если активна
    if animation_timer then
        timer.cancel(animation_timer)
        animation_timer = nil
    end
    
    player.message("§7Widget API demo завершен!")
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
end

-- =============================================================================
-- Управление текстом - text() и hoveredText()
-- =============================================================================

function btn_change_text_click()
    log.info("=== btn_change_text_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        text_index = text_index + 1
        if text_index > 6 then
            text_index = 1
        end
        
        local texts = {
            "Демонстрационный текст",
            "Новый текст!",
            "Widget API работает!",
            "Изменяемый контент",
            "Динамический текст",
            "Lua + DisplayLib"
        }
        
        local new_text = texts[text_index]
        
        -- Демонстрация widget.text()
        demo_text.text(new_text)
        
        player.message("§d📝 Текст изменен: " .. new_text)
        player.message("§7Использована функция widget.text()")
        player.sound("block.note_block.chime", 1.0, 1.0 + text_index * 0.1)
        
        log.info("Text changed to: " .. new_text .. " (index: " .. text_index .. ")")
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_change_text_click completed ===")
end

function btn_change_hover_click()
    log.info("=== btn_change_hover_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        local hover_texts = {
            "Наведите мышь для подсказки",
            "Это hover текст!",
            "hoveredText() в действии",
            "Подсказка изменена",
            "Widget API демонстрация",
            "Интерактивный элемент"
        }
        
        local hover_index = math.random(1, #hover_texts)
        local new_hover = hover_texts[hover_index]
        
        -- Демонстрация widget.hoveredText()
        demo_text.hoveredText(new_hover)
        
        player.message("§d💭 Hover текст изменен: " .. new_hover)
        player.message("§7Использована функция widget.hoveredText()")
        player.sound("block.note_block.pling", 1.0, 1.5)
        
        log.info("Hover text changed to: " .. new_hover)
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_change_hover_click completed ===")
end

-- =============================================================================
-- Управление видимостью - visible() и enabled()
-- =============================================================================

function btn_toggle_visibility_click()
    log.info("=== btn_toggle_visibility_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        -- Демонстрация widget.visible() getter
        local visible = demo_text.visible()
        log.info("Current visibility: " .. tostring(visible))
        
        -- Демонстрация widget.visible() setter
        demo_text.visible(not visible)
        
        -- Проверяем что изменение применилось
        local new_visible = demo_text.visible()
        log.info("New visibility: " .. tostring(new_visible))
        
        if visible then
            player.message("§7👻 Текст скрыт с помощью widget.visible(false)")
            player.sound("entity.enderman.teleport", 0.5, 0.5)
        else
            player.message("§a✨ Текст показан с помощью widget.visible(true)")
            player.sound("entity.enderman.teleport", 0.5, 1.5)
        end
        
        log.info("Text visibility toggled: " .. tostring(not visible))
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_toggle_visibility_click completed ===")
end

function btn_toggle_enabled_click()
    log.info("=== btn_toggle_enabled_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        -- Демонстрация widget.enabled() getter
        local enabled = demo_text.enabled()
        log.info("Current enabled state: " .. tostring(enabled))
        
        -- Демонстрация widget.enabled() setter
        demo_text.enabled(not enabled)
        
        local new_enabled = demo_text.enabled()
        log.info("New enabled state: " .. tostring(new_enabled))
        
        if enabled then
            player.message("§c🔒 Виджет отключен с помощью widget.enabled(false)")
            player.message("§7Виджет не будет реагировать на клики")
            player.sound("block.iron_door.close", 1.0, 1.0)
        else
            player.message("§a🔓 Виджет включен с помощью widget.enabled(true)")
            player.message("§7Виджет снова активен")
            player.sound("block.iron_door.open", 1.0, 1.0)
        end
        
        log.info("Widget enabled state toggled: " .. tostring(not enabled))
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_toggle_enabled_click completed ===")
end

-- =============================================================================
-- Управление цветом - bgColor() и bgAlpha()
-- =============================================================================

function btn_change_color_click()
    log.info("=== btn_change_color_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        color_index = color_index + 1
        if color_index > 6 then
            color_index = 1
        end
        
        local colors = {
            {60, 30, 60, "Фиолетовый"},   -- фиолетовый
            {80, 40, 40, "Красный"},      -- красный
            {40, 80, 40, "Зеленый"},      -- зеленый
            {40, 40, 80, "Синий"},        -- синий
            {80, 80, 40, "Желтый"},       -- желтый
            {80, 40, 80, "Розовый"}       -- розовый
        }
        
        local color = colors[color_index]
        
        -- Демонстрация widget.bgColor()
        demo_text.bgColor(color[1], color[2], color[3])
        
        player.message("§d🎨 Цвет фона изменен: " .. color[4])
        player.message("§7widget.bgColor(" .. color[1] .. ", " .. color[2] .. ", " .. color[3] .. ")")
        player.sound("block.note_block.harp", 1.0, 1.0 + color_index * 0.1)
        
        log.info("Background color changed to: " .. color[1] .. ", " .. color[2] .. ", " .. color[3])
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_change_color_click completed ===")
end

function btn_change_alpha_click()
    log.info("=== btn_change_alpha_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        local alphas = {50, 100, 150, 200, 255}
        local alpha_names = {"Очень прозрачный", "Прозрачный", "Полупрозрачный", "Почти непрозрачный", "Непрозрачный"}
        
        local alpha_index = math.random(1, #alphas)
        local alpha_value = alphas[alpha_index]
        local alpha_name = alpha_names[alpha_index]
        
        -- Демонстрация widget.bgAlpha()
        demo_text.bgAlpha(alpha_value)
        
        player.message("§d🔍 Прозрачность изменена: " .. alpha_name)
        player.message("§7widget.bgAlpha(" .. alpha_value .. ")")
        player.sound("block.glass.break", 0.5, 1.0 + alpha_value / 255)
        
        log.info("Background alpha changed to: " .. alpha_value)
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_change_alpha_click completed ===")
end

-- =============================================================================
-- Управление tooltip - tooltip()
-- =============================================================================

function btn_change_tooltip_click()
    log.info("=== btn_change_tooltip_click called ===")
    
    local tooltips = {
        "Новая подсказка!",
        "widget.tooltip() работает",
        "Динамический tooltip",
        "Изменяемая подсказка",
        "Интерактивный элемент",
        "Tooltip обновлен"
    }
    
    local tooltip_index = math.random(1, #tooltips)
    local new_tooltip = tooltips[tooltip_index]
    
    -- Демонстрация widget.tooltip() на самой кнопке
    local button = screen.widget("btn_change_tooltip")
    if button then
        button.tooltip(new_tooltip)
        
        player.message("§d🏷️ Tooltip кнопки изменен: " .. new_tooltip)
        player.message("§7Использована функция widget.tooltip()")
        player.sound("block.note_block.bell", 1.0, 1.2)
        
        log.info("Button tooltip changed to: " .. new_tooltip)
    else
        log.error("btn_change_tooltip widget not found!")
    end
    
    log.info("=== btn_change_tooltip_click completed ===")
end

-- =============================================================================
-- Анимации - комбинированные эффекты
-- =============================================================================

function btn_animate_color_click()
    log.info("=== btn_animate_color_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        player.message("§d🌈 Запуск цветовой анимации...")
        player.sound("block.beacon.activate", 1.0, 1.0)
        
        -- Цветовая анимация с использованием Timer API
        local colors = {
            {100, 50, 50}, -- красный
            {50, 100, 50}, -- зеленый
            {50, 50, 100}, -- синий
            {100, 100, 50}, -- желтый
            {100, 50, 100}, -- фиолетовый
            {50, 100, 100}  -- циан
        }
        
        local step = 1
        animation_timer = timer["repeat"](10, function()  -- каждые 0.5 секунды
            local color = colors[step]
            demo_text.bgColor(color[1], color[2], color[3])
            
            step = step + 1
            if step > #colors then
                step = 1
            end
            
            -- Останавливаем через 30 итераций (15 секунд)
            if step == 1 then  -- каждый полный цикл
                local cycles = (step == 1 and 1 or 0)
                if cycles >= 5 then  -- 5 полных циклов
                    timer.cancel(animation_timer)
                    animation_timer = nil
                    player.message("§d✨ Цветовая анимация завершена")
                end
            end
        end)
        
        log.info("Color animation started")
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_animate_color_click completed ===")
end

function btn_animate_fade_click()
    log.info("=== btn_animate_fade_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        player.message("§d👻 Запуск анимации исчезновения...")
        player.sound("entity.enderman.ambient", 1.0, 1.0)
        
        -- Анимация исчезновения
        demo_text.bgAlpha(255)
        
        timer.times(5, 10, function(i)  -- 5 шагов по 0.5 секунды
            local alpha = 255 - (i * 50)
            demo_text.bgAlpha(alpha)
            
            if i == 5 then
                -- Анимация появления
                timer.after(20, function()  -- пауза 1 секунда
                    player.message("§d✨ Анимация появления...")
                    
                    timer.times(5, 10, function(j)
                        local alpha = j * 50
                        demo_text.bgAlpha(alpha)
                        
                        if j == 5 then
                            player.message("§d🎉 Анимация завершена!")
                            player.sound("entity.experience_orb.pickup", 1.0, 1.5)
                        end
                    end)
                end)
            end
        end)
        
        log.info("Fade animation started")
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_animate_fade_click completed ===")
end

-- =============================================================================
-- Комплексная демонстрация
-- =============================================================================

function btn_complex_demo_click()
    log.info("=== btn_complex_demo_click called ===")
    
    local demo_text = screen.widget("demo_text")
    if demo_text then
        player.message("§d🧪 Запуск комплексной демонстрации Widget API...")
        player.sound("block.beacon.power_select", 1.0, 1.0)
        
        -- Этап 1: Изменение текста
        demo_text.text("Комплексная демонстрация")
        demo_text.hoveredText("Демонстрация всех функций Widget API")
        
        timer.after(20, function()  -- 1 сек
            player.message("§71. text() и hoveredText() ✓")
            
            -- Этап 2: Изменение цвета
            demo_text.bgColor(100, 50, 50)
            demo_text.bgAlpha(200)
            
            timer.after(20, function()  -- 2 сек
                player.message("§72. bgColor() и bgAlpha() ✓")
                
                -- Этап 3: Скрытие и показ
                demo_text.visible(false)
                
                timer.after(20, function()  -- 3 сек
                    player.message("§73. visible(false) ✓")
                    demo_text.visible(true)
                    
                    timer.after(20, function()  -- 4 сек
                        player.message("§74. visible(true) ✓")
                        
                        -- Этап 4: Отключение и включение
                        demo_text.enabled(false)
                        
                        timer.after(20, function()  -- 5 сек
                            player.message("§75. enabled(false) ✓")
                            demo_text.enabled(true)
                            
                            timer.after(20, function()  -- 6 сек
                                player.message("§76. enabled(true) ✓")
                                
                                -- Финал
                                demo_text.text("Демонстрация завершена!")
                                demo_text.bgColor(50, 100, 50)
                                player.message("§a🎉 Комплексная демонстрация завершена успешно!")
                                player.sound("entity.player.levelup", 1.0, 1.0)
                                
                                log.info("Complex demo completed successfully")
                            end)
                        end)
                    end)
                end)
            end)
        end)
        
        log.info("Complex demo started")
    else
        log.error("demo_text widget not found!")
    end
    
    log.info("=== btn_complex_demo_click completed ===")
end