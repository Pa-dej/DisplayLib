<script setup lang="ts">
import Icon from './Icon.vue';
import { state, currentScreen, addWidget, removeWidget, duplicateWidget, moveWidgetOrder, closeDoc, toggleHidden, toggleLocked } from '../store';
import { richToPlain } from '../model/screen';

function select(path: string) { state.current = path; }
function label(w: { id: string; type: string; text: any; material: string; sprite: string }) {
  return w.id || (w.type === 'TEXT_BUTTON' ? richToPlain(w.text) : w.type === 'SPRITE_BUTTON' ? w.sprite : w.material) || '(без id)';
}
</script>

<template>
  <aside class="sidebar">
    <div class="nav-group">
      <div class="nav-title"><span>Экраны</span><span class="badge">{{ state.docs.length }}</span></div>
      <div v-for="d in state.docs" :key="d.path" class="nav-item" :class="{ active: d.path === state.current }" @click="select(d.path)" :title="'screens/' + d.path">
        <span class="kind">{{ d.screen.screenType === 'PUBLIC' ? 'PUB' : 'PRV' }}</span>
        <span class="name">{{ d.screen.id }}</span>
        <span v-if="d.dirty" class="dot" title="Не сохранено"></span>
        <button v-if="d.savedText === null" class="btn sm icon" title="Закрыть без сохранения" @click.stop="closeDoc(d.path)"><Icon name="times" /></button>
      </div>
      <div v-if="!state.docs.length" class="empty">Откройте папку плагина или создайте экран</div>
    </div>

    <div class="nav-group" v-if="currentScreen">
      <div class="nav-title">
        <span>Виджеты</span>
        <span class="btn-group">
          <button class="btn sm" @click="addWidget('TEXT_BUTTON')" title="Текстовая кнопка"><Icon name="font" /></button>
          <button class="btn sm" @click="addWidget('ITEM_BUTTON')" title="Предмет"><Icon name="cube" /></button>
          <button class="btn sm" @click="addWidget('SPRITE_BUTTON')" title="Спрайт атласа (1.21.9+)"><Icon name="icons" /></button>
        </span>
      </div>
      <div v-for="(w, i) in currentScreen.widgets" :key="w.uid" class="nav-item" :class="{ active: w.uid === state.selected, hidden: w.hidden }" @click="state.selected = w.uid" @mouseenter="state.hovered = w.uid" @mouseleave="state.hovered = null">
        <span class="kind">{{ w.type === 'TEXT_BUTTON' ? 'T' : w.type === 'SPRITE_BUTTON' ? 'S' : 'I' }}</span>
        <span class="name">{{ label(w) }}</span>
        <span class="muted" v-if="w.onClick && w.onClick.action !== 'NONE'"><Icon :name="w.onClick.action === 'RUN_SCRIPT' ? 'code' : w.onClick.action === 'SWITCH_SCREEN' ? 'exchange-alt' : 'times'" :size="11" /></span>
        <button class="btn sm icon flag" :class="{ on: w.hidden }" :title="w.hidden ? 'Показать' : 'Скрыть (не рисуется, не участвует в привязках)'" @click.stop="toggleHidden(w.uid)"><Icon :name="w.hidden ? 'eye-slash' : 'eye'" :size="11" /></button>
        <button class="btn sm icon flag" :class="{ on: w.locked }" :title="w.locked ? 'Разблокировать' : 'Заблокировать (не выбирается и не двигается в сцене)'" @click.stop="toggleLocked(w.uid)"><Icon :name="w.locked ? 'lock' : 'lock-open'" :size="11" /></button>
        <template v-if="w.uid === state.selected">
          <button class="btn sm icon" title="Выше" @click.stop="moveWidgetOrder(w.uid, -1)" :disabled="i === 0"><Icon name="arrow-up" /></button>
          <button class="btn sm icon" title="Ниже" @click.stop="moveWidgetOrder(w.uid, 1)" :disabled="i === currentScreen.widgets.length - 1"><Icon name="arrow-down" /></button>
          <button class="btn sm icon" title="Дублировать" @click.stop="duplicateWidget(w.uid)"><Icon name="clone" /></button>
          <button class="btn sm icon danger" title="Удалить" @click.stop="removeWidget(w.uid)"><Icon name="trash" /></button>
        </template>
      </div>
      <div v-if="!currentScreen.widgets.length" class="empty">Виджетов нет. Добавьте текстовую кнопку или предмет.</div>
    </div>

    <div class="nav-group" v-if="state.folder">
      <div class="nav-title"><span>Скрипты</span><span class="badge">{{ state.scripts.length }}</span></div>
      <div v-for="s in state.scripts" :key="s" class="nav-item" :class="{ active: currentScreen?.script === s }" :title="(state.scriptInfo[s]?.functions ?? []).join(', ')">
        <span class="kind">jmp</span><span class="name">{{ s }}</span>
        <span class="muted">{{ state.scriptInfo[s]?.functions.length ?? 0 }}ƒ</span>
      </div>
    </div>
  </aside>
</template>
