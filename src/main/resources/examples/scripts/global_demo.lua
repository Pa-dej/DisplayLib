-- Public Demo Script
-- Simple example: Hello button and counter with +/- buttons

-- Shared counter variable (shared between all players)
local counter = 0

function on_open()
    log.info("Public demo screen opened")
    -- Load counter from storage
    counter = storage.get("global_counter", 0)
    update_counter_display()
end

function on_close()
    log.info("Public demo screen closed")
    -- Save counter to storage
    storage.set("global_counter", counter)
end

-- Hello button function
function say_hello()
    local playerName = player.name()
    
    -- Send personalized message to the player who clicked
    player.message("§a§lHello, " .. playerName .. "! §r§7Welcome to the public screen!")
    
    -- Play sound
    player.sound("entity.player.levelup", 1.0, 1.0)
    
    -- Log to console
    log.info("Player " .. playerName .. " said hello!")
end

-- Increment counter
function increment_counter()
    counter = counter + 1
    update_counter_display()
    
    local playerName = player.name()
    player.message("§a+1 §7Counter: §f" .. counter .. " §7(changed by: " .. playerName .. ")")
    player.sound("block.note_block.harp", 1.0, 1.2)
    
    log.info("Player " .. playerName .. " incremented counter to " .. counter)
    
    -- Save to storage
    storage.set("global_counter", counter)
end

-- Decrement counter
function decrement_counter()
    if counter > 0 then
        counter = counter - 1
        update_counter_display()
        
        local playerName = player.name()
        player.message("§c-1 §7Counter: §f" .. counter .. " §7(changed by: " .. playerName .. ")")
        player.sound("block.note_block.bass", 1.0, 0.8)
        
        log.info("Player " .. playerName .. " decremented counter to " .. counter)
        
        -- Save to storage
        storage.set("global_counter", counter)
    else
        player.message("§cCounter is already zero!")
        player.sound("block.note_block.didgeridoo", 1.0, 0.5)
    end
end

-- Update counter display
function update_counter_display()
    local counter_widget = screen.widget("counter_value")
    
    if counter_widget then
        counter_widget.text(tostring(counter))
        log.info("Updated counter display to: " .. counter)
    else
        log.warning("Counter widget not found!")
    end
end