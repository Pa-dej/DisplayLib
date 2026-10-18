<script setup lang="ts">
import Icon from '../Icon.vue';
import { pstate, addEffect, closeEffect, duplicateEffect, isDirty, EXAMPLES, openExample } from '../../particles/store';
import type { RenderType } from '../../particles/model';
const KIND: Record<RenderType, string> = { PIXEL: 'PX', TEXT: 'T', ITEM: 'I', BLOCK: 'B' };
</script>

<template>
  <aside class="sidebar">
    <div class="nav-group">
      <div class="nav-title"><span>Эффекты</span>
        <span class="btn-group"><button class="btn sm" title="Новый эффект" @click="addEffect()"><Icon name="plus" /></button></span>
      </div>
      <div v-for="d in pstate.docs" :key="d.key" class="nav-item" :class="{ active: d.key === pstate.current }" :title="'particles/' + d.effect.id + '.jmc'" @click="pstate.current = d.key">
        <span class="kind">{{ KIND[d.effect.render.type] }}</span>
        <span class="name">{{ d.effect.id }}</span>
        <span v-if="isDirty(d)" class="dot" title="Не скачан"></span>
        <template v-if="d.key === pstate.current">
          <button class="btn sm icon" title="Дублировать" @click.stop="duplicateEffect(d.key)"><Icon name="clone" /></button>
        </template>
        <button class="btn sm icon" title="Убрать из редактора" @click.stop="closeEffect(d.key)"><Icon name="times" /></button>
      </div>
      <div v-if="!pstate.docs.length" class="empty">Создайте эффект, импортируйте .jmc или откройте пример ниже</div>
    </div>

    <div class="nav-group">
      <div class="nav-title"><span>Примеры плагина</span><span class="badge">{{ EXAMPLES.length }}</span></div>
      <div v-for="x in EXAMPLES" :key="x.id" class="nav-item example" :title="x.title + '\n\nКлик - открыть копию (эффект с тем же id заменяется)'" @click="openExample(x.id)">
        <span class="kind"><Icon name="magic" :size="10" /></span>
        <span class="name">{{ x.id }}</span>
      </div>
      <p class="hint" style="margin:6px 8px">Те же файлы, что плагин кладёт в <code>examples/particles</code>.</p>
    </div>
  </aside>
</template>
