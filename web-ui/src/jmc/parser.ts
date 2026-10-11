// Разбор конфигов Jumper (.jmc) - то же подмножество языка, что принимает Config.load плагина:
// объявления (dyn/int/long/double/boolean/String), присваивания, if/else, выражения с
// ?: || && == != < <= > >= + - * / %, унарные - и !, скобки, таблицы { k: v }, массивы [a, b]
// и { a, b }, строки с escape, числа (hex, подчёркивания, 1e3), true/false/null, return <value>;
// Результат: таблица переменных верхнего уровня (в порядке объявления) или значение return.

export type JmcValue = null | boolean | number | string | JmcValue[] | JmcTable;
export type JmcTable = { [key: string]: JmcValue };

export class JmcError extends Error {
  constructor(message: string, public line: number, public col: number) {
    super(message + ' (' + line + ':' + col + ')');
  }
}

type Tok =
  | { t: 'num'; v: number; line: number; col: number }
  | { t: 'str'; v: string; line: number; col: number }
  | { t: 'id'; v: string; line: number; col: number }
  | { t: 'op'; v: string; line: number; col: number }
  | { t: 'eof'; v: ''; line: number; col: number };

const KEYWORDS = new Set(['dyn', 'int', 'long', 'double', 'boolean', 'String', 'if', 'else', 'return', 'true', 'false', 'null']);
const OPS = ['>>>', '<<', '>>', '==', '!=', '<=', '>=', '&&', '||', '+', '-', '*', '/', '%', '!', '?', ':', '(', ')', '[', ']', '{', '}', ',', ';', '=', '<', '>', '.'];

function lex(src: string): Tok[] {
  const toks: Tok[] = [];
  let i = 0, line = 1, col = 1;
  const n = src.length;
  const adv = (k = 1) => { for (let j = 0; j < k; j++) { if (src[i] === '\n') { line++; col = 1; } else col++; i++; } };
  while (i < n) {
    const c = src[i];
    if (c === ' ' || c === '\t' || c === '\r' || c === '\n') { adv(); continue; }
    if (c === '/' && src[i + 1] === '/') { while (i < n && src[i] !== '\n') adv(); continue; }
    if (c === '/' && src[i + 1] === '*') { adv(2); while (i < n && !(src[i] === '*' && src[i + 1] === '/')) adv(); adv(2); continue; }
    const sl = line, sc = col;
    if (c === '"' || c === "'") {
      const q = c; adv(); let s = '';
      while (i < n && src[i] !== q) {
        if (src[i] === '\\') {
          adv(); const e = src[i];
          if (e === 'n') s += '\n'; else if (e === 't') s += '\t'; else if (e === 'r') s += '\r';
          else if (e === 'u') { s += String.fromCharCode(parseInt(src.substr(i + 1, 4), 16)); adv(4); }
          else s += e;
          adv();
        } else { s += src[i]; adv(); }
      }
      if (i >= n) throw new JmcError('Unterminated string', sl, sc);
      adv();
      toks.push({ t: 'str', v: s, line: sl, col: sc });
      continue;
    }
    if (/[0-9]/.test(c) || (c === '.' && /[0-9]/.test(src[i + 1] ?? ''))) {
      let j = i;
      if (c === '0' && (src[i + 1] === 'x' || src[i + 1] === 'X')) { j += 2; while (/[0-9a-fA-F_]/.test(src[j] ?? '')) j++; }
      else { while (/[0-9_]/.test(src[j] ?? '')) j++; if (src[j] === '.' && /[0-9]/.test(src[j + 1] ?? '')) { j++; while (/[0-9_]/.test(src[j] ?? '')) j++; } if (src[j] === 'e' || src[j] === 'E') { j++; if (src[j] === '+' || src[j] === '-') j++; while (/[0-9]/.test(src[j] ?? '')) j++; } }
      let text = src.slice(i, j).replace(/_/g, '');
      if (src[j] === 'L' || src[j] === 'l' || src[j] === 'f' || src[j] === 'F' || src[j] === 'd' || src[j] === 'D') j++;
      const v = text.startsWith('0x') || text.startsWith('0X') ? parseInt(text, 16) : Number(text);
      adv(j - i);
      toks.push({ t: 'num', v, line: sl, col: sc });
      continue;
    }
    if (/[A-Za-z_$Ѐ-ӿ]/.test(c)) {
      let j = i; while (/[A-Za-z0-9_$Ѐ-ӿ]/.test(src[j] ?? '')) j++;
      const v = src.slice(i, j); adv(j - i);
      toks.push({ t: 'id', v, line: sl, col: sc });
      continue;
    }
    const op = OPS.find((o) => src.startsWith(o, i));
    if (!op) throw new JmcError("Unexpected character '" + c + "'", sl, sc);
    adv(op.length);
    toks.push({ t: 'op', v: op, line: sl, col: sc });
  }
  toks.push({ t: 'eof', v: '', line, col });
  return toks;
}

