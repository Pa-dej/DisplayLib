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

void onClose(String reason) { log.info("shop closed (" + reason + "), sold=" + sold); }
```

- `onOpen()` / `onClose(reason)` - необязательные хуки жизненного цикла. `onClose` вызывается ровно
  один раз при **любом** закрытии, пока экран ещё на месте (виджеты читаются, `storage` пишется);
  `reason` - почему (параметр можно не объявлять):

  | `reason` | Когда |
  |---|---|
  | `"close"` | кнопка `CLOSE_SCREEN`, `screen.close()`, `/displaylib close`, `closepublic`, Java API |
  | `"distance"` | игрок отошёл дальше `closeDistance` |
  | `"switch"` | игроку открыт другой экран (`switchTo`, `screen.switchTo`, новое открытие поверх) |
  | `"quit"` / `"death"` / `"world"` / `"teleport"` / `"inventory"` | игрок вышел, умер, сменил мир, телепортировался, открыл инвентарь (`closeOn` в `config.jmc`) |
  | `"replaced"` | публичный экран поставлен заново с тем же id |
  | `"unload"` | мир публичного экрана выгружен (сохранённый экран вернётся, когда мир загрузится) |
  | `"shutdown"` | выключение сервера или плагина |

  Закрыть или переключить экран из `onClose` нельзя (вызов игнорируется), таймеры после него отменяются.
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
| `player` | `name()`, `uuid()`, `op()`, `online()`, `gamemode()`, `gamemode(mode)`, `health()`, `health(v)`, `message(text)`, `message(text, "#hex")`, `sound(name[, volume[, pitch]])`, `command(cmd)`, `input([prompt, [timeoutTicks,]] text -> ...)`, `cancelInput()`, `awaitingInput()` (ввод из чата, см. ниже), `handle()` (Bukkit `Player`, если открыт политикой) |
| `screen` | `id()`, `isPublic()`, `close()`, `switchTo(id)`, `widget(id)`, `widgets()`, `data(key)`, `data(key, value)` |
| виджет | `id()`, `type()`, `valid()`, `text()`, `text(v)`, `hoveredText(v)`, `frames(list, ticks)`, `hoveredFrames(list, ticks)`, `sprite(name)`, `sprite(atlas, name[, color])`, `spriteColor(color)`, `player(name[, hat])`, `hoveredSprite(atlas, name)`, `bgColor(r, g, b)`, `bgAlpha(a)`, `rotation()`, `rotation(deg[, ticks])`, `scale()`, `scale(s[, ticks])`, `scale(x, y, z[, ticks])`, `translation()`, `translation(x, y, z[, ticks])`, `visible()`, `visible(b)`, `enabled()`, `enabled(b)`, `tooltip()`, `tooltip(v)` |
| `storage` | `get(key)`, `get(key, default)`, `set(key, value)`, `has(key)`, `remove(key)`, `clear()`, `size()` - на игрока (у публичного экрана - общее на экран), сохраняется на диск |
| `timer` | `after(ticks, fn)`, `every(ticks, fn)`, `times(period, count, i -> ...)`, `cancel(id)`, `cancelAll()`, `active()` - 20 тиков = 1 с |
| `log` | `info(msg)`, `warn(msg)`, `error(msg)` |

Числовые параметры принимают и `int`, и `double`. Методы с `ticks` меняют трансформацию плавно - интерполирует клиент,
сервер не тикает: `timer.every(20, () -> w.rotation(w.rotation() + 90, 20))` крутит виджет почти бесплатно. Звук - ключ как в игре (`ui.button.click`)
или константа `Sound` (`UI_BUTTON_CLICK`).

`storage` сохраняется в `plugins/DisplayLib/data/storage/` (`players/<uuid>.jmc`, `public/<screen>.jmc`) и
переживает выход игрока и перезапуск сервера: данные игрока пишутся при выходе (после `onClose("quit")`),
всё - при выключении и автосохранением (`storage.autosaveSeconds`). Сохраняются числа (`int`, `long`, `double`),
строки, `boolean`, `null`, массивы и таблицы; объекты (игрок, виджет, экземпляры классов скрипта) не сохраняются -
в консоли будет предупреждение с ключом.

## Ввод из чата

`player.input(...)` просит у игрока текст: следующее его сообщение в чат не уходит в чат, а передаётся функции
скрипта строкой - как набрал игрок, без форматирования. Так делаются поля ввода на экранах.

```java
void price(dyn widget, dyn player) {
    player.input("Введите цену:", 600, text -> widget.text("Цена: " + text));
}

void rename(dyn widget, dyn player) {
    player.input([ { text: "Новое имя ", color: "gold" }, { text: "(или «-» для отмены)", color: "gray" } ], text -> {
        if (text == "-") return;
        storage.set("name", text);
        widget.text(text);
    });
}
```

| Вызов | Что делает |
|---|---|
| `player.input(fn)` | ждать следующее сообщение; `fn(text)` получит его текст |
| `player.input(prompt, fn)` | то же, сначала отправить подсказку `prompt` (строка или сегменты, как `player.message`) |
| `player.input(prompt, timeoutTicks, fn)` | то же с таймаутом в тиках (20 = 1 с); по таймауту `fn` не вызывается; `<= 0` - без таймаута |
| `player.cancelInput()` | снять ожидание, поставленное этим экраном |
| `player.awaitingInput()` | ждёт ли этот экран ввода от игрока |

- У игрока одно ожидание: новый `input` (с любого экрана) заменяет прежний.
- Ожидание снимается само при закрытии экрана, который его поставил (по любой причине), при выходе игрока
  с сервера и по таймауту. В `onClose` новое ожидание не ставится.
- `fn` выполняется в главном потоке под тем же сторожевым таймером и с тем же перехватом ошибок, что
  обработчики кликов. Если экран успел закрыться, пока сообщение шло, `fn` не вызывается.
- Публичный экран: используйте объект `player` из обработчика клика - ввод ждётся от кликнувшего игрока.
- Сообщение перехватывается на самом раннем приоритете `AsyncChatEvent` и отменяется: его не видят чат и
  обработчики других плагинов с `ignoreCancelled = true` (обычная практика). Плагины, которые слушают и
  отменённые сообщения, его всё же получат.

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
// storage скриптов: на диск (data/storage/) или только в памяти; автосохранение изменившихся данных, с (0 - только при выходе и выключении)
dyn storage = { persist: true, autosaveSeconds: 300 };
// частицы: предел одновременно живых на весь сервер; показ пакетами (без сущностей на сервере) или сущностями;
// с какого расстояния от точки запуска видно (для пакетов); частицы без коллизий - в отдельном потоке (для пакетов)
dyn particles = { maxAlive: 1500, packets: true, viewDistance: 96, async: true };
```
