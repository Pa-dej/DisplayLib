// Форматированный текст (строка или сегменты { text, color } / { sprite, atlas, color } / { player, hat, color })
// в .jmc и обратно - общий для экранов и эффектов частиц.
import type { JmcTable, JmcValue } from './parser';
import type { RichText, Segment } from '../model/screen';

function jstr(s: string): string {
  return '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t') + '"';
}

/** Текст из значения файла: строка или список сегментов; null - нет значения. */
export function richFromJmc(v: JmcValue): RichText | null {
  if (v == null) return null;
  if (typeof v === 'string') return v;
  if (Array.isArray(v)) {
    const segs: Segment[] = [];
    for (const p of v) {
      if (typeof p === 'string') segs.push({ text: p });
      else if (p && typeof p === 'object') {
        const pt = p as JmcTable;
        if (pt.player != null) segs.push({ text: '', player: String(pt.player), hat: pt.hat === false ? false : undefined, color: pt.color == null ? undefined : String(pt.color) });
        else if (pt.sprite != null) segs.push({ text: '', sprite: String(pt.sprite), atlas: atlasOrNull(pt.atlas == null ? undefined : String(pt.atlas), String(pt.sprite)), color: pt.color == null ? undefined : String(pt.color) });
        else segs.push({ text: pt.text == null ? '' : String(pt.text), color: pt.color == null ? undefined : String(pt.color) });
      }
    }
    return segs;
  }
  return String(v);
}

/** Атлас, который плагин/клиент и так подставили бы по имени: item/… → items, block/… → blocks; такой не храним. */
export function inferredAtlas(sprite: string): string | null {
  const k = sprite.trim().replace(/^minecraft:/, '');
  return k.startsWith('item/') ? 'minecraft:items' : k.startsWith('block/') ? 'minecraft:blocks' : null;
}
export function atlasOrNull(atlas: string | null | undefined, sprite: string): string | undefined {
  if (atlas == null) return undefined;
  const a = atlas.trim(); if (!a) return undefined;
  const full = a.includes(':') ? a : 'minecraft:' + a;
  return full === inferredAtlas(sprite) ? undefined : a;
}

/** Текст в литерал .jmc. */
export function richToJmc(t: RichText): string {
  if (typeof t === 'string') return jstr(t);
  return '[ ' + t.map((s) => s.player !== undefined
    ? '{ player: ' + jstr(s.player) + (s.hat === false ? ', hat: false' : '') + (s.color ? ', color: ' + jstr(s.color) : '') + ' }'
    : s.sprite !== undefined
    ? '{ ' + (atlasFor(s.atlas, s.sprite) ? 'atlas: ' + jstr(atlasFor(s.atlas, s.sprite)!) + ', ' : '') + 'sprite: ' + jstr(spriteFor(s.atlas, s.sprite)) + (s.color ? ', color: ' + jstr(s.color) : '') + ' }'
    : '{ text: ' + jstr(s.text) + (s.color ? ', color: ' + jstr(s.color) : '') + ' }').join(', ') + ' ]';
}

/** Атлас для записи: заданный, иначе выведенный из имени (item/… → items, block/… → blocks); null - не писать. */
export function atlasFor(atlas: string | null | undefined, sprite: string): string | null {
  if (atlas && atlas.trim()) return atlas.trim() === 'minecraft:mob_effects' || atlas.trim() === 'mob_effects' ? 'minecraft:gui' : atlas.trim();
  const k = sprite.trim().replace(/^minecraft:/, '');
  if (k.startsWith('item/')) return 'minecraft:items';
  if (k.startsWith('block/')) return 'minecraft:blocks';
  return null;
}

/** Имя спрайта для записи: для бывшего атласа mob_effects добавляем префикс mob_effect/. */
export function spriteFor(atlas: string | null | undefined, sprite: string): string {
  const a = (atlas ?? '').trim();
  if ((a === 'minecraft:mob_effects' || a === 'mob_effects') && !sprite.startsWith('mob_effect/')) return 'mob_effect/' + sprite.replace(/^minecraft:/, '');
  return sprite;
}