class Parser {
  private p = 0;
  private vars = new Map<string, JmcValue>();
  private order: string[] = [];
  private exec = true;

  constructor(private toks: Tok[]) {}

  private peek(k = 0): Tok { return this.toks[Math.min(this.p + k, this.toks.length - 1)]; }
  private next(): Tok { return this.toks[this.p++]; }
  private isOp(v: string, k = 0): boolean { const t = this.peek(k); return t.t === 'op' && t.v === v; }
  private isId(v: string, k = 0): boolean { const t = this.peek(k); return t.t === 'id' && t.v === v; }
  private expectOp(v: string): void { const t = this.next(); if (t.t !== 'op' || t.v !== v) throw new JmcError("Expected '" + v + "'", t.line, t.col); }
  private err(msg: string, t: Tok = this.peek()): never { throw new JmcError(msg, t.line, t.col); }

  program(): JmcValue {
    while (this.peek().t !== 'eof') {
      if (this.isId('return')) {
        this.next();
        const v = this.expr();
        if (this.isOp(';')) this.next();
        if (this.exec) return v;
        continue;
      }
      this.statement();
    }
    const t: JmcTable = {};
    for (const k of this.order) t[k] = this.vars.get(k) ?? null;
    return t;
  }

  private block(): void {
    if (this.isOp('{')) {
      this.next();
      while (!this.isOp('}')) { if (this.peek().t === 'eof') this.err("Expected '}'"); this.statement(); }
      this.next();
    } else this.statement();
  }

  private statement(): void {
    const t = this.peek();
    if (t.t === 'id' && t.v === 'if') {
      this.next(); this.expectOp('(');
      const c = this.expr(); this.expectOp(')');
      const saved = this.exec;
      const truth = c !== null && c !== false;
      this.exec = saved && truth;
      this.block();
      if (this.isId('else')) { this.next(); this.exec = saved && !truth; this.block(); }
      this.exec = saved;
      return;
    }
    if (t.t === 'id' && (t.v === 'dyn' || t.v === 'int' || t.v === 'long' || t.v === 'double' || t.v === 'boolean' || t.v === 'String')) {
      this.next();
      for (;;) {
        const name = this.next();
        if (name.t !== 'id' || KEYWORDS.has(name.v)) this.err('Expected a variable name', name);
        let v: JmcValue = t.v === 'int' || t.v === 'long' || t.v === 'double' ? 0 : t.v === 'boolean' ? false : null;
        if (this.isOp('=')) { this.next(); v = this.expr(); }
        if (this.exec) { if (!this.vars.has(name.v)) this.order.push(name.v); this.vars.set(name.v, v); }
        if (this.isOp(',')) { this.next(); continue; }
        break;
      }
      this.expectOp(';');
      return;
    }
    if (t.t === 'id' && this.isOp('=', 1)) {
      this.next(); this.next();
      const v = this.expr();
      this.expectOp(';');
      if (this.exec) { if (!this.vars.has(t.v)) this.err("Unknown variable '" + t.v + "'", t); this.vars.set(t.v, v); }
      return;
    }
    if (t.t === 'id' && this.isOp('[', 1) || t.t === 'id' && this.isOp('.', 1)) {
      // t.k = v / t[k] = v - присваивание в таблицу (в конфиге встречается редко, но допустимо)
      this.next();
      let target = this.vars.get(t.v) as JmcValue;
      const path: JmcValue[] = [];
      while (this.isOp('.') || this.isOp('[')) {
        if (this.isOp('.')) { this.next(); const k = this.next(); path.push(k.v); }
        else { this.next(); path.push(this.expr()); this.expectOp(']'); }
      }
      this.expectOp('='); const v = this.expr(); this.expectOp(';');
      if (this.exec) {
        for (let i = 0; i < path.length - 1; i++) target = (target as any)[path[i] as any];
        if (target && typeof target === 'object') (target as any)[path[path.length - 1] as any] = v;
      }
      return;
    }
    // выражение-оператор
    this.expr();
    this.expectOp(';');
  }

