-- Global Info Board Script
-- Информационная доска для отображения различной информации о сервере

function on_open()
    log.info("Global info board opened")
    -- Обновляем время при открытии
    update_server_time()
end

function on_close()
    log.info("Global info board closed")
end

-- Показать правила сервера
function show_rules()
    log.info("Someone requested server rules")
    -- Здесь можно показать правила через чат или открыть книгу
    -- Пример сообщений в чат:
    --[[
    player.sendMessage("§6=== Правила сервера ===")
    player.sendMessage("§71. Не гриферить постройки других игроков")
    player.sendMessage("§72. Не использовать читы и дюпы")
    player.sendMessage("§73. Быть вежливым в чате")
    player.sendMessage("§74. Не спамить и не флудить")
    player.sendMessage("§75. Слушаться администрацию")
    --]]
end

-- Показать список команд
function show_commands()
    log.info("Someone requested command list")
    -- Показать доступные команды
    --[[
    player.sendMessage("§e=== Доступные команды ===")
    player.sendMessage("§a/spawn §7- телепорт на спавн")
    player.sendMessage("§a/home §7- телепорт домой")
    player.sendMessage("§a/sethome §7- установить дом")
    player.sendMessage("§a/tpa <игрок> §7- запрос телепортации")
    player.sendMessage("§a/balance §7- проверить баланс")
    player.sendMessage("§a/shop §7- открыть магазин")
    --]]
end

-- Показать онлайн игроков
function show_online()
    log.info("Someone requested online players list")
    -- Показать список онлайн игроков
    --[[
    local online_players = {}
    for player in server.getOnlinePlayers() do
        table.insert(online_players, player.getName())
    end
    
    player.sendMessage("§b=== Онлайн игроки (" .. #online_players .. ") ===")
    for i, name in ipairs(online_players) do
        player.sendMessage("§7" .. i .. ". §f" .. name)
    end
    --]]
end

-- Показать статистику сервера
function show_stats()
    log.info("Someone requested server statistics")
    -- Показать различную статистику
    --[[
    player.sendMessage("§d=== Статистика сервера ===")
    player.sendMessage("§7Онлайн: §f" .. server.getOnlinePlayers().size() .. "/" .. server.getMaxPlayers())
    player.sendMessage("§7Время работы: §f" .. get_uptime())
    player.sendMessage("§7TPS: §f" .. get_tps())
    player.sendMessage("§7Использование RAM: §f" .. get_memory_usage())
    --]]
end

-- Показать новости
function show_news()
    log.info("Someone requested news")
    -- Показать последние новости сервера
    --[[
    player.sendMessage("§c=== Последние новости ===")
    player.sendMessage("§6[15.03.2024] §fОбновление 1.2.0")
    player.sendMessage("§7- Добавлены новые квесты")
    player.sendMessage("§7- Исправлены баги с экономикой")
    player.sendMessage("§7- Улучшена производительность")
    player.sendMessage("")
    player.sendMessage("§6[10.03.2024] §fВесенний ивент")
    player.sendMessage("§7- Специальные награды до 31 марта")
    player.sendMessage("§7- Новые достижения")
    --]]
end

-- Показать полезные ссылки
function show_links()
    log.info("Someone requested useful links")
    -- Показать ссылки на Discord, YouTube и т.д.
    --[[
    player.sendMessage("§b=== Полезные ссылки ===")
    player.sendMessage("§9Discord: §fhttps://discord.gg/yourserver")
    player.sendMessage("§cYouTube: §fhttps://youtube.com/yourchannel")
    player.sendMessage("§eФорум: §fhttps://forum.yourserver.com")
    player.sendMessage("§aВКонтакте: §fhttps://vk.com/yourgroup")
    player.sendMessage("")
    player.sendMessage("§7Кликните на ссылку в чате для перехода!")
    --]]
end

-- Обновить время сервера
function update_time()
    log.info("Updating server time display")
    update_server_time()
end

-- Вспомогательная функция для обновления времени
function update_server_time()
    -- Получаем текущее время и обновляем виджет
    --[[
    local current_time = os.date("%H:%M:%S")
    local current_date = os.date("%d.%m.%Y")
    local time_widget = screen.widget("server_time")
    
    if time_widget then
        time_widget.text("🕐 " .. current_date .. " " .. current_time)
    end
    --]]
end

-- Вспомогательные функции для статистики (примеры)
--[[
function get_uptime()
    -- Получить время работы сервера
    local uptime_ticks = server.getTicksRunning()
    local uptime_seconds = uptime_ticks / 20
    local hours = math.floor(uptime_seconds / 3600)
    local minutes = math.floor((uptime_seconds % 3600) / 60)
    return hours .. "ч " .. minutes .. "м"
end

function get_tps()
    -- Получить TPS сервера
    return string.format("%.1f", server.getTPS())
end

function get_memory_usage()
    -- Получить использование памяти
    local runtime = java.lang.Runtime.getRuntime()
    local used = runtime.totalMemory() - runtime.freeMemory()
    local total = runtime.totalMemory()
    local percent = (used * 100) / total
    return string.format("%.1f%%", percent)
end
--]]