// Плавные переключатели по всему редактору: под выбранной кнопкой сегмента (.seg > .on) едет подложка,
// под активной вкладкой (.tab.active) - полоска. Ничего не нужно размечать в шаблонах: наблюдатель DOM
// находит такие группы сам и выставляет им позицию активного элемента в CSS-переменные.

const pending = new Set<HTMLElement>();
let raf = 0;

function place(el: HTMLElement, kind: 'seg' | 'tabs'): void {
  const active = el.querySelector<HTMLElement>(kind === 'seg' ? ':scope > .on' : ':scope > .tab.active');
  const cls = kind === 'seg' ? 'slide' : 'tabs-slide';
  if (!active || !active.offsetWidth) { el.classList.remove('slide-on'); return; }
  const first = !el.classList.contains(cls);
  if (first) el.classList.add(cls, 'slide-init');
  el.style.setProperty('--sx', active.offsetLeft + 'px');
  el.style.setProperty('--sy', active.offsetTop + 'px');
  el.style.setProperty('--sw', active.offsetWidth + 'px');
  el.style.setProperty('--sh', active.offsetHeight + 'px');
  // первое появление - без анимации (подложка не должна выезжать из угла)
  const wasOff = !el.classList.contains('slide-on');
  el.classList.add('slide-on');
  if (first || wasOff) { el.classList.add('slide-init'); requestAnimationFrame(() => requestAnimationFrame(() => el.classList.remove('slide-init'))); }
}

function flush(): void {
  raf = 0;
  const all = pending.size ? [...pending] : [];
  pending.clear();
  for (const el of all) if (el.isConnected) place(el, el.classList.contains('seg') ? 'seg' : 'tabs');
}
function queue(el: HTMLElement | null): void {
  if (!el) return;
  pending.add(el);
  if (!raf) raf = requestAnimationFrame(flush);
}
function scan(root: ParentNode): void {
  root.querySelectorAll<HTMLElement>('.seg').forEach(queue);
  root.querySelectorAll<HTMLElement>('.tab').forEach((t) => queue(t.parentElement));
}

export function installSlide(): void {
  scan(document);
  new MutationObserver((list) => {
    for (const m of list) {
      const t = m.target as HTMLElement;
      if (m.type === 'attributes') {
        if (t.classList?.contains('on') || t.classList?.contains('active') || m.oldValue?.match(/\b(on|active)\b/)) {
          const p = t.parentElement;
          if (p && (p.classList.contains('seg') || t.classList.contains('tab'))) queue(p);
        }
      } else {
        for (const n of m.addedNodes) if (n instanceof HTMLElement) {
          if (n.classList.contains('seg')) queue(n);
          else if (n.classList.contains('tab')) queue(n.parentElement);
          else scan(n);
        }
        if (t.classList?.contains('seg') || t.querySelector?.(':scope > .tab')) queue(t);
      }
    }
  }).observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'], attributeOldValue: true });
  // размеры меняются (сплиттеры, окно) - пересчитать все
  const ro = new ResizeObserver(() => scan(document));
  ro.observe(document.body);
  window.addEventListener('resize', () => scan(document));
  document.addEventListener('pointerup', () => setTimeout(() => scan(document), 0)); // конец перетаскивания сплиттера
}
