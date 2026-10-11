# DisplayLib Screen Editor (web-ui)

Веб-редактор экранов DisplayLib: 3D-предпросмотр как в игре, виджеты, фон, скрипты,
сохранение прямо в `plugins/DisplayLib/screens/` (hot reload плагина подхватывает).

Стек: Vite + Vue 3 + TypeScript + Three.js. Стиль - VitePress.

## Запуск

```
npm install
npm run dev        # http://localhost:5173
npm run build      # статика в dist/
npm run preview    # отдать dist/ на http://localhost:4173
```

Редактор на диск не пишет: экраны импортируются вставкой `.jmc` и экспортируются кнопками
«Скачать» (Ctrl+S: `screens/<id>.jmc` и, если нужен, `scripts/<id>.jmp`) или «Экспорт» (копировать /
скачать). Открытые экраны переживают перезагрузку вкладки - они автосохраняются в localStorage.

## Ассеты предпросмотра

- **Предметы**: спрайт `css-blocks-and-entities/icons-minecraft-268.*` уже разложен в
  `public/assets/items/icons.png` + `src/items.json` (1728 предметов).
- **Шрифт Minecraft** (чтобы текст был пиксель в пиксель): из клиента
  `%APPDATA%\.minecraft\versions\1.21.x\1.21.x.jar` (это zip) скопируйте две папки, сохраняя структуру:

  | в jar | куда |
  |---|---|
  | `assets/minecraft/font/` (default.json, include/*.json) | `public/assets/minecraft/font/` |
  | `assets/minecraft/textures/font/` (ascii.png, accented.png, nonlatin_european.png …) | `public/assets/minecraft/textures/font/` |

  `unifont` (unihex) не нужен и пропускается. Без png используется запасной шрифт: размеры
  близкие, вид другой. Файлы берутся из jar клиента 1.21.11 и входят в репозиторий (папки: font, textures/{font,item,block,gui/sprites,mob_effect,particle,painting,entity/banner,entity/shield,trims}).

## Иконки блоков в тексте

У блоков-кубов (`barrel`, `anvil`, лестницы…) в клиенте нет плоской `item/`-текстуры - иконка в инвентаре
рендерится из 3D-модели, и компонент `{atlas, sprite}` её показать не может: в игре будет «missing texture».
Редактор в этом случае показывает шахматку и подсказывает грани `block/<name>`. Ресурспаков плагин
не использует намеренно.

## Размер экрана

«Масштаб ×» в свойствах экрана умножает позиции, масштабы, tolerance и translation всех виджетов и фона,
а также смещения hover-анимаций (offset, translation, вложенные effects) - пропорции сохраняются.
Относительные величины (scale анимации, intensity, повороты) не меняются. Отмена - Ctrl+Z.

## Как считается предпросмотр

Формулы клиента (`DisplayRenderer.TextDisplayRenderer`): пиксель текста = 1/40 блока, строка 10 px,
фон от (-1, -1) до (ширина, строки·10) в координатах шрифта, якорь TextDisplay - низ по центру,
затем `translation` и `scale` из transformation. Предмет в `ItemDisplayTransform.GUI` - спрайт
~1 блок при scale 1, якорь - центр. Камера - глаза игрока: 2 блока до экрана, 0.72 блока выше
его центра (экран ставится на высоте height/2 = 0.9, глаза на 1.62), FOV 70.

Всё, чего нельзя проверить без клиента, вынесено во вкладку **Калибровка**: знак оси X для
`position` и `translation`, размер предмета, пиксель, высота строки, отступы фона, камера.
Значения хранятся в браузере. Сверьте один раз с игрой на экране `jumper_test`.

## Управление

- ЛКМ по виджету - выбрать и тащить в плоскости экрана (Shift - шаг 0.5, Alt - 0.01).
- Стрелки - сдвиг x/y выбранного виджета, PgUp/PgDn - z. Ctrl+Z / Ctrl+Shift+Z - отмена/повтор.
- ПКМ - вращать камеру, колесо - зум, СКМ - сдвиг. Кнопки: «Как игрок», «В центр», «Спереди», «Всё».
- Ctrl+S - сохранить текущий экран (файл называется по `id`), Ctrl+Shift+S - все изменённые.
- Внизу - живой текст `.jmc` и редактор скрипта `.jmp` экрана (сканирует функции для списка onClick,
  «создать заготовку» делает скрипт с обработчиками для всех `RUN_SCRIPT`-кнопок).

## Структура

```
src/model/screen.ts   модель и значения по умолчанию (как в плагине)
src/jmc/parser.ts     разбор подмножества Jumper-конфига (.jmc) - то же, что Config.load
src/jmc/toModel.ts    таблица -> модель (синонимы ключей как в ScreenLoader)
src/jmc/writer.ts     модель -> .jmc
src/jmp/scan.ts       функции и хуки в .jmp, заготовка скрипта
src/render/metrics.ts константы клиента и калибровка
src/render/font.ts    растровый шрифт из ресурсов клиента (+ запасной)
src/render/text.ts    текстура TextDisplay: сегменты, §-коды, перенос по 200 px
src/render/items.ts   спрайты предметов
src/render/scene.ts   Three.js: экран, камера, выделение, перетаскивание, зоны наведения
src/fs.ts             скачивание файлов
src/store.ts          состояние, undo/redo, сохранение
src/components/       интерфейс
```