  private expr(): JmcValue { return this.ternary(); }

  private ternary(): JmcValue {
    const c = this.or();
    if (this.isOp('?')) {
      this.next();
      const a = this.or(); this.expectOp(':'); const b = this.ternary();
      return c !== null && c !== false ? a : b;
    }
    return c;
  }
  private or(): JmcValue { let l = this.and(); while (this.isOp('||')) { this.next(); const r = this.and(); l = l !== null && l !== false ? l : r; } return l; }
  private and(): JmcValue { let l = this.eq(); while (this.isOp('&&')) { this.next(); const r = this.eq(); l = l !== null && l !== false ? r : l; } return l; }
  private eq(): JmcValue { let l = this.cmp(); for (;;) { if (this.isOp('==')) { this.next(); l = eq(l, this.cmp()); } else if (this.isOp('!=')) { this.next(); l = !eq(l, this.cmp()); } else return l; } }
  private cmp(): JmcValue {
    let l = this.add();
    for (;;) {
      const o = this.peek();
      if (o.t === 'op' && (o.v === '<' || o.v === '<=' || o.v === '>' || o.v === '>=')) {
        this.next(); const r = this.add();
        const a = l as any, b = r as any;
        l = o.v === '<' ? a < b : o.v === '<=' ? a <= b : o.v === '>' ? a > b : a >= b;
      } else return l;
    }
  }
  private add(): JmcValue {
    let l = this.mul();
    for (;;) {
      if (this.isOp('+')) { this.next(); const r = this.mul(); l = typeof l === 'string' || typeof r === 'string' ? str(l) + str(r) : (l as number) + (r as number); }
      else if (this.isOp('-')) { this.next(); l = (l as number) - (this.mul() as number); }
      else return l;
    }
  }
  private mul(): JmcValue {
    let l = this.unary();
    for (;;) {
      if (this.isOp('*')) { this.next(); l = (l as number) * (this.unary() as number); }
      else if (this.isOp('/')) { this.next(); const r = this.unary() as number; const a = l as number; l = Number.isInteger(a) && Number.isInteger(r) ? Math.trunc(a / r) : a / r; }
      else if (this.isOp('%')) { this.next(); l = (l as number) % (this.unary() as number); }
      else return l;
    }
  }
  private unary(): JmcValue {
    if (this.isOp('-')) { this.next(); return -(this.unary() as number); }
    if (this.isOp('!')) { this.next(); const v = this.unary(); return v === null || v === false; }
    if (this.isOp('+')) { this.next(); return this.unary(); }
    return this.postfix();
  }
  private postfix(): JmcValue {
    let v = this.primary();
    for (;;) {
      if (this.isOp('[')) { this.next(); const k = this.expr(); this.expectOp(']'); v = index(v, k); }
      else if (this.isOp('.') && this.peek(1).t === 'id') { this.next(); const k = this.next().v; if (k === 'length' && Array.isArray(v)) v = v.length; else v = index(v, k); }
      else return v;
    }
  }
  private primary(): JmcValue {
    const t = this.next();
    if (t.t === 'num') return t.v;
    if (t.t === 'str') return t.v;
    if (t.t === 'id') {
      if (t.v === 'true') return true;
      if (t.v === 'false') return false;
      if (t.v === 'null') return null;
      if (KEYWORDS.has(t.v)) this.err("Unexpected '" + t.v + "'", t);
      if (this.isOp('(')) {
        // вызов функции: в конфиге доступны только len/str/int/double (как встроенные Jumper)
        this.next(); const args: JmcValue[] = [];
        while (!this.isOp(')')) { args.push(this.expr()); if (this.isOp(',')) this.next(); }
        this.next();
        return builtin(t.v, args, t);
      }
      if (!this.vars.has(t.v)) this.err("Unknown name '" + t.v + "'", t);
      return this.vars.get(t.v) ?? null;
    }
    if (t.t === 'op') {
      if (t.v === '(') { const v = this.expr(); this.expectOp(')'); return v; }
      if (t.v === '[') { const a: JmcValue[] = []; while (!this.isOp(']')) { a.push(this.expr()); if (this.isOp(',')) this.next(); else if (!this.isOp(']')) this.err("Expected ',' or ']'"); } this.next(); return a; }
      if (t.v === '{') {
        // таблица { k: v } или массив { a, b }
        if (this.isOp('}')) { this.next(); return {}; }
        const isTable = (this.peek().t === 'id' || this.peek().t === 'str' || this.peek().t === 'num') && this.isOp(':', 1);
        if (isTable) {
          const tbl: JmcTable = {};
          while (!this.isOp('}')) {
            const k = this.next(); this.expectOp(':');
            tbl[String(k.v)] = this.expr();
            if (this.isOp(',')) this.next(); else if (!this.isOp('}')) this.err("Expected ',' or '}'");
          }
          this.next();
          return tbl;
        }
        const a: JmcValue[] = [];
        while (!this.isOp('}')) { a.push(this.expr()); if (this.isOp(',')) this.next(); else if (!this.isOp('}')) this.err("Expected ',' or '}'"); }
        this.next();
        return a;
      }
    }
    this.err('Unexpected token ' + (t.v || t.t), t);
  }
}

