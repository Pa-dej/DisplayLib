-- Демонстрация системы анимации hover
-- Показывает различные реакции на клики по анимированным виджетам

function on_open()
    log.info("Демонстрация hover анимаций открыта для " .. player.name())
    player.message("🎭 Добро пожаловать в демонстрацию hover анимаций!")
    player.message("Наведите курсор на виджеты чтобы увидеть анимации")
    player.sound("ENTITY_EXPERIENCE_ORB_PICKUP", 1.0, 1.2)
end

function on_close()
    log.info("Демонстрация hover анимаций закрыта")
    player.message("До свидания! 👋")
end

-- === ОБРАБОТЧИКИ ПРЕДУСТАНОВОК ===

function onBounceClick()
    player.message("🏀 Bounce эффект! Подпрыгивание с отскоком")
    player.sound("BLOCK_SLIME_BLOCK_PLACE", 1.0, 1.5)
    
    -- Показываем информацию об анимации
    player.message("Настройки: preset=BOUNCE, duration=8, intensity=1.15, easing=BOUNCE")
end

function onScaleClick()
    player.message("📏 Scale эффект! Плавное увеличение размера")
    player.sound("BLOCK_ANVIL_PLACE", 0.5, 2.0)
    
    player.message("Настройки: preset=SCALE, duration=6, intensity=1.25, easing=EASE_OUT")
end

function onLiftClick()
    player.message("⬆️ Lift эффект! Поднятие виджета вверх")
    player.sound("ENTITY_PHANTOM_FLAP", 1.0, 1.8)
    
    player.message("Настройки: preset=LIFT, duration=8, intensity=1.5, easing=EASE_IN_OUT")
end

function onPulseClick()
    player.message("💓 Pulse эффект! Ритмичная пульсация")
    player.sound("BLOCK_NOTE_BLOCK_BELL", 1.0, 1.0)
    
    player.message("Настройки: preset=PULSE, duration=12, intensity=1.3, easing=EASE_IN_OUT")
end

function onSpinClick()
    player.message("🔄 Spin эффект! Поворот с эластичностью")
    player.sound("BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON", 1.0, 0.8)
    
    player.message("Настройки: preset=SPIN, duration=10, intensity=1.0, easing=ELASTIC")
end

-- === ОБРАБОТЧИКИ НАСТРАИВАЕМЫХ АНИМАЦИЙ ===

function onCustomScaleClick()
    player.message("📐 Настраиваемое масштабирование!")
    player.message("Растягивание: X=1.4, Y=1.1, Z=1.0")
    player.sound("BLOCK_BAMBOO_WOOD_PRESSURE_PLATE_CLICK_ON", 1.0, 1.2)
    
    -- Демонстрируем изменение виджета через API
    local btn = screen.widget("custom_scale_btn")
    if btn then
        btn.text("📐 Clicked!")
        timer.after(40, function()
            btn.text("📐 Custom Scale")
        end)
    end
end

function onTranslateClick()
    player.message("➡️ Смещение позиции!")
    player.message("Offset: X=0.05, Y=0.03, Z=0.0")
    player.sound("ENTITY_ITEM_PICKUP", 1.0, 1.5)
    
    local btn = screen.widget("translate_btn")
    if btn then
        btn.text("➡️ Moved!")
        timer.after(40, function()
            btn.text("➡️ Translate")
        end)
    end
end

function onRotateClick()
    player.message("🔄 Поворот виджета!")
    player.message("Rotation: Y=25°, easing=ELASTIC")
    player.sound("BLOCK_BAMBOO_WOOD_TRAPDOOR_OPEN", 1.0, 1.0)
    
    local btn = screen.widget("rotate_btn")
    if btn then
        btn.text("🔄 Rotated!")
        timer.after(40, function()
            btn.text("🔄 Rotate")
        end)
    end
end

function onCombinedClick()
    player.message("🎭 Комбинированная анимация!")
    player.message("Scale + Translate + Rotate одновременно")
    player.sound("BLOCK_AMETHYST_BLOCK_CHIME", 1.0, 1.2)
    
    local btn = screen.widget("combined_btn")
    if btn then
        btn.text("🎭 Amazing!")
        btn.bgColor(255, 215, 0) -- Золотой цвет
        timer.after(60, function()
            btn.text("🎭 Combined")
            btn.bgColor(150, 50, 100) -- Возвращаем исходный цвет
        end)
    end
end

-- === ОБРАБОТЧИКИ ПРЕДМЕТОВ ===

function onSwordClick()
    player.message("⚔️ Алмазный меч выбран!")
    player.message("Анимация: SCALE с BOUNCE эффектом")
    player.sound("ITEM_ARMOR_EQUIP_DIAMOND", 1.0, 1.0)
    
    -- Увеличиваем счетчик кликов
    local clicks = screen.data("sword_clicks") or 0
    clicks = clicks + 1
    screen.data("sword_clicks", clicks)
    
    player.message("Кликов по мечу: " .. clicks)
end

function onGemClick()
    player.message("💎 Изумруд собран!")
    player.message("Анимация: ROTATE с зацикливанием")
    player.sound("BLOCK_AMETHYST_CLUSTER_BREAK", 1.0, 1.5)
    
    -- Эффект частиц (эмуляция)
    player.message("✨ Магические частицы! ✨")
end

function onPotionClick()
    player.message("🧪 Зелье использовано!")
    player.message("Анимация: TRANSLATE с левитацией (3 цикла)")
    player.sound("ENTITY_GENERIC_DRINK", 1.0, 1.2)
    
    -- Эффект лечения (эмуляция)
    player.message("💚 +5 HP восстановлено!")
end

-- === ДОПОЛНИТЕЛЬНЫЕ ФУНКЦИИ ===

-- Функция для демонстрации динамического изменения анимаций
function toggleAnimationSpeed()
    local fast_mode = screen.data("fast_mode") or false
    fast_mode = not fast_mode
    screen.data("fast_mode", fast_mode)
    
    if fast_mode then
        player.message("⚡ Быстрый режим анимаций включен!")
        player.message("(В реальной реализации можно динамически менять duration)")
    else
        player.message("🐌 Обычная скорость анимаций")
    end
end

-- Функция для показа статистики
function showAnimationStats()
    local sword_clicks = screen.data("sword_clicks") or 0
    
    player.message("📊 Статистика демонстрации:")
    player.message("• Кликов по мечу: " .. sword_clicks)
    player.message("• Всего виджетов: 21")
    player.message("• Типов анимаций: 6")
    player.message("• Типов easing: 6")
end

-- Автоматическое обновление каждые 5 секунд
local update_counter = 0
function on_tick()
    update_counter = update_counter + 1
    
    -- Каждые 5 секунд (100 тиков при tick_rate=4)
    if update_counter >= 100 then
        update_counter = 0
        
        -- Можно добавить периодические эффекты
        local online_count = #game.getOnlinePlayers()
        if online_count > 1 then
            -- Показываем сообщение только если есть другие игроки
            player.message("👥 Онлайн игроков: " .. online_count .. " (демонстрация работает для всех!)")
        end
    end
end

-- Функция для сброса демонстрации
function resetDemo()
    screen.data("sword_clicks", 0)
    screen.data("fast_mode", false)
    
    player.message("🔄 Демонстрация сброшена!")
    player.sound("BLOCK_ANVIL_USE", 0.8, 1.0)
end