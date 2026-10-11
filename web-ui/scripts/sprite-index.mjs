// Индекс спрайтов для автодополнения в редакторе: сканирует public/assets/minecraft/textures
// (скопированные из клиента) и пишет public/assets/sprite-index.json: { atlas: [sprite, ...] }.
// Запуск: npm run sprites   (после копирования текстур; без него подсказки только по листу предметов)
import { readdirSync, statSync, writeFileSync, existsSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'assets', 'minecraft', 'textures');
const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'assets', 'sprite-index.json');

// атлас -> [папка в textures, префикс, который клиент добавляет к имени спрайта]
const ATLASES = {
  'minecraft:items': [['item', 'item/']],
  'minecraft:blocks': [['block', 'block/'], ['item', 'item/']],
  // иконки эффектов клиент кладёт в атлас gui под префиксом mob_effect/ (atlases/gui.json)
  'minecraft:gui': [['gui/sprites', ''], ['mob_effect', 'mob_effect/']],
  'minecraft:particles': [['particle', '']],
  'minecraft:paintings': [['painting', '']],
  'minecraft:banner_patterns': [['entity/banner', '']],
  'minecraft:shield_patterns': [['entity/shield', '']],
  'minecraft:armor_trims': [['trims', 'trims/']],
};

function walk(dir, prefix, acc) {
  if (!existsSync(dir)) return;
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, prefix, acc);
    else if (name.endsWith('.png')) acc.push(prefix + relative(dir0, p).replace(/\\/g, '/').replace(/\.png$/, ''));
  }
}
let dir0 = '';
const index = {};
for (const [atlas, sources] of Object.entries(ATLASES)) {
  const acc = [];
  for (const [sub, prefix] of sources) { dir0 = join(root, sub); walk(dir0, prefix, acc); }
  index[atlas] = [...new Set(acc)].sort();
}
writeFileSync(out, JSON.stringify(index));
console.log(Object.entries(index).map(([k, v]) => k + ': ' + v.length).join('\n'));