function eq(a: JmcValue, b: JmcValue): boolean {
  if (typeof a === 'number' && typeof b === 'number') return a === b;
  if (typeof a === 'string' && typeof b === 'string') return a === b;
  return a === b;
}
function str(v: JmcValue): string {
  if (v === null) return 'null';
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : String(v);
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}
function index(v: JmcValue, k: JmcValue): JmcValue {
  if (Array.isArray(v)) return v[k as number] ?? null;
  if (v && typeof v === 'object') return (v as JmcTable)[String(k)] ?? null;
  return null;
}
function builtin(name: string, args: JmcValue[], t: Tok): JmcValue {
  const a = args[0] ?? null;
  switch (name) {
    case 'len': return Array.isArray(a) ? a.length : typeof a === 'string' ? a.length : a && typeof a === 'object' ? Object.keys(a).length : 0;
    case 'str': return str(a);
    case 'int': return typeof a === 'number' ? Math.trunc(a) : typeof a === 'string' ? (Number.isNaN(parseInt(a, 10)) ? null : parseInt(a, 10)) : null;
    case 'double': return typeof a === 'number' ? a : typeof a === 'string' ? (Number.isNaN(Number(a)) ? null : Number(a)) : null;
    default: throw new JmcError("Function '" + name + "' is not available in a config", t.line, t.col);
  }
}

/** Разобрать текст .jmc: таблица переменных верхнего уровня или значение return. */
export function parseJmc(source: string): JmcValue {
  return new Parser(lex(source)).program();
}
