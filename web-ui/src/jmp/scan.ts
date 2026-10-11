// Имена функций верхнего уровня в скрипте .jmp - для выпадающего списка onClick.
// Ищем объявления вида `void name(`, `dyn name(`, `int name(` ... в начале строки (без отступа:
// функции внутри блоков тоже начинаются с типа, поэтому отступ не учитываем, но вложенные
// функции в обработчики не попадают - достаточно для подсказки).

const HOOKS = new Set(['onOpen', 'onClose']);

export interface ScriptInfo {
  functions: string[];
  hooks: string[];
  imports: string[];
  /** Функции с пустым телом ({}, только ; или комментарии) - плагин считает такой клик «ничего». */
  emptyFunctions: string[];
}

export function scanScript(source: string): ScriptInfo {
  const functions: string[] = [];
  const hooks: string[] = [];
  const imports: string[] = [];
  const emptyFunctions: string[] = [];
  const re = /^[ \t]*(?:void|dyn|int|long|double|boolean|String|[A-Z][A-Za-z0-9_]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*\{/gm;
  let m: RegExpExecArray | null;
  const seen = new Set<string>();
  while ((m = re.exec(source))) {
    const name = m[1];
    if (name === 'if' || name === 'while' || name === 'for' || name === 'switch' || name === 'catch') continue;
    if (seen.has(name)) continue;
    seen.add(name);
    if (HOOKS.has(name)) hooks.push(name); else functions.push(name);
    // тело до парной скобки: пустое, если там только пробелы, ; и комментарии
    let depth = 1, i = re.lastIndex;
    for (; i < source.length && depth > 0; i++) { const ch = source[i]; if (ch === '{') depth++; else if (ch === '}') depth--; }
    const body = source.slice(re.lastIndex, i - 1).replace(/\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');
    if (!/[^\s;{}]/.test(body)) emptyFunctions.push(name);
  }
  const ir = /^\s*import\s+"([^"]+)"\s*;/gm;
  while ((m = ir.exec(source))) imports.push(m[1]);
  return { functions, hooks, imports, emptyFunctions };
}

/** Заготовка скрипта для нового экрана с обработчиками из его виджетов. */
export function scaffoldScript(screenId: string, handlers: string[], isPublic: boolean): string {
  const L: string[] = [];
  L.push(`// ${screenId}: скрипт экрана DisplayLib (Jumper, .jmp)`);
  L.push('');
  L.push('void onOpen() {');
  L.push(isPublic ? '    log.info("' + screenId + ' открыт");' : '    player.message("§7Экран ' + screenId + ' открыт");');
  L.push('}');
  L.push('');
  for (const h of handlers) {
    L.push(`void ${h}(dyn widget, dyn player) {`);
    L.push(`    player.message("§e${h}: " + widget.id());`);
    L.push('}');
    L.push('');
  }
  L.push('void onClose() {');
  L.push(`    log.info("${screenId} закрыт");`);
  L.push('}');
  L.push('');
  return L.join('\n');
}
