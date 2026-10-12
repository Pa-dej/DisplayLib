# Экран (`screens/*.jmc`)

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

Поля виджетов: `id`, `type`, `position`, `scale`, `tolerance`, `translation`, `rotation`, `text`, `hoveredText`,
`alignment`, `backgroundColor`, `backgroundAlpha`, `hoveredBackgroundColor`, `hoveredBackgroundAlpha`,
`material`, `glowOnHover`, `glowColor`, `sprite`, `atlas`, `hoveredSprite`, `spriteColor`, `hoveredSpriteColor`,
`lineWidth` (ширина переноса текста, px шрифта; 200 по умолчанию), `tooltip`, `tooltipColor`, `tooltipDelay`, `clickSound`, `hoverSound`, `onClick`, `hoverAnimation`. Тип можно не писать: есть `material` - `ITEM_BUTTON`, есть `sprite` - `SPRITE_BUTTON`,
иначе `TEXT_BUTTON`. `SPRITE_BUTTON` - это TextDisplay с компонентом-объектом (`{"atlas": ..., "sprite": ...}`),
поэтому у него есть фон, hover и `tolerance` как у текстовой кнопки; из скрипта - `widget.sprite("item/apple")`.
Атласы клиента: `minecraft:items`, `minecraft:blocks`, `minecraft:gui` для иконок эффектов (`mob_effect/speed`), `minecraft:gui`
(`hud/heart/full`), `minecraft:particles` (`heart`), `minecraft:paintings`, `minecraft:banner_patterns`,
`minecraft:shield_patterns`, `minecraft:armor_trims`. Имя спрайта - путь к png внутри папки атласа без расширения.
Голое имя без атласа плагин дополняет сам: `mace` → `item/mace` (items), `bricks` → `block/bricks` (blocks),
`speed` → `mob_effect/speed` в gui. Сегменты без `color` всегда белые - цвет соседнего сегмента не протекает.
Имена из прежних YAML-файлов в snake_case (`tick_rate`, `screen_type`) тоже принимаются.

## Поворот, тинт спрайтов, звуки

```java
{ id: "tag", text: "NEW", rotation: 15 },                                   // градусы в плоскости экрана, + = против часовой
{ id: "hp", atlas: "minecraft:gui", sprite: "hud/heart/container", spriteColor: "red", hoveredSpriteColor: "#ff9999" },
{ id: "quest", text: [ { text: "HP " }, { atlas: "minecraft:gui", sprite: "hud/heart/full", color: "red" } ] },
{ id: "buy", text: "Купить", clickSound: { sound: "block.note_block.pling", volume: 0.6, pitch: 1.4 }, hoverSound: "ui.button.click", onClick: true },
{ id: "silent", text: "Тихо", clickSound: false },
```

- `rotation` вращает виджет вокруг его центра (текст, спрайт и предмет). Зона наведения не вращается - её центр
  совпадает с центром поворота. Hover-поворот (`ROTATE`, `WOBBLE`) складывается с ним. Из скрипта - `widget.rotation(deg[, ticks])`.
- Тинт спрайта - это цвет текста компонента: клиент умножает текстуру на цвет. Белые и серые иконки (HUD, эффекты,
  узоры) красятся чисто, цветные предметы лишь затемняются. `hoveredSpriteColor` считается hover-реакцией.
- Звуки: ключ клиента (`ui.button.click`), константа (`UI_BUTTON_CLICK`), таблица `{ sound, volume, pitch }` или `false`.
  По умолчанию клик у текста/спрайта - `block.dispenser.fail` (pitch 2), у предмета - `ui.button.click`; `hoverSound` по
  умолчанию нет (и он тоже делает виджет интерактивным).

## Объёмные предметы и головы игроков

