# Скрипт (`scripts/*.jmp`)

```java
// scripts/shop.jmp
import "lib/economy.jmp";                  // модули - относительно папки скрипта

int sold = 0;                              // живёт, пока открыт экран

void onOpen() {
    player.message("Добро пожаловать, " + player.name() + "!");
    screen.widget("title").bgAlpha(0);
    timer.after(10, () -> screen.widget("title").bgAlpha(200));
}

void buySword(dyn widget, dyn player) {
    int coins = storage.get("coins", 0);
    if (coins < 100) {
        player.message("Нужно 100 монет, у вас " + coins, "#FF5555");
        player.sound("block.note_block.bass", 1, 0.7);
        return;
    }
    storage.set("coins", coins - 100);
    sold++;
    widget.tooltip("Продано: " + sold);
    player.sound("entity.player.levelup");
    player.command("give @s diamond_sword");
}

void onClose() { log.info("shop closed, sold=" + sold); }
```

- `onOpen()` / `onClose()` - необязательные хуки жизненного цикла.
- Обработчик клика получает `(widget, player)`; оба аргумента можно опустить.
- У **публичного** экрана глобал `player` равен `null`: игрок известен только внутри обработчика
  клика (аргумент). Сохраняйте его в замыкании, если он нужен в таймере.
- Каждый открытый экран получает собственный интерпретатор: переменные верхнего уровня не делятся
  между экранами и игроками.
- Замыкание захватывает переменную, а не значение. Чтобы запланировать несколько таймеров в цикле,
  вынесите один шаг в функцию - её параметры свои у каждого вызова.

## API скриптов

| Глобал | Методы |
|---|---|
| `player` | `name()`, `uuid()`, `op()`, `online()`, `gamemode()`, `gamemode(mode)`, `health()`, `health(v)`, `message(text)`, `message(text, "#hex")`, `sound(name[, volume[, pitch]])`, `command(cmd)`, `handle()` (Bukkit `Player`, если открыт политикой) |
| `screen` | `id()`, `isPublic()`, `close()`, `switchTo(id)`, `widget(id)`, `widgets()`, `data(key)`, `data(key, value)` |
| виджет | `id()`, `type()`, `valid()`, `text()`, `text(v)`, `hoveredText(v)`, `frames(list, ticks)`, `hoveredFrames(list, ticks)`, `sprite(name)`, `sprite(atlas, name[, color])`, `spriteColor(color)`, `hoveredSprite(atlas, name)`, `bgColor(r, g, b)`, `bgAlpha(a)`, `rotation()`, `rotation(deg[, ticks])`, `scale()`, `scale(s[, ticks])`, `scale(x, y, z[, ticks])`, `translation()`, `translation(x, y, z[, ticks])`, `skull(spec)`, `visible()`, `visible(b)`, `enabled()`, `enabled(b)`, `tooltip()`, `tooltip(v)` |
| `storage` | `get(key)`, `get(key, default)`, `set(key, value)`, `has(key)`, `remove(key)`, `clear()`, `size()` - на игрока, в памяти сервера |
| `timer` | `after(ticks, fn)`, `every(ticks, fn)`, `times(period, count, i -> ...)`, `cancel(id)`, `cancelAll()`, `active()` - 20 тиков = 1 с |
| `log` | `info(msg)`, `warn(msg)`, `error(msg)` |

Числовые параметры принимают и `int`, и `double`. Методы с `ticks` меняют трансформацию плавно - интерполирует клиент,
сервер не тикает: `timer.every(20, () -> w.rotation(w.rotation() + 90, 20))` крутит виджет почти бесплатно. Звук - ключ как в игре (`ui.button.click`)
или константа `Sound` (`UI_BUTTON_CLICK`).

## Политика доступа (`scripts.jma`)

Скрипт видит только то, что открыто в политике; остальное закрыто ещё на этапе разбора.
Файл по умолчанию открывает API плагина, `java.lang` и `java.util` (без `System`, `Thread`,
`Class`, `Runtime`) и модули:

```java
Policy.allowPackage("padej.displayLib.script.api");
Policy.allowPackage("java.lang");
Policy.allowPackage("java.util");
Policy.allowModules();
Policy.maxTableSize(100000);
// Policy.allowPackage("org.bukkit");   // открыть Bukkit через player.handle() - осознанно
```

## Настройки (`config.jmc`)

```java
boolean hotReload = true;      // следить за screens/
int scriptTimeoutMs = 1000;    // сторожевой таймер: вызов скрипта дольше этого прерывается; 0 - выключен
// автозакрытие приватного экрана (выход с сервера - всегда): смерть, смена мира, телепорт дальше N блоков (0 - нет), открытие инвентаря
dyn closeOn = { death: true, worldChange: true, teleport: 8, inventory: false };
```
