# DisplayLib - интерактивные 3D-экраны в Minecraft на Jumper

DisplayLib - плагин для Paper (1.21.x), который рисует интерактивные интерфейсы прямо в игровом
мире на Display-сущностях. Экран описывается конфигом [Jumper](https://jumper-lang.github.io/)
(`.jmc`), логика пишется на Jumper (`.jmp`), а что скриптам можно трогать в Java, задаёт
политика доступа (`.jma`).

## Возможности

- 📋 **Экраны как конфиги** (`screens/*.jmc`) - данные с выражениями, переменными и `if`, без кода
- 🔧 **Скрипты на Jumper** (`scripts/*.jmp`) - синтаксис Java, лёгкость Lua, модули через `import`
- 🔒 **Песочница** (`scripts.jma`) - белый список пакетов и классов; сторожевой таймер против зависших скриптов
- 🎮 **Виджеты** - текстовые и предметные кнопки, форматированный текст, подсказки, hover-анимации
- 👥 **Приватные и публичные экраны** - для одного игрока или для всех поблизости
- 🔄 **Hot Reload** - изменённый экран перечитывается сам, скрипт - при следующем открытии
- 💾 **Хранилище и таймеры** - данные на игрока (сохраняются на диск), отложенные и повторяющиеся действия
- ✨ **Частицы из дисплеев** (`particles/*.jmc`) - цветные квадраты, текст, спрайты, предметы и блоки; разлёт, гравитация,
  коллизии, притяжение и блуждание, цвет и прозрачность во времени;
  движение интерполирует клиент, поэтому сотни частиц почти бесплатны
- 🧰 **Поддержка редакторов** - `META-INF/jumper/host.jmc` описывает глобалы и хуки для плагинов Jumper
  (IntelliJ IDEA, VS Code, Neovim)

## Быстрый старт

### 1. Установка

1. Соберите плагин: `./gradlew build` (нужен JDK 21)
2. Скопируйте JAR из `build/libs/` в папку `plugins/` сервера
3. Перезапустите сервер

### 2. Первый запуск

Плагин создаст `plugins/DisplayLib/` с `config.jmc` и `scripts.jma`. Примеры выгружаются командой:

```
/displaylib examples
```

```
plugins/DisplayLib/
  config.jmc          # настройки плагина
  scripts.jma         # политика доступа скриптов
  screens/            # экраны (.jmc)
    demo_menu.jmc     # меню демонстраций: виджеты, наведение, кадры, скрипт, игрок, таймеры
    demo_widgets.jmc  demo_hover.jmc  demo_frames.jmc  demo_script.jmc  demo_player.jmc  demo_timers.jmc
    demo_public.jmc   # публичный экран (openpublic)
    morph_menu.jmc  morph_shop.jmc  morph_settings.jmc   # morph-переходы
  scripts/            # скрипты (.jmp) - по одному на экран, плюс lib/util.jmp (import)
  particles/          # эффекты частиц: sparkle, confetti, poison, coin_fountain, embers, flame, water_splash, fireflies
  data/               # пишет плагин: storage скриптов, сохранённые публичные экраны
```

### 3. Тестирование

```
/displaylib open demo_menu    # Меню демонстраций (все демо связаны кнопками «Назад»)
/displaylib openpublic demo_public <x> <y> <z>   # Публичный экран в мире
/displaylib list              # Список экранов
/displaylib close             # Закрыть экран
/displaylib particle confetti # Эффект частиц перед собой (админ); particle list / stop
/displaylib reload            # Перечитать экраны, политику и скрипты (админ)
/displaylib validate          # Перечитать и показать проблемы: дубли id, клики в никуда, нет скрипта/функции (админ)
```

## Экран (`screens/*.jmc`)

Файл экрана - конфиг Jumper: переменные верхнего уровня и есть конфиг. Можно использовать
выражения, переменные, тернарный оператор и `if`; циклов, функций и Java в конфиге нет.

```java
// screens/shop.jmc
String id = "shop";
String screenType = "PRIVATE";      // PRIVATE или PUBLIC
int tickRate = 4;                   // период обновления, тики (1-20)
double interactionRadius = 5;       // дальше - нет наведения и кликов (-1 = без ограничения)
double closeDistance = 10;          // дистанция автозакрытия (-1 = не закрывать)

dyn background = { color: [20, 20, 30], alpha: 200, scale: [12, 6, 1], anchor: "CENTER" };
// anchor - какая точка фона стоит в начале координат экрана (position [0,0,0] виджетов):
// "BOTTOM" (низ по центру, по умолчанию), "CENTER", "TOP_LEFT", … или доли [x, y] - [0.5, 0.5] = центр
String script = "shop.jmp";         // scripts/shop.jmp
dyn morph = { duration: 12, steps: 6 };   // анимация смены/закрытия экрана (см. ниже); по умолчанию выключена

dyn gold = "#FFD700";               // переменные можно переиспользовать
dyn widgets = [
    {
        id: "title",
        type: "TEXT_BUTTON",
        text: [ { text: "Магазин ", color: gold }, { text: "v2", color: "gray" } ],
        position: [0, 2, 0],
        scale: [0.6, 0.6, 0.6],
        backgroundAlpha: 0,
    },
    {
        id: "sword",
        type: "ITEM_BUTTON",
        material: "DIAMOND_SWORD",
        position: [-1.5, 0, 0],
        tooltip: "Алмазный меч - 100 монет",
        hoverAnimation: { type: "PRESET", preset: "LIFT" },
        onClick: "buySword",                        // void buySword(dyn widget, dyn player)
    },
    {
        id: "bricks",
        type: "SPRITE_BUTTON",                      // любой спрайт клиента (1.21.9+), плоский как текст
        sprite: "block/bricks",                     // atlas по умолчанию: items для item/..., иначе blocks
        hoveredSprite: "block/cracked_stone_bricks",
        position: [1.5, 0, 0],
        scale: [2, 2, 1],                           // спрайт = 8×8 текстовых пикселей = 0.2 блока при scale 1
        backgroundAlpha: 0,
    },
    {
        id: "quest",
        type: "TEXT_BUTTON",                        // спрайты можно вставлять и в текст
        text: [ { text: "Принести: ", color: "gray" }, { sprite: "item/iron_ingot" }, { text: " x3", color: "white" } ],
        position: [0, -1, 0],
    },
    { id: "back",  type: "TEXT_BUTTON", text: "Назад",   position: [-3, -2, 0], onClick: { switchTo: "demo_menu" } },
    { id: "close", type: "TEXT_BUTTON", text: "Закрыть", position: [3, -2, 0],  onClick: "close" },
];
```

Поля виджетов: `id`, `type`, `position`, `scale`, `tolerance`, `translation`, `rotation` (градусы в плоскости экрана, + = против часовой, вокруг центра виджета; зона наведения не поворачивается), `text`, `hoveredText`,
`alignment`, `backgroundColor`, `backgroundAlpha`, `hoveredBackgroundColor`, `hoveredBackgroundAlpha`,
`material`, `glowOnHover`, `glowColor`, `sprite`, `atlas`, `hoveredSprite`, `player`/`hoveredPlayer`/`hat` (голова игрока по нику - объект `player`, клиент 1.21.9+; и сегмент `{ player: "Steve" }` в тексте), `tooltip`, `tooltipColor`, `tooltipDelay`,
`lineWidth` (ширина переноса текста, px шрифта, по умолчанию 200), `clickSound` (ключ звука `ui.button.click` / константа `UI_BUTTON_CLICK` / таблица `{ sound, volume, pitch }` / `false`; по умолчанию текст - `block.dispenser.fail` pitch 2, предмет - `ui.button.click`), `hoverSound` (то же, играет при наведении; считается hover-реакцией), `onClick`, `hoverAnimation` (у `ROTATE` - `angle` и `axis`: `[0, 0, 1]` - в плоскости экрана, `[0, 1, 0]` - вокруг вертикали; складывается со статическим `rotation`). Тип можно не писать: есть `material` - `ITEM_BUTTON`, есть `sprite` - `SPRITE_BUTTON`,
иначе `TEXT_BUTTON`. `SPRITE_BUTTON` - это TextDisplay с компонентом-объектом (`{"atlas": ..., "sprite": ...}`),
поэтому у него есть фон, hover и `tolerance` как у текстовой кнопки; из скрипта - `widget.sprite("item/apple")`.
Атласы клиента: `minecraft:items`, `minecraft:blocks`, `minecraft:gui` для иконок эффектов (`mob_effect/speed`), `minecraft:gui`
(`hud/heart/full`), `minecraft:particles` (`heart`), `minecraft:paintings`, `minecraft:banner_patterns`,
`minecraft:shield_patterns`, `minecraft:armor_trims`. Имя спрайта - путь к png внутри папки атласа без расширения.
Тинт спрайта - как цвет текста: `{ sprite: "hud/heart/full", atlas: "minecraft:gui", color: "#ff5555" }`
(клиент умножает текстуру на цвет; без `color` - белый, без тинта); у `SPRITE_BUTTON` поля `spriteColor`/`color`
и `hoveredSpriteColor`, из скрипта `widget.sprite(atlas, sprite, color)` и `widget.spriteColor("gold")`.
Голое имя без атласа плагин дополняет сам: `mace` → `item/mace` (items), `bricks` → `block/bricks` (blocks),
`speed` → `mob_effect/speed` в gui. Сегменты без `color` всегда белые - цвет соседнего сегмента не протекает.
Имена из прежних YAML-файлов в snake_case (`tick_rate`, `screen_type`) тоже принимаются.

### Действия `onClick`

| Запись | Что делает |
|---|---|
| `onClick: true` | вызвать функцию скрипта, названную как `id` виджета: `void <id>(widget, player)` |
| `onClick: "buySword"` | вызвать функцию `buySword(widget, player)` скрипта |
| `onClick: { switchTo: "demo_menu" }` | открыть другой экран на том же месте |
| `onClick: "close"` | закрыть экран |
| `onClick: { action: "RUN_SCRIPT", function: "f" }` | полная форма (`NONE`, `SWITCH_SCREEN` + `target`, `CLOSE_SCREEN`, `RUN_SCRIPT` + `function`) |

### Кадровая анимация текста

```java
{ id: "loading", frames: ["Загрузка", "Загрузка.", "Загрузка.."], frameInterval: 6 },
{ id: "play", text: "Играть", hoveredFrames: ["> Играть", " > Играть"], hoveredFrameInterval: 3, onClick: true },
```

`frames` - кадры (строки или массивы сегментов, как `text`), сменяются каждые `frameInterval` тиков;
`hoveredFrames` - свой набор при наведении (`hoveredFrameInterval`, по умолчанию как `frameInterval`;
на публичных экранах не показываются). `text` при этом - первый кадр. Из скрипта: `widget.frames(list, ticks)`,
`widget.hoveredFrames(list, ticks)`; `widget.text(...)` отменяет анимацию. Кадры тикаются каждый серверный тик
независимо от `tickRate` (одна общая задача; у экрана без кадров это проверка пустого списка).
Пример: `demo_frames`.

### Статические виджеты (не тикаются)

При каждой загрузке экранов (старт, `/displaylib reload`, hot reload) плагин разбирает их и составляет
для каждого экрана список виджетов, за взглядом на которые следить незачем. Виджет не тикается, если
**все** три условия выполнены:

1. клик ничего не делает: `onClick` нет / `none`; функция скрипта не объявлена или пустая (`void b(dyn w, dyn p) {}`);
   `switchTo` на несуществующий или публичный экран; на публичном экране - любые `switchTo`/`close`;
2. нет реакции на наведение: подсказки, hover-анимации, другого текста/спрайта, свечения предмета;
3. фон при наведении не отличается: RGBA совпадают, либо альфа обоих фонов 0 (фона не видно - RGB не важен).

Отдельно: виджет с `tolerance` `<= 0` по любой оси не тикается всегда - в зону нулевого размера не попасть взглядом.

Такие виджеты не попадают в список обсчитываемых на тике и не перехватывают клики; экран из одних
надписей не делает hit-test вовсе. `/displaylib list` показывает число виджетов и не тикаемые id.
Скрипт для п. 1 только разбирается (без выполнения); функции из `import` считаются рабочими.

### Morph - анимация смены и закрытия экрана

```java
dyn morph = { enabled: true, duration: 12, steps: 6 };   // или boolean morph = true; или int morph = 12;
```

При `switchTo` между экранами (и при закрытии) экран не исчезает мгновенно:

- виджет с тем же `id` и тем же типом сущности (текст/спрайт ↔ текст/спрайт, предмет ↔ предмет)
  на обоих экранах **переезжает**: позицию, масштаб и цвет фона интерполирует клиент за `duration`
  тиков (1-59); содержимое и цвет текста клиент не интерполирует - они меняются по `steps` шагам:
  общие начало и конец строки остаются, середина старого текста «съедается», середина нового
  «вырастает» (`"text"` → `"extext"`, `"exatext"`, … `"example text"`), цвет идёт от старого к новому
  по RGB; предмет `ITEM_BUTTON` сменяется на половине пути;
- виджет только старого экрана сжимается в ноль, его фон становится прозрачным, потом удаляется;
- виджет только нового экрана вырастает из нуля с прозрачного фона;
- фон экрана - такой же «переезжающий» виджет (размер, позиция, цвет).

При переключении действует `morph` открываемого экрана, при закрытии - закрываемого.
Пока переход идёт, наведение и клики не обрабатываются. `steps` по умолчанию - `duration / 2`.
Прозрачность текста не анимируется: клиент интерполирует `text_opacity` как знаковый байт.
Пример: `morph_menu` → `morph_shop` → `morph_settings` (`/displaylib open morph_menu`); все `demo_*` экраны тоже переходят друг в друга через morph.

## Скрипт (`scripts/*.jmp`)

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

### API скриптов

| Глобал | Методы |
|---|---|
| `player` | `name()`, `uuid()`, `op()`, `online()`, `gamemode()`, `gamemode(mode)`, `health()`, `health(v)`, `message(text)`, `message(text, "#hex")`, `sound(name[, volume[, pitch]])`, `command(cmd)`, `handle()` (Bukkit `Player`, если открыт политикой) |
| `screen` | `id()`, `isPublic()`, `close()`, `switchTo(id)`, `widget(id)`, `widgets()`, `data(key)`, `data(key, value)` |
| виджет | `id()`, `type()`, `valid()`, `text()`, `text(v)`, `hoveredText(v)`, `bgColor(r, g, b)`, `bgAlpha(a)`, `visible()`, `visible(b)`, `enabled()`, `enabled(b)`, `tooltip()`, `tooltip(v)`, `player(name[, hat])`, `rotation()`, `rotation(deg[, ticks])`, `scale()`, `scale(s[, ticks])`, `scale(x, y, z[, ticks])`, `translation()`, `translation(x, y, z[, ticks])` - с `ticks` плавно (интерполяция клиента) |
| `storage` | `get(key)`, `get(key, default)`, `set(key, value)`, `has(key)`, `remove(key)`, `clear()`, `size()` - на игрока (у публичного экрана - общее на экран), сохраняется на диск |
| `timer` | `after(ticks, fn)`, `every(ticks, fn)`, `times(period, count, i -> ...)`, `cancel(id)`, `cancelAll()`, `active()` - 20 тиков = 1 с |
| `log` | `info(msg)`, `warn(msg)`, `error(msg)` |

Числовые параметры принимают и `int`, и `double`. Звук - ключ как в игре (`ui.button.click`)
или константа `Sound` (`UI_BUTTON_CLICK`).

`storage` сохраняется в `plugins/DisplayLib/data/storage/` (`players/<uuid>.jmc`, `public/<screen>.jmc`) и
переживает выход игрока и перезапуск сервера: данные игрока пишутся при выходе (после `onClose("quit")`),
всё - при выключении и автосохранением (`storage.autosaveSeconds`). Сохраняются числа (`int`, `long`, `double`),
строки, `boolean`, `null`, массивы и таблицы; объекты (игрок, виджет, экземпляры классов скрипта) не сохраняются -
в консоли будет предупреждение с ключом.

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

## Частицы (`particles/*.jmc`)

Эффект - конфиг Jumper: что рисуется (`render`), откуда и сколько (`emitter`), как движется (`motion`),
как меняется размер и поворот (`animation`). Сервер шлёт частице кадр раз в `keyframeTicks` тиков, между
кадрами её ведёт клиент.

```java
// particles/sparkle.jmc
String id = "sparkle";
String viewers = "all";              // "owner" - только игрок, для которого запущен
int keyframeTicks = 4;
dyn render = { type: "TEXT", variants: ["★", "✦"], colors: ["#ffe066", "#ffffff"], brightness: 15 };   // TEXT, PIXEL, ITEM, BLOCK
dyn emitter = { shape: "SPHERE", radius: 0.15, count: 16 };                                          // POINT, SPHERE, CIRCLE, BOX
dyn motion = { lifetime: [16, 26], speed: [0.06, 0.14], direction: "OUTWARD", drag: 0.12 };          // + gravity, collide, bounce
dyn animation = { scale: [0.5, 0.9], endScale: 0, easing: "EASE_IN", fadeIn: 3, rotation: [0, 360], spin: [-12, 12] };
// + color: [[0, "#ffffff"], [0.4, "#ff8800"], [1, "#00000000"]], opacity: [1, 0]; motion: attract, attractAxis, wander
```

`/displaylib particle <effect> [x y z] [player] [count]`, из Java - `Particles.play("sparkle", location[, player])`.
Все поля - в справочнике документации (`docs/reference/particles.md`).

## Настройки (`config.jmc`)

```java
boolean hotReload = true;      // следить за screens/
int scriptTimeoutMs = 1000;    // сторожевой таймер: вызов скрипта дольше этого прерывается; 0 - выключен
// автозакрытие приватного экрана (выход с сервера - всегда): смерть, смена мира, телепорт дальше N блоков (0 - нет), открытие инвентаря
dyn closeOn = { death: true, worldChange: true, teleport: 8, inventory: false };
// storage скриптов: на диск (data/storage/) или только в памяти; автосохранение изменившихся данных, с (0 - только при выходе и выключении)
dyn storage = { persist: true, autosaveSeconds: 300 };
// частиц-сущностей одновременно на весь сервер; сверх предела новые не появляются
dyn particles = { maxAlive: 1500 };
```

## Система координат

Позиция виджета задаётся относительно центра фона в локальных осях экрана:

```
        Y+
        |
 -X ----+---- +X    (смотришь на экран)
        |
        Y-
```

## Команды

| Команда | Назначение | Права |
|---|---|---|
| `/displaylib open <screen_id> [player] [x y z] [yaw pitch]` | Открыть приватный экран | - |
| `/displaylib close` | Закрыть свой экран | - |
| `/displaylib list` | Список загруженных экранов | - |
| `/displaylib validate` | Перезагрузить и показать проблемы экранов (клики, которые не сработают; дубли id; скрипты) | `displaylib.admin` |
| `/displaylib openpublic <screen_id> <x> <y> <z> [yaw] [pitch]` | Поставить публичный экран в мире; сохраняется и встаёт на место после перезапуска | `displaylib.admin` |
| `/displaylib closepublic <screen_id>` | Убрать публичный экран (и из сохранённых) | `displaylib.admin` |
| `/displaylib listpublic` | Список публичных экранов | `displaylib.admin` |
| `/displaylib particle <effect> [x y z] [player] [count]` | Запустить эффект частиц (`particles/*.jmc`); `particle list`, `particle stop` | `displaylib.admin` |
| `/displaylib reload` | Перечитать экраны, политику и сбросить кэш скриптов | `displaylib.admin` |
| `/displaylib examples` | Выгрузить примеры в папку плагина | `displaylib.admin` |

Алиасы: `/dlib`, `/dl`.

## Java API для других плагинов

- `UIManager.getInstance()` - `openScreen`, `switchScreen`, `closeScreen`, `openPublicScreen`,
  `closePublicScreenById`, `getActiveScreen`, `getPublicScreens`.
- `DisplayClickEvent` - отменяемое событие клика по виджету приватного или публичного экрана:
  `getPlayer()`, `getScreenId()`, `getWidgetId()`, `isPublicScreen()`; отмена отменяет действие клика.
- `CloseReason` - причина закрытия: `closeScreen(player, reason)`, её получает `onClose` скрипта.
- `openPublicScreen(id, location, yaw, pitch, persistent)` - `persistent = true` (так делает
  `/displaylib openpublic`) запоминает экран в `data/public_screens.jmc`: после перезапуска он встаёт
  на то же место (экраны из миров, загруженных позже, - когда мир загрузится). `closePublicScreenById`
  убирает его и из сохранённых. Без флага (`openPublicScreen(id, location, yaw, pitch)`) экран, как и раньше,
  живёт до выключения - для плагинов, которые сами расставляют экраны при включении.
- `Particles` - эффекты частиц: `play(id, location[, owner])` → `ParticleEmission` (`stop()`, `cancel()`, `setCenter()`),
  `effect(id)`, `ids()`, `stopAll()`. Формат эффектов - справочник «Частицы».
- `ScreenInstance#getScriptContext()` / `GlobalScreenInstance#getScriptContext()` -
  `call("functionName", args...)` вызывает функцию скрипта экрана.

## Сборка

Плагин живёт в `plugin/` монорепозитория; редактор - в `web-ui/`, документация - в `docs/` (https://pa-dej.github.io/DisplayLib/).

Локальный деплой на свой сервер: `./gradlew deployLocal -PserverPlugins=<путь к plugins/>` (или переменная `DISPLAYLIB_SERVER`; можно положить `serverPlugins=` в `gradle.properties`) - копирует jar и примеры; `copyExamples` - только примеры. В `build` это не входит.

Jumper подключается через JitPack: `com.github.jumper-lang:jumper:<тег релиза>` (см. `build.gradle.kts`, сейчас `v0.11.2`).
Gradle 9.7 (wrapper), JDK 21+. Частицы-пакеты используют внутренние классы сервера: компиляция идёт против
dev bundle Paper через [paperweight-userdev](https://docs.papermc.io/paper/dev/userdev/) (`paperweight.paperDevBundle`
в `build.gradle.kts`; первая сборка скачивает и готовит сервер - несколько минут). Jar - на mojang-именах, как и сам
Paper 1.21.11, без переобфускации.

## Лицензия

MIT
