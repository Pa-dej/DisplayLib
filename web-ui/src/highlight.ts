// Подсветка синтаксиса Jumper (.jmc / .jmp) без зависимостей: токены → HTML со span.tok-*.
// Токены: комментарии, строки (с §-кодами и escape), числа, ключевые слова, типы, имена функций
// (идентификатор перед «(»), ключи таблиц (идентификатор перед «:»), глобалы API, скобки, операторы.
const KEYWORDS = new Set(['if', 'else', 'for', 'while', 'do', 'return', 'break', 'continue', 'import', 'new', 'switch', 'case', 'default', 'try', 'catch', 'finally', 'throw', 'in', 'instanceof']);
const TYPES = new Set(['String', 'int', 'long', 'double', 'float', 'boolean', 'void', 'dyn', 'var', 'char', 'byte', 'short']);
const LITERALS = new Set(['true', 'false', 'null']);
const GLOBALS = new Set(['player', 'screen', 'storage', 'timer', 'log', 'widget', 'Policy']);

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const span = (cls: string, s: string) => `<span class="tok-${cls}">${esc(s)}</span>`;

export function highlightJumper(src: string): string {
  let out = '';
  let i = 0;
  const n = src.length;
  while (i < n) {
    const ch = src[i];
    // комментарии
    if (ch === '/' && src[i + 1] === '/') { const j = src.indexOf('\n', i); const e = j < 0 ? n : j; out += span('comment', src.slice(i, e)); i = e; continue; }
    if (ch === '/' && src[i + 1] === '*') { const j = src.indexOf('*/', i + 2); const e = j < 0 ? n : j + 2; out += span('comment', src.slice(i, e)); i = e; continue; }
    // строки
    if (ch === '"' || ch === "'") {
      let j = i + 1;
      while (j < n && src[j] !== ch) { if (src[j] === '\\') j++; if (src[j] === '\n') break; j++; }
      const e = Math.min(n, j + 1);
      const body = src.slice(i, e);
      // §-коды и escape внутри строки
      out += '<span class="tok-string">' + esc(body).replace(/(§[0-9a-fk-or])|(\\.)/gi, (m) => `<span class="tok-escape">${m}</span>`) + '</span>';
      i = e; continue;
    }
    // числа
    if (/[0-9]/.test(ch) || (ch === '-' && /[0-9]/.test(src[i + 1] ?? '') && !/[\w)\]]/.test(src[i - 1] ?? ''))) {
      const m = /^-?(?:0x[0-9a-f]+|\d+(?:\.\d+)?(?:e[+-]?\d+)?)[lLfFdD]?/i.exec(src.slice(i))!;
      out += span('number', m[0]); i += m[0].length; continue;
    }
    // идентификаторы
    if (/[A-Za-z_$Ѐ-ӿ]/.test(ch)) {
      const m = /^[A-Za-z_$Ѐ-ӿ][\w$Ѐ-ӿ]*/.exec(src.slice(i))!;
      const word = m[0]; const after = src.slice(i + word.length).match(/^\s*(.)/)?.[1];
      const before = src.slice(0, i).match(/(\S)\s*$/)?.[1];
      let cls = 'ident';
      if (KEYWORDS.has(word)) cls = 'keyword';
      else if (TYPES.has(word)) cls = 'type';
      else if (LITERALS.has(word)) cls = 'literal';
      else if (after === '(') cls = 'func';
      else if (after === ':' && before !== '?' ) cls = 'key';
      else if (GLOBALS.has(word) && before !== '.') cls = 'global';
      else if (before === '.') cls = 'member';
      out += cls === 'ident' ? esc(word) : span(cls, word);
      i += word.length; continue;
    }
    if ('{}[]()'.includes(ch)) { out += span('bracket', ch); i++; continue; }
    if ('+-*/%=<>!&|?:;,.'.includes(ch)) { out += span('op', ch); i++; continue; }
    out += esc(ch); i++;
  }
  return out;
}
