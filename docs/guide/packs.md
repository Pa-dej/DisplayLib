# Экраны в своём плагине

Плагину с экранами не обязательно складывать файлы в `plugins/DisplayLib/`. Его папка может быть отдельным
**набором содержимого** со своими экранами, скриптами, частицами и своей политикой доступа скриптов:

```
plugins/WordBomb/
├── screens/       # экраны .jmc
├── scripts/       # скрипты .jmp (import "lib/x.jmp" - относительно скрипта)
├── particles/     # эффекты частиц .jmc
└── scripts.jma    # политика доступа скриптов ЭТОЙ папки
```

## Подключение

Из кода плагина - в `onEnable` (в `plugin.yml`: `depend: [DisplayLib]`):

```java
import padej.displayLib.pack.ContentPacks;

@Override
public void onEnable() {
    ContentPacks.register(this);   // plugins/<ИмяПлагина>/
}
```

Или без кода - в `config.jmc` DisplayLib (папки относительно `plugins/`):

```java
dyn packs = ["WordBomb"];
```

Недостающие папки создаются, `scripts.jma` при отсутствии - копия политики DisplayLib по умолчанию. Экраны и
частицы набора загружаются сразу, `screens/` отслеживается hot reload, `/displaylib reload` перечитывает всё,
включая политики наборов. Когда плагин-владелец выключается, его набор снимается сам (открытые экраны
доживают до закрытия). `/displaylib list` помечает экраны набора: `wordbomb_config [WordBomb]`.

## Как это работает

- **Скрипт экрана** ищется в `scripts/` того же набора, что и экран: `script: "wordbomb_config.jmp"` у экрана из
  `plugins/WordBomb/screens/` - это `plugins/WordBomb/scripts/wordbomb_config.jmp`. Скрипт по умолчанию
  (`scripts/<id>.jmp`) - тоже в своём наборе. Выйти за пределы своей `scripts/` нельзя.
- **Политика доступа** - своя: скрипты набора подчиняются `plugins/WordBomb/scripts.jma`, а не
  `plugins/DisplayLib/scripts.jma`. Можно открыть скриптам свой API, не трогая политику DisplayLib, и наоборот.
- **id общие.** Экран открывается по id как обычно (`/displaylib open wordbomb_config`,
  `UIManager.getInstance().openScreen(player, "wordbomb_config")`), `switchTo` работает между наборами. Если id
  совпал, остаётся экран набора, зарегистрированного раньше (DisplayLib - первым), в консоли предупреждение. То же
  для эффектов частиц. Удобно начинать id с имени плагина: `wordbomb_config`, `wordbomb_boom`.
- **`storage`** - общее хранилище DisplayLib (`plugins/DisplayLib/data/storage/`), ключи у всех экранов игрока одни;
  используйте префикс: `storage.set("wordbomb.wins", ...)`.

## Java API

| Метод | Что делает |
|---|---|
| `ContentPacks.register(Plugin)` | Папка плагина как набор; возвращает `ContentPack` |
| `ContentPacks.register(Plugin, Path)` | Любая папка как набор этого плагина |
| `ContentPacks.unregister(Plugin)` | Снять наборы плагина (при его выключении - само) |
| `ContentPacks.all()`, `get(name)`, `main()` | Наборы; `main()` - DisplayLib |
| `ContentPack.screens()/scripts()/particles()/policyFile()` | Пути набора |
