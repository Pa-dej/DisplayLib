// Голова игрока в тексте (объект player, клиент 1.21.9+): квадрат 8×8, как спрайт. Текстуру клиент берёт по нику;
// в превью лицо грузится с mc-heads.net (CORS), пока грузится или без сети - заглушка «Стив».
import { spriteState } from './sprites';

const cache = new Map<string, { img: HTMLImageElement | null; done: boolean }>();

function avatar(name: string, hat: boolean): HTMLImageElement | null {
  const key = name.toLowerCase() + (hat ? '' : '|nohelm');
  let e = cache.get(key);
  if (!e) {
    e = { img: null, done: false }; cache.set(key, e);
    const img = new Image(); img.crossOrigin = 'anonymous';
    const entry = e;
    img.onload = () => { entry.img = img; entry.done = true; spriteState.revision++; };
    img.onerror = () => { entry.done = true; spriteState.revision++; };
    img.src = 'https://mc-heads.net/avatar/' + encodeURIComponent(name.trim()) + '/8' + (hat ? '' : '/nohelm');
  }
  return e.img;
}

let steve: HTMLCanvasElement | null = null;
/** Лицо-заглушка 8×8 в духе Стива. */
function steveFace(): HTMLCanvasElement {
  if (steve) return steve;
  const c = document.createElement('canvas'); c.width = 8; c.height = 8;
  const g = c.getContext('2d')!;
  const px = (x: number, y: number, w: number, h: number, col: string) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
  px(0, 0, 8, 8, '#b48a65'); px(0, 0, 8, 2, '#3b2a1a'); px(0, 2, 1, 1, '#3b2a1a'); px(7, 2, 1, 1, '#3b2a1a');
  px(1, 4, 2, 1, '#ffffff'); px(5, 4, 2, 1, '#ffffff'); px(2, 4, 1, 1, '#4a3cb5'); px(5, 4, 1, 1, '#4a3cb5');
  px(3, 5, 2, 1, '#8a5a3c'); px(3, 6, 2, 1, '#8a5a3c');
  steve = c; return c;
}

/** Нарисовать голову в текстовых координатах (x, y - левый верхний угол, S - px canvas на текстовый px). */
export function drawHead(ctx: CanvasRenderingContext2D, name: string, hat: boolean, x: number, y: number, S: number): void {
  const img = avatar(name, hat);
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(img ?? steveFace(), x * S, y * S, 8 * S, 8 * S);
}
