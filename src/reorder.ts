// Перетаскивание строк списка за «ручку» (HTML5 drag & drop): сегменты текста, кадры анимации.
// Использование: const dnd = useReorder(() => arr, (from, to) => move(from, to));
// на строке: :class="dnd.rowClass(i)" @dragover="dnd.over(i, $event)" @drop="dnd.drop(i, $event)" @dragleave="dnd.leave(i)"
// на ручке: draggable="true" @dragstart="dnd.start(i, $event)" @dragend="dnd.end()"
import { reactive } from 'vue';

export function useReorder(onMove: (from: number, to: number) => void) {
  const st = reactive({ from: -1, over: -1 });
  return {
    st,
    start(i: number, e: DragEvent) {
      st.from = i;
      if (e.dataTransfer) { e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', String(i)); }
      // перетаскивать всю строку, а не только ручку
      const row = (e.target as HTMLElement).closest('[data-row]') as HTMLElement | null;
      if (row && e.dataTransfer) e.dataTransfer.setDragImage(row, 10, 10);
    },
    over(i: number, e: DragEvent) {
      if (st.from < 0) return;
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'move';
      st.over = i;
    },
    leave(i: number) { if (st.over === i) st.over = -1; },
    drop(i: number, e: DragEvent) {
      e.preventDefault();
      const from = st.from;
      st.from = -1; st.over = -1;
      if (from < 0 || from === i) return;
      onMove(from, i);
    },
    end() { st.from = -1; st.over = -1; },
    rowClass(i: number) { return { dragging: st.from === i, 'drop-before': st.over === i && st.from > i, 'drop-after': st.over === i && st.from >= 0 && st.from < i }; },
  };
}

/** Переставить элемент массива с from на to (на месте). */
export function moveItem<T>(arr: T[], from: number, to: number): void {
  if (from === to || from < 0 || to < 0 || from >= arr.length || to >= arr.length) return;
  const [x] = arr.splice(from, 1);
  arr.splice(to, 0, x);
}
