---
layout: home
hero:
  name: DisplayLib
  text: Экранные интерфейсы из display-сущностей
  tagline: Плагин для Paper 1.21.11 — экраны в мире из TextDisplay/ItemDisplay, конфиги и скрипты на Jumper, визуальный редактор в браузере
  image:
    src: /logo.svg
    alt: DisplayLib
  actions:
    - theme: brand
      text: Начать
      link: /guide/getting-started
    - theme: alt
      text: Открыть редактор
      link: /editor/
      target: _self
    - theme: alt
      text: GitHub
      link: https://github.com/Pa-dej/DisplayLib
features:
  - icon: 🧩
    title: Экран — это файл .jmc
    details: Виджеты, фон, morph-переходы и анимации наведения описываются конфигом Jumper; hot reload подхватывает правки без перезапуска.
  - icon: 🐸
    title: Скрипты .jmp
    details: Обработчики кликов, хуки onOpen/onClose, таймеры, хранилище игрока, API игрока и ввод из чата — под политикой доступа scripts.jma.
  - icon: 🖥️
    title: Редактор в браузере
    details: 3D-предпросмотр как в игре, умные направляющие, кадровые анимации, экспорт .jmc и .jmp одним нажатием.
---
  - icon: ✨
    title: Morph-переходы
    details: Одноимённые виджеты плавно переезжают между экранами, текст и цвет меняются по шагам, остальное растворяется и вырастает.
  - icon: 🎞️
    title: Кадровая анимация
    details: Строки и спрайты сменяют друг друга каждые N тиков — в покое и при наведении; тикается одной общей задачей.
  - icon: ⚡
    title: Дёшево на тиках
    details: Статические виджеты вычисляются при загрузке и не участвуют в hit-test; клики и наведение считаются только там, где они что-то делают.
---
