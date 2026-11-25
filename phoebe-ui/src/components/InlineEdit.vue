<template>
  <!-- 表格内联编辑：数值/文本后跟铅笔图标，点开浮层修改（参考公开截图中问答列表的“出价 4.00 ✎”） -->
  <span class="inline-edit">
    <slot>{{ display }}</slot>
    <el-popover v-model:visible="open" trigger="click" :width="type === 'number' ? 220 : 300" placement="bottom-start">
      <template #reference><el-icon class="edit-icon"><EditPen /></el-icon></template>
      <div class="pop">
        <el-input-number v-if="type === 'number'" v-model="val" :min="min" :max="max" :step="step" :precision="precision" :value-on-clear="null" style="width:100%" />
        <el-input v-else v-model="val" :maxlength="maxlength" />
        <div v-if="hint" class="muted" style="margin-top:4px">{{ hint }}</div>
        <div class="btns">
          <el-button @click="open = false">取消</el-button>
          <el-button type="primary" @click="save">确定</el-button>
        </div>
      </div>
    </el-popover>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  value: [String, Number], type: { type: String, default: 'text' }, min: Number, max: Number,
  step: { type: Number, default: 0.1 }, precision: { type: Number, default: 2 }, maxlength: Number, hint: String
})
const emit = defineEmits(['save'])
const open = ref(false)
const val = ref(props.value)
const display = computed(() => props.value ?? '—')
watch(open, o => { if (o) val.value = props.value })
function save() { emit('save', val.value); open.value = false }
</script>

<style scoped>
.inline-edit { white-space: nowrap; }
.btns { display: flex; justify-content: flex-end; gap: 6px; margin-top: 10px; }
</style>
