-- Продвинутая демонстрация hover анимаций
-- Показывает сложные настройки и эффекты

function on_open()
    if not screen then
        log.info("Продвинутая демонстрация hover анимаций открыта")
        return
    end
    
    log.info("Продвинутая демонстрация hover анимаций открыта для " .. player.name())
    player.message("🎪 Продвинутые hover анимации")
    player.message("Изучите задержки, зацикливание и сложные комбинации")
    player.sound("BLOCK_ENCHANTMENT_TABLE_USE", 1.0, 1.0)
    
    -- Инициализируем данные (используем тики вместо os.time)
    screen.data("demo_started", 0)
end

function on_close()
    log.info("Продвинутая демонстрация закрыта")
    
    local start_ticks = screen.data("demo_started") or 0
    if start_ticks > 0 then
        player.message("Демонстрация завершена")
    end
end

function resetDemo()
    player.message("🔄 Демонстрация сброшена!")
    player.message("Все анимации возвращены к исходному состоянию")
    player.sound("BLOCK_ANVIL_USE", 0.8, 1.2)
    
    -- В реальной реализации здесь можно было бы сбросить состояния виджетов
    -- Например, вернуть виджет "no_reverse" к исходному размеру
    local no_reverse_btn = screen.widget("no_reverse")
    if no_reverse_btn then
        -- Эмуляция сброса через изменение текста
        no_reverse_btn.text("Сброшен!")
        timer.after(40, function()
            no_reverse_btn.text("Остается большим")
        end)
    end
    
    -- Сбрасываем счетчики
    screen.data("reset_count", (screen.data("reset_count") or 0) + 1)
    
    local resets = screen.data("reset_count") or 0
    if resets == 1 then
        player.message("Первый сброс выполнен")
    elseif resets >= 5 then
        player.message("Вы сбрасывали демонстрацию " .. resets .. " раз!")
    end
end

-- Функция для демонстрации информации о задержках
function showDelayInfo()
    player.message("⏰ Информация о задержках:")
    player.message("• Мгновенно: delay = 0 тиков")
    player.message("• 0.25 сек: delay = 5 тиков")
    player.message("• 0.5 сек: delay = 10 тиков") 
    player.message("• 1.0 сек: delay = 20 тиков")
    player.message("При tick_rate=4: 20 тиков = 1 секунда")
end

-- Функция для демонстрации информации о зацикливании
function showLoopInfo()
    player.message("🔄 Информация о зацикливании:")
    player.message("• loop = true: включает зацикливание")
    player.message("• loopCount = -1: бесконечные циклы")
    player.message("• loopCount = 3: ровно 3 повтора")
    player.message("• loopCount = 5: ровно 5 повторов")
end

-- Функция для демонстрации информации о комбинациях
function showComboInfo()
    player.message("🎭 Информация о комбинациях:")
    player.message("• type = COMBINED: несколько эффектов одновременно")
    player.message("• effects: массив отдельных анимаций")
    player.message("• Можно комбинировать SCALE + TRANSLATE + ROTATE")
    player.message("• Общие настройки: duration, delay, easing")
end

-- Автоматические подсказки каждые 10 секунд
local tip_counter = 0
local tips = {
    "💡 Совет: Наведите курсор и подождите, чтобы увидеть задержки",
    "💡 Совет: Зацикленные анимации продолжаются пока курсор наведен",
    "💡 Совет: reverseOnExit=false оставляет виджет в измененном состоянии",
    "💡 Совет: ELASTIC easing создает эффект пружины",
    "💡 Совет: Комбинированные анимации могут включать разные типы",
    "💡 Совет: duration влияет на скорость всех эффектов в комбинации"
}

function on_tick()
    tip_counter = tip_counter + 1
    
    -- Каждые 10 секунд (200 тиков при tick_rate=4)
    if tip_counter >= 200 then
        tip_counter = 0
        
        -- Показываем случайный совет (используем простую формулу вместо math.random)
        local tip_index = ((tip_counter / 50) % 6) + 1
        player.message(tips[tip_index])
    end
end

-- Функция для показа статистики анимаций
function showAnimationStats()
    player.message("📊 Статистика продвинутых анимаций:")
    player.message("• Анимаций с задержкой: 4")
    player.message("• Зацикленных анимаций: 3")
    player.message("• Комбинированных анимаций: 3")
    player.message("• Анимаций без возврата: 1")
    player.message("• Всего виджетов: 15")
    
    local resets = screen.data("reset_count") or 0
    player.message("• Количество сбросов: " .. resets)
end