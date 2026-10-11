import { describe, it, expect } from 'vitest';
import { scanScript } from '../src/jmp/scan';

describe('scanScript', () => {
  it('функции, хуки, импорты, пустые тела', () => {
    const info = scanScript(`import "lib/util.jmp";
void onOpen() { log.info("x"); }
void buy(dyn w, dyn p) {
}
void sell(dyn w, dyn p) { // комментарий
  ;
}
dyn helper(int a) { return a; }
`);
    expect(info.hooks).toEqual(['onOpen']);
    expect(info.functions).toEqual(['buy', 'sell', 'helper']);
    expect(info.emptyFunctions).toEqual(['buy', 'sell']);
    expect(info.imports).toEqual(['lib/util.jmp']);
  });
});
