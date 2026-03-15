-- Timer API Demo Script
-- Демонстрация всех функций Timer API

local repeat_timer = nil
local repeat_counter = 0

function on_open()
    log.info("=== Timer API Demo opened ===")
    log.info("Timer API demo opened for " .. player.name())
    
    -- Приветственное сообщение
    player.message("§e⏰ Добро пожаловать в демонстрацию Timer API!")
    player.message("§eИзучите функции для работы с таймерами:")
    player.message("§7- §eafter() §7- одноразовое выполнение с задержкой")
    player.message("§7- §erepeat() §7- циклическое выполнение")
    player.message("§7- §etimes() §7- ограниченное количество повторений")
    player.message("§7- §ecancel() §7- отмена активных таймеров")
    player.sound("ui.button.click", 1.0, 1.0)
    
    log.info("=== Timer API Demo on_open completed ===")
end

function on_close()
    log.info("Timer API demo closed for " .. player.name())
    
    -- Отменяем все активные таймеры
    if repeat_timer then
        timer.cancel(repeat_timer)
        repeat_timer = nil
    end
    
    player.message("§7Timer API demo завершен!")
    player.sound("entity.experience_orb.pickup", 1.0, 1.0)
end

-- =============================================================================
-- timer.after() - Одноразовые таймеры
-- =============================================================================

function btn_after_simple_click()
    log.info("=== btn_after_simple_click called ===")
    
    local status_widget = screen.widget("after_status")
    if status_widget then
        status_widget.text("Ожидание 3 секунды...")
        status_widget.bgColor(60, 60, 30)
    end
    
    player.message("§e⏰ Демонстрация timer.after(60) - задержка 3 секунды")
    player.sound("block.note_block.pling", 1.0, 1.0)
    
    -- Демонстрация timer.after()
    timer.after(60, function()  -- 3 секунды (60 тиков)
        local status_widget = screen.widget("after_status")
        if status_widget then
            status_widget.text("Готов к запуску")
            status_widget.bgColor(50, 50, 25)
        end
        
        player.message("§a✓ timer.after() выполнен через 3 секунды!")
        player.sound("entity.experience_orb.pickup", 1.0, 1.5)
        
        log.info("Simple after timer completed")
    end)
    
    log.info("Simple after timer started (3 seconds)")
    log.info("=== btn_after_simple_click completed ===")
end

function btn_after_countdown_click()
    log.info("=== btn_after_countdown_click called ===")
    
    local status_widget = screen.widget("after_status")
    
    player.message("§e🚀 Демонстрация множественных timer.after() - обратный отсчет")
    player.sound("block.beacon.activate", 1.0, 1.0)
    
    if status_widget then
        status_widget.text("Запуск через 5...")
        status_widget.bgColor(80, 60, 30)
    end
    
    -- Демонстрация множественных timer.after() для обратного отсчета
    timer.after(20, function()  -- 1 сек
        local status_widget = screen.widget("after_status")
        if status_widget then status_widget.text("Запуск через 4...") end
        player.message("§e4...")
        player.sound("block.note_block.pling", 1.0, 1.1)
    end)
    
    timer.after(40, function()  -- 2 сек
        local status_widget = screen.widget("after_status")
        if status_widget then status_widget.text("Запуск через 3...") end
        player.message("§e3...")
        player.sound("block.note_block.pling", 1.0, 1.2)
    end)
    
    timer.after(60, function()  -- 3 сек
        local status_widget = screen.widget("after_status")
        if status_widget then status_widget.text("Запуск через 2...") end
        player.message("§e2...")
        player.sound("block.note_block.pling", 1.0, 1.3)
    end)
    
    timer.after(80, function()  -- 4 сек
        local status_widget = screen.widget("after_status")
        if status_widget then status_widget.text("Запуск через 1...") end
        player.message("§e1...")
        player.sound("block.note_block.pling", 1.0, 1.4)
    end)
    
    timer.after(100, function()  -- 5 сек
        local status_widget = screen.widget("after_status")
        if status_widget then
            status_widget.text("Готов к запуску")
            status_widget.bgColor(50, 50, 25)
        end
        
        player.message("§a🎉 Обратный отсчет завершен!")
        player.sound("entity.firework_rocket.blast", 1.0, 1.0)
        
        log.info("Countdown completed")
    end)
    
    log.info("Countdown started (5 steps)")
    log.info("=== btn_after_countdown_click completed ===")
