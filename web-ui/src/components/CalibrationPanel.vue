<script setup lang="ts">
import { calibration, resetCalibration } from '../render/metrics';
import { font } from '../render/font';
</script>
<template>
  <div class="section">
    <h3>Калибровка предпросмотра<button class="btn sm" @click="resetCalibration">Сброс</button></h3>
    <div class="hint" style="margin-bottom:8px">Константы отрисовки клиента. Сверьте один раз с игрой на экране <code>jumper_test</code>; значения хранятся в браузере.</div>
    <div class="field"><label>position +x</label><select v-model.number="calibration.positionXSign"><option :value="1">Вправо для игрока</option><option :value="-1">Влево для игрока</option></select></div>
    <div class="field"><label>translation +x</label><select v-model.number="calibration.translationXSign"><option :value="1">Вправо для игрока</option><option :value="-1">Влево для игрока</option></select></div>
    <div class="field"><label>Пиксель, бл.</label><input type="number" step="0.001" v-model.number="calibration.px" /></div>
    <div class="field"><label>Строка, px</label><input type="number" step="1" v-model.number="calibration.lineHeight" /></div>
    <div class="field"><label>Отступ фона</label><div class="vec" style="grid-template-columns:repeat(4,1fr)"><input type="number" step="1" v-model.number="calibration.bgLeft" title="Слева" /><input type="number" step="1" v-model.number="calibration.bgRight" title="Справа" /><input type="number" step="1" v-model.number="calibration.bgTop" title="Сверху" /><input type="number" step="1" v-model.number="calibration.bgBottom" title="Снизу" /></div></div>
    <div class="field"><label>Предмет, бл.</label><input type="number" step="0.025" v-model.number="calibration.itemSize" title="Размер спрайта предмета при scale 1" /></div>
    <div class="field"><label>Глаза над центром</label><input type="number" step="0.01" v-model.number="calibration.eyeAboveCenter" /></div>
    <div class="field"><label>До экрана, бл.</label><input type="number" step="0.1" v-model.number="calibration.eyeDistance" /></div>
    <div class="field"><label>FOV</label><input type="number" step="1" v-model.number="calibration.fov" /></div>
    <div class="callout" :class="font.loaded ? '' : 'warn'">
      <b>Шрифт:</b> {{ font.loaded ? 'Растровый шрифт Minecraft загружен (' + font.glyphs.size + ' глифов)' : 'Используется запасной - размеры примерные' }}.
      <template v-if="!font.loaded"><br />Скопируйте из клиента <code>versions/1.21.x/1.21.x.jar</code> Папки <code>assets/minecraft/font/</code> И <code>assets/minecraft/textures/font/</code> В <code>public/assets/minecraft/</code> (структура как в jar).</template>
      <div v-if="font.problems.length" class="hint">{{ font.problems.join('; ') }}</div>
    </div>
  </div>
</template>
