<template>
  <div>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
      title="高级创意：企业产品图片 + 企业产品描述，适用于华表位（PC 搜索右侧单图+问答）展现。公开资料确认存在该概念；Phoebe 内部“创意模块”的实际职责未知，此处为可选模块。" />
    <div class="pb-toolbar">
      <el-button type="primary" icon="Plus" @click="router.push({ path: '/creatives/new', query: unitId ? { unitId } : {} })">新建高级创意</el-button>
      <el-dropdown :disabled="!sel.length" @command="bulk">
        <el-button :disabled="!sel.length">编辑操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="ACTIVE">启用</el-dropdown-item>
            <el-dropdown-item command="PAUSED">暂停</el-dropdown-item>
            <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <el-table :data="list" border v-loading="loading" @selection-change="sel = $event">
      <el-table-column type="selection" width="38" />
      <el-table-column label="企业产品图片" width="110">
        <template #default="{ row }"><img v-if="row.imageUrls?.[0]" :src="row.imageUrls[0]" class="thumb" /></template>
      </el-table-column>
      <el-table-column label="创意名称" min-width="140">
        <template #default="{ row }"><a @click="router.push(`/creatives/${row.id}/edit`)">{{ row.title }}</a></template>
      </el-table-column>
      <el-table-column label="企业产品描述" prop="description" min-width="220" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tooltip :disabled="!row.rejectReason" :content="row.rejectReason">
            <span :class="['st', row.status === 'PAUSED' ? 'st-gray' : AUDIT[row.auditStatus].cls]">{{ row.status === 'PAUSED' ? '暂停' : AUDIT[row.auditStatus].text }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="关联问答" width="80"><template #default="{ row }">{{ row.qaIds?.length || 0 }}</template></el-table-column>
      <el-table-column label="推广单元" min-width="110"><template #default="{ row }">{{ unitName(row.unitId) }}</template></el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/creatives/${row.id}/edit`)">编辑</el-button>
          <el-popconfirm title="删除该创意？" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api'
import { AUDIT } from '../../labels'
import { batch } from './stats'

const props = defineProps({ planId: Number, unitId: Number, range: Array, plans: Array, units: Array })
const emit = defineEmits(['changed', 'stats'])
const router = useRouter()
const list = ref([])
const loading = ref(false)
const sel = ref([])
const unitName = id => props.units?.find(u => u.unit.id === id)?.unit.name || '#' + id

async function load() {
  loading.value = true
  try { list.value = await http.get('/creatives', { params: { planId: props.planId || undefined, unitId: props.unitId || undefined } }) } finally { loading.value = false }
  emit('stats', null)
}
async function remove(row) { await http.delete(`/creatives/${row.id}`); load() }
async function bulk(cmd) {
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除选中的 ${sel.value.length} 个创意？`, '删除', { type: 'warning' })
    await batch(sel.value, r => http.delete(`/creatives/${r.id}`))
  } else await batch(sel.value, r => http.put(`/creatives/${r.id}/status`, { status: cmd }))
  ElMessage.success('操作完成'); load()
}
watch(() => [props.planId, props.unitId], load)
onMounted(load)
</script>

<style scoped>
.thumb { width: 72px; height: 54px; object-fit: cover; border: 1px solid #eee; }
</style>
