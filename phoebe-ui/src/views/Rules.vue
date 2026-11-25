<template>
  <div class="pb-page">
    <div class="pb-crumb"><router-link to="/tools">工具中心</router-link> › <b>业务规则</b></div>
    <div class="pb-panel">
      <p class="muted" style="margin-top:0">配置位置：application.yml → phoebe.*。历史产品规则均做成可配置项；证据等级详见 docs/03-差异与不确定性清单.md。</p>
      <el-table :data="rows" border>
        <el-table-column prop="name" label="规则" min-width="200" />
        <el-table-column prop="key" label="配置项" min-width="220" />
        <el-table-column prop="value" label="当前值" min-width="160" />
        <el-table-column label="证据" width="120"><template #default="{ row }"><span :class="['evidence', row.ev === '公开资料' ? '' : 'inferred']">{{ row.ev }}</span></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import http from '../api'

const META = {
  maxPlansPerAccount: ['每账户计划上限', '公开资料'], maxUnitsPerPlan: ['每计划单元上限', '公开资料'], maxQasPerUnit: ['每单元问答上限', '公开资料'],
  maxMiddlePagesPerPlan: ['每计划中间页上限', '公开资料'], maxEnabledMiddlePagesPerPlan: ['每计划同时启用中间页', '公开资料'],
  questionMinChars: ['问题最少字符', '公开资料'], questionMaxChars: ['问题最多字符', '公开资料'], answerMinChars: ['答案最少字符', '公开资料'], answerMaxChars: ['答案最多字符', '公开资料'],
  forbidConsecutivePunct: ['禁止连续标点', '公开资料'], excelMaxRows: ['Excel 单次上限', '公开资料'], bannedIndustries: ['不可开通行业', '公开资料'],
  bannedWords: ['违禁词（示例）', '未知'], bidMin: ['最低出价', '未知'], bidMax: ['最高出价', '未知'], budgetMin: ['最低日预算', '未知'],
  mobileRatioMin: ['移动出价比例下限', '公开资料'], mobileRatioMax: ['移动出价比例上限', '公开资料'], maxSubscribeWordsPerUnit: ['每单元订阅词上限', '未知'],
  creativeTitleMaxChars: ['创意标题最多字符', '未知'], creativeDescMaxChars: ['创意描述最多字符', '未知'], domainConsistencyCheck: ['主域一致性预审提示', '推测']
}
const rows = ref([])
onMounted(async () => {
  const r = await http.get('/rules')
  rows.value = [
    ...Object.entries(r.rules).map(([k, v]) => ({ name: META[k]?.[0] || k, key: 'phoebe.rules.' + k, value: Array.isArray(v) ? v.join('、') : String(v), ev: META[k]?.[1] || '未知' })),
    ...Object.entries(r.simulator).map(([k, v]) => ({ name: '模拟器：' + k, key: 'phoebe.simulator.' + k, value: String(v), ev: k.includes('Position') ? '公开资料' : '推测' })),
    { name: '自动审核（演示）', key: 'phoebe.audit.auto-approve', value: String(r.audit.autoApprove), ev: '推测' }
  ]
})
</script>
