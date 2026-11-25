<template>
  <div class="pb-page">
    <div class="pb-crumb"><router-link to="/tools">工具中心</router-link> › <b>出价助手</b></div>
    <div class="pb-panel">
      <p class="muted" style="margin-top:0">对有效问答给出排到第一位所需的建议出价（模拟算法：最强竞争者综合得分 / 自身质量度 + 0.01；移动端再除以移动出价比例）。</p>
      <el-table :data="qas" border>
        <el-table-column prop="question" label="问题" min-width="240" />
        <el-table-column label="当前出价" width="100"><template #default="{ row }">{{ row.bid ?? '单元出价' }}</template></el-table-column>
        <el-table-column label="质量度" width="80"><template #default="{ row }">{{ res[row.id]?.quality ?? '—' }}</template></el-table-column>
        <el-table-column label="计算机建议出价" width="130"><template #default="{ row }"><b v-if="res[row.id]">¥{{ res[row.id].pc.suggestedBid }}</b></template></el-table-column>
        <el-table-column label="移动设备建议出价" width="140"><template #default="{ row }"><b v-if="res[row.id]">¥{{ res[row.id].mobile.suggestedBid }}</b></template></el-table-column>
        <el-table-column label="竞争广告主" width="100"><template #default="{ row }">{{ res[row.id]?.pc.competitors ?? '—' }}</template></el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button link type="primary" @click="suggest(row)">获取建议</el-button>
            <el-button v-if="res[row.id]" link type="primary" @click="apply(row)">采用PC建议</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const qas = ref([])
const res = reactive({})
async function suggest(row) { res[row.id] = await http.get('/tools/bid-suggestion', { params: { qaId: row.id } }) }
async function apply(row) {
  await http.put(`/qas/${row.id}`, { question: row.question, answer: row.answer, bid: res[row.id].pc.suggestedBid, source: row.source })
  ElMessage.success('出价已更新')
  qas.value = await http.get('/qas', { params: { auditStatus: 'APPROVED' } })
}
onMounted(async () => { qas.value = await http.get('/qas', { params: { auditStatus: 'APPROVED' } }) })
</script>
