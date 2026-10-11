// Файлы уходят из редактора только экспортом: скачивание или копирование. Прямой записи в папку плагина нет.
export function download(name: string, text: string): void {
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

/** Скачать zip с набором текстовых файлов: { "screens/a.jmc": "...", "scripts/a.jmp": "..." }. */
export async function downloadZip(name: string, files: Record<string, string>): Promise<void> {
  const { zipSync, strToU8 } = await import('fflate');
  const data: Record<string, Uint8Array> = {};
  for (const [k, v] of Object.entries(files)) data[k] = strToU8(v);
  const out = zipSync(data, { level: 6 });
  const blob = new Blob([out as BlobPart], { type: 'application/zip' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
