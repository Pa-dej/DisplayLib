<script setup lang="ts">
import Icon from './Icon.vue';
import { ref } from 'vue';
import { currentScreen, selectedWidget, currentDoc, state } from '../store';
import ScreenPanel from './ScreenPanel.vue';
import WidgetPanel from './WidgetPanel.vue';
import CalibrationPanel from './CalibrationPanel.vue';
const tab = ref<'props' | 'calib'>('props');
</script>
<template>
  <aside class="inspector">
    <div class="row" style="margin-bottom:12px">
      <span class="tab" :class="{ active: tab === 'props' }" @click="tab = 'props'"><Icon name="sliders-h" /> Свойства</span>
      <span class="tab" :class="{ active: tab === 'calib' }" @click="tab = 'calib'"><Icon name="cog" /> Калибровка</span>
    </div>
    <template v-if="tab === 'calib'"><CalibrationPanel /></template>
    <template v-else-if="!currentScreen"><div class="empty">Нет открытого экрана</div></template>
    <template v-else>
      <div v-if="currentDoc?.warnings.length" class="callout warn"><b>При чтении файла:</b><br />{{ currentDoc.warnings.join('; ') }}</div>
      <WidgetPanel v-if="selectedWidget" :key="selectedWidget.uid" :widget="selectedWidget" />
      <template v-else>
        <ScreenPanel :key="currentDoc?.path" :screen="currentScreen" />
        <div class="hint">Выберите виджет в списке слева или кликом в предпросмотре, чтобы редактировать его. Виджетов: {{ currentScreen.widgets.length }}.</div>
      </template>
      <div v-if="selectedWidget" style="margin-top:8px"><button class="btn sm" @click="state.selected = null">← к настройкам экрана</button></div>
    </template>
  </aside>
</template>