end

-- =============================================================================
-- timer.repeat() - Повторяющиеся таймеры
-- =============================================================================

function btn_repeat_start_click()
    log.info("=== btn_repeat_start_click called ===")
    
    if repeat_timer then
        player.message("§c⚠️ Повторяющийся таймер уже запущен!")
        player.sound("block.anvil.hit", 0.5, 0.5)
        return
    end
    
    local status_widget = screen.widget("repeat_status")
    repeat_counter = 0
    
    player.message("§e🔄 Демонстрация timer.repeat(20) - каждую секунду")
    player.sound("block.piston.extend", 1.0, 1.2)
    
    if status_widget then
        status_widget.text("Запущен: 0")
        status_widget.bgColor(30, 60, 30)
    end
    
    -- Демонстрация timer.repeat()
    repeat_timer = timer["repeat"](20, function()  -- каждые 20 тиков (1 секунда)
        repeat_counter = repeat_counter + 1
        
        local status_widget = screen.widget("repeat_status")
        if status_widget then
            status_widget.text("Запущен: " .. repeat_counter)
            
            -- Меняем цвет циклично
            local colors = {
                {30, 60, 30}, -- зеленый
                {60, 60, 30}, -- желтый
                {60, 30, 30}, -- красный
                {30, 30, 60}, -- синий
                {60, 30, 60}, -- фиолетовый
                {30, 60, 60}  -- циан
            }
            
            local color_index = ((repeat_counter - 1) % #colors) + 1
            local color = colors[color_index]
            if color and color[1] and color[2] and color[3] then
                status_widget.bgColor(color[1], color[2], color[3])
            else
                status_widget.bgColor(30, 60, 30) -- fallback color
            end
        end
        
        -- Звук каждые 5 итераций
        if repeat_counter % 5 == 0 then
            player.message("§a🔔 Повтор #" .. repeat_counter)
            player.sound("block.bell.use", 0.5, 1.0 + repeat_counter * 0.05)
        end
        
        log.info("Repeat timer iteration: " .. repeat_counter)
    end)
    
    log.info("Repeat timer started")
    log.info("=== btn_repeat_start_click completed ===")
end

function btn_repeat_stop_click()
    log.info("=== btn_repeat_stop_click called ===")
    
    if not repeat_timer then
        player.message("§c⚠️ Нет активного повторяющегося таймера!")
        player.sound("block.anvil.hit", 0.5, 0.5)
        return
    end
    
    -- Демонстрация timer.cancel()
    timer.cancel(repeat_timer)
    repeat_timer = nil
    
    local status_widget = screen.widget("repeat_status")
    if status_widget then
        status_widget.text("Остановлен")
        status_widget.bgColor(50, 25, 25)
    end
    
    player.message("§c⏹️ Повторяющийся таймер остановлен после " .. repeat_counter .. " итераций")
    player.message("§7Использована функция timer.cancel()")
    player.sound("block.piston.contract", 1.0, 1.0)
    
    log.info("Repeat timer cancelled after " .. repeat_counter .. " iterations")
    log.info("=== btn_repeat_stop_click completed ===")
end

-- =============================================================================
-- timer.times() - Ограниченные повторения
-- =============================================================================

function btn_times_5_click()
    log.info("=== btn_times_5_click called ===")
    
    local progress_widget = screen.widget("times_progress")
    if progress_widget then
        progress_widget.text("Прогресс: 0/5")
        progress_widget.bgColor(50, 50, 25)
    end
    
    player.message("§e🎯 Демонстрация timer.times(20, 5) - 5 повторений каждую секунду")
    player.sound("block.beacon.activate", 1.0, 1.0)
    
    -- Демонстрация timer.times()
    timer.times(20, 5, function(i)  -- каждые 20 тиков, 5 раз
        local progress_widget = screen.widget("times_progress")
        if progress_widget then
            progress_widget.text("Прогресс: " .. i .. "/5")
            
            -- Меняем цвет от красного к зеленому
            local red = 80 - (i * 10)
            local green = 20 + (i * 10)
            progress_widget.bgColor(red, green, 25)
        end
        
        player.sound("block.note_block.harp", 0.8, 0.8 + i * 0.2)
        
        if i == 3 then
            player.message("§e⚡ Половина пути пройдена!")
        elseif i == 5 then
            player.message("§a🎉 timer.times(20, 5) завершен!")
            player.sound("entity.player.levelup", 1.0, 1.0)
            
            -- Сбрасываем через 2 секунды
            timer.after(40, function()
                local progress_widget = screen.widget("times_progress")
                if progress_widget then
                    progress_widget.text("Прогресс: 0/0")
                    progress_widget.bgColor(50, 50, 25)
                end
            end)
        end
        
        log.info("Times(5) iteration: " .. i .. "/5")
    end)
    
    log.info("Times(5) timer started")
    log.info("=== btn_times_5_click completed ===")
end

function btn_times_10_click()
    log.info("=== btn_times_10_click called ===")
    
    local progress_widget = screen.widget("times_progress")
    if progress_widget then
        progress_widget.text("Прогресс: 0/10")
        progress_widget.bgColor(50, 50, 25)
    end
    
    player.message("§e🚀 Демонстрация timer.times(10, 10) - 10 повторений каждые 0.5 секунды")
    player.sound("block.beacon.power_select", 1.0, 1.0)
    
    -- Демонстрация timer.times() с более частыми интервалами
    timer.times(10, 10, function(i)  -- каждые 10 тиков, 10 раз
        local progress_widget = screen.widget("times_progress")
        if progress_widget then
            progress_widget.text("Прогресс: " .. i .. "/10")
            
            -- Радужный эффект
            local colors = {
                {80, 30, 30}, {80, 60, 30}, {60, 80, 30}, {30, 80, 30}, {30, 80, 60},
                {30, 60, 80}, {30, 30, 80}, {60, 30, 80}, {80, 30, 60}, {80, 30, 30}
            }
            
            local color = colors[i]
            if color and color[1] and color[2] and color[3] then
                progress_widget.bgColor(color[1], color[2], color[3])
            else
                progress_widget.bgColor(50, 50, 25) -- fallback color
            end
        end
        
        player.sound("block.note_block.chime", 0.5, 1.0 + i * 0.1)
        
        if i == 5 then
            player.message("§e⚡ Половина завершена!")
        elseif i == 10 then
            player.message("§a🌟 timer.times(10, 10) завершен!")
            player.sound("entity.firework_rocket.blast", 1.0, 1.0)
            
            -- Сбрасываем через 2 секунды
            timer.after(40, function()
                local progress_widget = screen.widget("times_progress")
                if progress_widget then
                    progress_widget.text("Прогресс: 0/0")
                    progress_widget.bgColor(50, 50, 25)
                end
            end)
        end
        
        log.info("Times(10) iteration: " .. i .. "/10")
    end)
    
    log.info("Times(10) timer started")
    log.info("=== btn_times_10_click completed ===")
end

function btn_times_animation_click()
    log.info("=== btn_times_animation_click called ===")
    
    local progress_widget = screen.widget("times_progress")
    local title_widget = screen.widget("title")
    
    player.message("§e🎨 Демонстрация анимации с timer.times()")
    player.sound("entity.firework_rocket.launch", 1.0, 1.0)
    
    if progress_widget then
        progress_widget.text("Анимация: 0/8")
        progress_widget.bgColor(50, 50, 25)
    end
    
    -- Анимация заголовка с timer.times()
    timer.times(8, 8, function(i)  -- каждые 8 тиков, 8 раз
        local progress_widget = screen.widget("times_progress")
        local title_widget = screen.widget("title")
        
        if progress_widget then
            progress_widget.text("Анимация: " .. i .. "/8")
        end
        
        if title_widget then
            -- Меняем цвет заголовка
            local colors = {
                {100, 50, 50}, {100, 80, 50}, {80, 100, 50}, {50, 100, 50},
                {50, 100, 80}, {50, 80, 100}, {50, 50, 100}, {80, 50, 100}
            }
            
            local color = colors[i]
            if color and color[1] and color[2] and color[3] then
                title_widget.bgColor(color[1], color[2], color[3])
            else
                title_widget.bgColor(80, 80, 40) -- fallback color
            end
            
            -- Меняем прозрачность
            local alpha = 150 + (i * 10)
            title_widget.bgAlpha(alpha)
        end
        
        player.sound("block.note_block.bell", 0.3, 1.0 + i * 0.15)
        
        if i == 8 then
            player.message("§a✨ Анимация завершена!")
            
            -- Возвращаем исходный цвет
            timer.after(20, function()
                local title_widget = screen.widget("title")
                local progress_widget = screen.widget("times_progress")
                
                if title_widget then
                    title_widget.bgColor(80, 80, 40)
                    title_widget.bgAlpha(200)
                end
                if progress_widget then
                    progress_widget.text("Прогресс: 0/0")
                    progress_widget.bgColor(50, 50, 25)
                end
            end)
        end
        
        log.info("Animation iteration: " .. i .. "/8")
    end)
    
    log.info("Animation with times() started")
    log.info("=== btn_times_animation_click completed ===")
end

-- =============================================================================
-- Комплексная демонстрация
-- =============================================================================

function btn_complex_timer_demo_click()
    log.info("=== btn_complex_timer_demo_click called ===")
    
    player.message("§e🧪 Запуск комплексной демонстрации Timer API...")
    player.sound("block.beacon.power_select", 1.0, 1.0)
    
    local after_widget = screen.widget("after_status")
    local repeat_widget = screen.widget("repeat_status")
    local times_widget = screen.widget("times_progress")
    
    -- Этап 1: timer.after()
    if after_widget then
        after_widget.text("Этап 1: after()")
        after_widget.bgColor(60, 30, 30)
    end
    
    timer.after(20, function()  -- 1 сек
        player.message("§71. timer.after() ✓")
        
        -- Этап 2: timer.times()
        if times_widget then
            times_widget.text("Этап 2: times()")
            times_widget.bgColor(30, 60, 30)
        end
        
        timer.times(10, 3, function(i)
            local times_widget = screen.widget("times_progress")
            if times_widget then
                times_widget.text("Этап 2: " .. i .. "/3")
            end
            
            if i == 3 then
                player.message("§72. timer.times() ✓")
                
                -- Этап 3: timer.repeat()
                local repeat_widget = screen.widget("repeat_status")
                if repeat_widget then
                    repeat_widget.text("Этап 3: repeat()")
                    repeat_widget.bgColor(30, 30, 60)
                end
                
                local demo_repeat_counter = 0
                repeat_timer = timer["repeat"](15, function()  -- используем module-level repeat_timer
                    demo_repeat_counter = demo_repeat_counter + 1
                    
                    local repeat_widget = screen.widget("repeat_status")
                    if repeat_widget then
                        repeat_widget.text("Этап 3: " .. demo_repeat_counter)
                    end
                    
                    if demo_repeat_counter >= 4 then
                        if repeat_timer then
                            timer.cancel(repeat_timer)
                            repeat_timer = nil
                        end
                        player.message("§73. timer.repeat() + timer.cancel() ✓")
                        
                        -- Финал
                        timer.after(20, function()
                            -- Проверяем виджеты перед использованием
                            local final_after = screen.widget("after_status")
                            local final_repeat = screen.widget("repeat_status")
                            local final_times = screen.widget("times_progress")
                            
                            if final_after and final_after.text and final_after.bgColor then
                                final_after.text("Готов к запуску")
                                final_after.bgColor(50, 50, 25)
                            end
                            if final_repeat and final_repeat.text and final_repeat.bgColor then
                                final_repeat.text("Остановлен")
                                final_repeat.bgColor(50, 25, 25)
                            end
                            if final_times and final_times.text and final_times.bgColor then
                                final_times.text("Прогресс: 0/0")
                                final_times.bgColor(50, 50, 25)
                            end
                            
                            player.message("§a🎉 Комплексная демонстрация Timer API завершена!")
                            player.sound("entity.player.levelup", 1.0, 1.0)
                            
                            log.info("Complex timer demo completed successfully")
                        end)
                    end
                end)
            end
        end)
    end)
    
    log.info("Complex timer demo started")
    log.info("=== btn_complex_timer_demo_click completed ===")
end