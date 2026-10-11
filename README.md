# DisplayLib

[![plugin](https://github.com/Pa-dej/DisplayLib/actions/workflows/plugin.yml/badge.svg)](https://github.com/Pa-dej/DisplayLib/actions/workflows/plugin.yml) [![pages](https://github.com/Pa-dej/DisplayLib/actions/workflows/pages.yml/badge.svg)](https://github.com/Pa-dej/DisplayLib/actions/workflows/pages.yml) [![release](https://img.shields.io/github/v/release/Pa-dej/DisplayLib?label=release)](https://github.com/Pa-dej/DisplayLib/releases/latest)

Экранные интерфейсы из display-сущностей для Paper 1.21.11: экраны описываются конфигами Jumper (`.jmc`),
поведение — скриптами (`.jmp`), а собираются в визуальном редакторе прямо в браузере.

**Документация и редактор:** https://pa-dej.github.io/DisplayLib/ · редактор: https://pa-dej.github.io/DisplayLib/editor/

**Скачать плагин:** jar последнего релиза - на странице [Releases](https://github.com/Pa-dej/DisplayLib/releases/latest); сборка каждого коммита - артефакт `DisplayLib` в workflow `plugin`. Нужен Paper 1.21.11 и JDK 21.

```
plugin/    плагин (Gradle, JDK 21)      - см. plugin/README.md
web-ui/    редактор экранов (Vite + Vue 3 + Three.js)
docs/      сайт документации (VitePress)
```

## Сборка

```bash
cd plugin && ./gradlew build          # jar в plugin/build/libs/
cd web-ui && npm install && npm run dev
cd docs   && npm install && npm run dev
```

Релиз плагина — тег `v<версия>` (`git tag v3.1.0 && git push origin v3.1.0`): workflow `plugin` собирает `DisplayLib-1.21.11+3.1.0.jar` (версия из тега) и прикладывает к GitHub Release с автосгенерированными заметками; что вошло — в [CHANGELOG.md](CHANGELOG.md).
Сайт собирается workflow `pages` при изменениях в `docs/` или `web-ui/` (документация в корень, редактор в `/editor/`).

## Лицензия

MIT
