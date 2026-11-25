<template>
  <!-- 投放时段：周一~周日 × 0~23 点，点击或按住拖动选择；蓝色为投放 -->
  <div class="sg" @mouseleave="dragging = false" @mouseup="dragging = false">
    <div class="presets">
      <el-button size="small" @click="preset('all')">全部时段</el-button>
      <el-button size="small" @click="preset('workday')">工作日 9:00-18:00</el-button>
      <el-button size="small" @click="preset('daytime')">每天 8:00-22:00</el-button>
      <el-button size="small" @click="preset('none')">清空</el-button>
      <span class="muted">已选 {{ count }} 小时 / 168 · 点击或拖动选择</span>
    </div>
    <table>
      <thead>
        <tr><th></th><th v-for="h in 24" :key="h" class="h">{{ h - 1 }}</th></tr>
      </thead>
      <tbody>
        <tr v-for="(d, di) in DAYS" :key="d">
          <th class="d" @click="toggleRow(di)">{{ d }}</th>
          <td v-for="h in 24" :key="h" :class="{ on: bits[di * 24 + h - 1] === '1' }"
            @mousedown.prevent="start(di * 24 + h - 1)" @mouseenter="over(di * 24 + h - 1)" />
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({ modelValue: String })
const emit = defineEmits(['update:modelValue'])
const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const bits = computed(() => (props.modelValue && props.modelValue.length === 168 ? props.modelValue : '1'.repeat(168)))
const count = computed(() => [...bits.value].filter(c => c === '1').length)
const dragging = ref(false)
let paint = '1'

function set(arr) { emit('update:modelValue', arr.join('')) }
function start(i) {
  const arr = [...bits.value]
  paint = arr[i] === '1' ? '0' : '1'
  arr[i] = paint
  dragging.value = true
  set(arr)
}
function over(i) {
  if (!dragging.value) return
  const arr = [...bits.value]
  arr[i] = paint
  set(arr)
}
function toggleRow(d) {
  const arr = [...bits.value]
  const allOn = arr.slice(d * 24, d * 24 + 24).every(c => c === '1')
  for (let h = 0; h < 24; h++) arr[d * 24 + h] = allOn ? '0' : '1'
  set(arr)
}
function preset(kind) {
  const arr = []
  for (let d = 0; d < 7; d++) for (let h = 0; h < 24; h++) {
    arr.push(kind === 'all' ? '1' : kind === 'none' ? '0' : kind === 'workday' ? (d < 5 && h >= 9 && h < 18 ? '1' : '0') : (h >= 8 && h < 22 ? '1' : '0'))
  }
  set(arr)
}
</script>

<style scoped>
.sg { user-select: none; }
.presets { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.presets .muted { margin-left: 8px; }
table { border-collapse: collapse; font-size: 11px; }
th { font-weight: normal; color: #999; padding: 0 4px; }
th.h { width: 22px; text-align: center; }
th.d { cursor: pointer; white-space: nowrap; color: #666; }
th.d:hover { color: #3385ff; }
td { width: 22px; height: 20px; border: 1px solid #e4e7ed; background: #fff; cursor: pointer; }
td.on { background: #3385ff; border-color: #2a6ad6; }
td:hover { outline: 1px solid #2a6ad6; }
</style>
