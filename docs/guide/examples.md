# Примеры

Все экраны лежат в `plugins/DisplayLib/screens` после `/displaylib examples` и открываются командой
`/displaylib open <id>`. Снимки сделаны в [редакторе](/editor/){target="_self"} - в игре выглядит так же,
только с настоящим освещением.

## Хаб: `demo_menu`

Главное меню демонстраций; каждая кнопка переходит на свой экран через morph, кнопки «Назад»/«Закрыть» с одними и теми же `id` на всех экранах переезжают, а не мигают.

![demo_menu](/examples/demo_menu.png)

## `demo_widgets` - три типа виджетов и сегменты

`TEXT_BUTTON`, `SPRITE_BUTTON` (кирпичи), `ITEM_BUTTON` (меч) и строка с вставленными спрайтами предметов.

![demo_widgets](/examples/demo_widgets.png)

## `demo_hover` - реакции на наведение

Пресеты `SCALE`/`LIFT`/`BOUNCE`, `TRANSLATE`, `ROTATE`, `COMBINED` и кнопка, у которой меняется только фон.
В редакторе наведите мышь - анимация проигрывается с теми же длительностью и easing.

![demo_hover](/examples/demo_hover.png)

## `demo_frames` - кадровая анимация текста

`frames`/`frameInterval` и `hoveredFrames`: бегущие точки, «печатающийся» заголовок, стрелка при наведении.

![demo_frames](/examples/demo_frames.png)

## `demo_script` - API скрипта

`screen`, `widget`, `storage`, `log`, хуки `onOpen`/`onClose`; счётчик, который меняет текст и фон виджета.

![demo_script](/examples/demo_script.png)

## `demo_player` - PlayerAPI

Имя, UUID, режим игры, здоровье, сообщения, звуки и команды от имени игрока. «Поле ввода» - ввод из чата
(`player.input`): клик, и следующее сообщение игрока в чат не уходит в чат, а появляется в поле; повторный клик
отменяет ожидание, без ответа за 30 секунд поле возвращается к «нажмите». См. [Ввод из чата](/reference/script#ввод-из-чата).

![demo_player](/examples/demo_player.png)

## `demo_pages` - страницы списка и morph на тот же экран

`/displaylib open demo_pages`. Восемь строк заполняет `onOpen` из списка в скрипте; ◀/▶ меняют номер страницы в
`storage` и делают `screen.switchTo(screen.id())` - экран открывается заново, и строки посимвольно перетекают из
старых слов в новые, а фон стрелок - к цвету, который выставил `onOpen` (на краю списка стрелка серая и не
кликается). Цель morph - состояние виджетов после `onOpen`, а не заглушки «…» из файла.

![demo_pages](/examples/demo_pages.png)

## `demo_timers` - TimerAPI и модули

`after`, `every`, `times`, `cancel`, `active` и `import "lib/util.jmp"`.

![demo_timers](/examples/demo_timers.png)

## `demo_public` - публичный экран

Стоит в мире и виден всем: `/displaylib openpublic demo_public <x> <y> <z>`. Наведение у каждого игрока своё: кнопки «Голосов» и «Кто здесь?» подсвечиваются и анимируются только у того, кто на них смотрит. `switchTo` и `close` на публичном экране не работают, игрок известен только внутри обработчика клика.

![demo_public](/examples/demo_public.png)

## Частицы: `particles/*.jmc`

`/displaylib particle <id>` запускает эффект перед вами (`~ ~ ~` - в ногах), `particle stop` убирает все частицы.

| Эффект | Что показывает |
|---|---|
| `sparkle` | текст-звёздочки, разлёт `OUTWARD` из сферы, вращение, таяние к концу жизни |
| `confetti` | всплеск вверх, гравитация, коллизии с отскоком - ложится на пол |
| `poison` | испускание в течение 2 секунд из коробки, `viewers: "owner"` - видит только тот, для кого запущен |
| `coin_fountain` | предметы (`ITEM`), фонтан из круга с отскоком |
| `embers` | блоки (`BLOCK`), отрицательная гравитация - угли всплывают |
| `flame` | `PIXEL`, палитра пламени по доле жизни с прозрачным концом, всплытие и притяжение к оси (`attractAxis`) |
| `water_splash` | `PIXEL`, случайные бело-голубые капли, отскок от блоков, таяние (`opacity`) |
| `fireflies` | `PIXEL`, блуждание (`wander`) около точки запуска, появление и угасание |

## `morph_menu` → `morph_shop` → `morph_settings`

Исходная демонстрация morph: три экрана с общими `id` кнопок; текст и цвет меняются по шагам, позиция, масштаб и фон - плавно. В редакторе откройте два экрана и нажмите «Переход» в секции Morph.

| | | |
|---|---|---|
| ![morph_menu](/examples/morph_menu.png) | ![morph_shop](/examples/morph_shop.png) | ![morph_settings](/examples/morph_settings.png) |
