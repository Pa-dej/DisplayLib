import { defineConfig } from 'vitepress';

export default defineConfig({
  lang: 'ru-RU',
  title: 'DisplayLib',
  description: 'Экранные интерфейсы из display-сущностей для Paper: конфиги .jmc, скрипты .jmp, визуальный редактор',
  base: '/DisplayLib/',
  head: [['link', { rel: 'icon', href: '/DisplayLib/favicon.svg' }]],
  cleanUrls: true,
  themeConfig: {
    logo: '/logo.svg',
    nav: [
      { text: 'Руководство', link: '/guide/getting-started' },
      { text: 'Справочник', link: '/reference/screen' },
      { text: 'Редактор', link: '/editor/', target: '_self' },
    ],
    sidebar: [
      { text: 'Руководство', items: [
        { text: 'Быстрый старт', link: '/guide/getting-started' },
        { text: 'Редактор экранов', link: '/guide/editor' },
        { text: 'Команды и API', link: '/guide/commands' },
        { text: 'Примеры', link: '/guide/examples' },
      ] },
      { text: 'Справочник', items: [
        { text: 'Экран (.jmc)', link: '/reference/screen' },
        { text: 'Скрипт (.jmp)', link: '/reference/script' },
        { text: 'Частицы (.jmc)', link: '/reference/particles' },
      ] },
    ],
    socialLinks: [{ icon: 'github', link: 'https://github.com/Pa-dej/DisplayLib' }],
    outline: { label: 'На этой странице', level: [2, 3] },
    docFooter: { prev: 'Назад', next: 'Дальше' },
    search: { provider: 'local' },
  },
});
