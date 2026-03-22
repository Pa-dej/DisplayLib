-- Global Shop Script
-- Этот скрипт работает для глобального экрана, где нет конкретного игрока в контексте
-- Функции будут вызываться когда любой игрок кликнет на виджет

function on_open()
    log.info("Global shop screen opened")
end

function on_close()
    log.info("Global shop screen closed")
end

-- Функция покупки меча
function buy_sword()
    log.info("Someone tried to buy a diamond sword")
    -- В глобальном экране нет player в контексте, но можно получить игрока через другие способы
    -- Здесь можно добавить логику:
    -- 1. Проверить баланс игрока
    -- 2. Списать деньги
    -- 3. Выдать предмет
    -- 4. Показать сообщение
    
    -- Пример логики (нужно адаптировать под вашу экономическую систему):
    -- if player_has_money(100) then
    --     take_money(100)
    --     give_item("DIAMOND_SWORD")
    --     show_message("Вы купили алмазный меч за 100 монет!")
    -- else
    --     show_message("Недостаточно монет! Нужно: 100")
    -- end
end

-- Функция покупки брони
function buy_armor()
    log.info("Someone tried to buy diamond armor")
    -- Логика покупки брони за 200 монет
end

-- Функция покупки еды
function buy_food()
    log.info("Someone tried to buy golden apple")
    -- Логика покупки золотого яблока за 50 монет
end

-- Функция покупки инструментов
function buy_tools()
    log.info("Someone tried to buy diamond pickaxe")
    -- Логика покупки алмазной кирки за 150 монет
end

-- Функция проверки баланса
function check_balance()
    log.info("Someone checked their balance")
    -- Показать баланс игрока
    -- show_message("Ваш баланс: " .. get_player_money() .. " монет")
end

-- Функция продажи предметов
function sell_items()
    log.info("Someone tried to sell items")
    -- Логика продажи предметов из инвентаря
    -- Можно открыть GUI для выбора предметов или продать все ценные предметы автоматически
end

-- Вспомогательные функции (примеры, нужно адаптировать под вашу систему)

--[[
function player_has_money(amount)
    -- Проверить, есть ли у игрока достаточно денег
    -- return economy.getBalance(player) >= amount
    return true -- заглушка
end

function take_money(amount)
    -- Списать деньги с игрока
    -- economy.withdrawPlayer(player, amount)
end

function give_item(material)
    -- Выдать предмет игроку
    -- player.getInventory().addItem(new ItemStack(Material.valueOf(material)))
end

function show_message(message)
    -- Показать сообщение игроку
    -- player.sendMessage(message)
end

function get_player_money()
    -- Получить баланс игрока
    -- return economy.getBalance(player)
    return 0 -- заглушка
end
--]]