```java
{ id: "cube", material: "GRASS_BLOCK", itemTransform: "NONE", tilt: [30, 45], scale: [0.5, 0.5, 0.5] },  // блок кубом в изометрии
{ id: "me",   skull: "@viewer", itemTransform: "FIXED", tilt: [20, -30] },     // голова того, кто смотрит
{ id: "notch", skull: "Notch" },                                                 // по имени (или UUID) - профиль дотянется с Mojang
{ id: "custom", skull: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvLi4uIn19fQ==" }, // Value с minecraft-heads.com
{ id: "url",   skull: "https://textures.minecraft.net/texture/<hash>" },
```

- `itemTransform` - как `ItemDisplay` показывает предмет: `GUI` (по умолчанию) - плоская иконка как в инвентаре;
  `FIXED` - объёмно, как в рамке (блоки в половину размера); `NONE` - полный размер; `HEAD`, `GROUND`,
  `THIRDPERSON_RIGHTHAND`, … - остальные режимы клиента. Синонимы: `block`/`3d` = `FIXED`, `icon` = `GUI`.
- `tilt: [x, y]` - наклон объёмного предмета в градусах (вокруг X - кивок, вокруг Y - разворот), поверх `rotation`.
- `skull` - голова игрока; `material` при этом можно не писать (станет `PLAYER_HEAD`). Имя/UUID требуют запроса
  к Mojang - он идёт асинхронно, голова появится через мгновение; base64-значение или URL текстуры ставятся сразу
  и работают офлайн; `@viewer` - голова смотрящего (на публичных экранах - Стив). Из скрипта: `widget.skull("...")`.
  Форма-таблица тоже принимается: `skull: { owner: "Notch" }`, `{ texture: "..." }`, `{ url: "..." }`.

## Действия `onClick`

| Запись | Что делает |
|---|---|
| `onClick: true` | вызвать функцию скрипта, названную как `id` виджета: `void <id>(widget, player)` |
| `onClick: "buySword"` | вызвать функцию `buySword(widget, player)` скрипта |
| `onClick: { switchTo: "demo_menu" }` | открыть другой экран на том же месте |
| `onClick: "close"` | закрыть экран |
| `onClick: { action: "RUN_SCRIPT", function: "f" }` | полная форма (`NONE`, `SWITCH_SCREEN` + `target`, `CLOSE_SCREEN`, `RUN_SCRIPT` + `function`) |

## Кадровая анимация текста

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

## Статические виджеты (не тикаются)

При каждой загрузке экранов (старт, `/displaylib reload`, hot reload) плагин разбирает их и составляет
для каждого экрана список виджетов, за взглядом на которые следить незачем. Виджет не тикается, если
**все** три условия выполнены:

1. клик ничего не делает: `onClick` нет / `none`; функция скрипта не объявлена или пустая (`void b(dyn w, dyn p) {}`);
   `switchTo` на несуществующий или публичный экран; на публичном экране - любые `switchTo`/`close`;
2. нет реакции на наведение: подсказки, hover-анимации, hover-звука, другого текста/спрайта/тинта, свечения предмета;
3. фон при наведении не отличается: RGBA совпадают, либо альфа обоих фонов 0 (фона не видно - RGB не важен).

Отдельно: виджет с `tolerance` `<= 0` по любой оси не тикается всегда - в зону нулевого размера не попасть взглядом.

Такие виджеты не попадают в список обсчитываемых на тике и не перехватывают клики; экран из одних
надписей не делает hit-test вовсе. `/displaylib list` показывает число виджетов и не тикаемые id.
Скрипт для п. 1 только разбирается (без выполнения); функции из `import` считаются рабочими.
`/displaylib validate` перечитывает экраны и показывает в чате всё, что в игре проявится как «кнопка не работает»:
дубли `id`, клики в никуда, отсутствующие скрипты и функции. Редактор показывает те же предупреждения в панели экрана.

## Morph - анимация смены и закрытия экрана

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

## Система координат

Позиция виджета задаётся относительно центра фона в локальных осях экрана:

```
        Y+
        |
 -X ----+---- +X    (смотришь на экран)
        |
        Y-
```
