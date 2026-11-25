<template>
  <div class="pb-page">
    <div class="pb-crumb"><router-link to="/tools">工具中心</router-link> › <b>历史操作记录</b></div>
    <div class="pb-panel">
      <el-tabs v-model="tab" @tab-change="load">
        <el-tab-pane label="操作记录" name="op" />
        <el-tab-pane label="预算达限记录" name="budget" />
      </el-tabs>
      <template v-if="tab === 'budget'">
        <div class="pb-toolbar">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="~" style="width:240px" @change="load" />
          <span class="muted">计划当日消费达到日预算时记录一条，当日停止展现（默认最近一周）</span>
        </div>
        <el-table :data="limits" border>
          <el-table-column prop="planName" label="推广计划" min-width="160" />
          <el-table-column prop="budget" label="日预算（元）" width="120" />
          <el-table-column prop="cost" label="达限时当日消费（元）" width="160" />
          <el-table-column prop="limitDate" label="日期" width="120" />
          <el-table-column prop="createdAt" label="达限时间" width="170" />
        </el-table>
        <el-pagination style="margin-top:10px" layout="total, prev, pager, next" :total="limitTotal" :page-size="20" v-model:current-page="limitPage" @current-change="load" />
      </template>
      <template v-else>
      <div class="pb-toolbar">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="~" style="width:240px" @change="load" />
        <el-select v-model="ttype" clearable placeholder="全部操作对象" style="width:140px" @change="load">
          <el-option v-for="(t, k) in TYPES" :key="k" :label="t" :value="k" />
        </el-select>
        <span class="muted">默认最近一周</span>
        <span class="spacer" />
        <el-button icon="Download" @click="exportCsv">下载操作记录</el-button>
      </div>
      <el-table :data="logs" border>
        <el-table-column prop="username" label="操作者" width="110" />
        <el-table-column label="操作对象类型" width="110"><template #default="{ row }">{{ TYPES[row.targetType] || row.targetType }}</template></el-table-column>
        <el-table-column prop="targetId" label="对象ID" width="80" />
        <el-table-column label="操作类型" width="130"><template #default="{ row }">{{ ACTIONS[row.action] || row.action }}</template></el-table-column>
        <el-table-column prop="detail" label="操作内容" min-width="360" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="操作时间" width="160" />
      </el-table>
      <el-pagination style="margin-top:10px" layout="total, prev, pager, next" :total="total" :page-size="20" v-model:current-page="page" @current-change="load" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import http from '../api'

const TYPES = { PLAN: '推广计划', UNIT: '推广单元', QA: '知识问答', CREATIVE: '高级创意', MIDDLE_PAGE: '中间页', KNOWLEDGE: '企业知识', IMAGE: '图片', AUDIT: '审核', ACCOUNT: '账户' }
const ACTIONS = { CREATE: '新建', UPDATE: '修改', DELETE: '删除', ENABLE: '启用', PAUSE: '暂停', UPDATE_CONTENT: '修改内容', UPDATE_BID: '修改出价', SAVE_DRAFT: '保存', SUBMIT: '提交审核', IMPORT: '导入', COPY_PC_TO_MOBILE: '导入PC内容', EXCEL_IMPORT: '批量上传', SUBSCRIBE_WORDS: '修改订阅词', APPROVE: '审核通过', REJECT: '审核拒绝', RECHARGE: '充值', DISABLE: '停用', BIND_QA: '关联问答' }
const range = ref(null)
const ttype = ref(null)
const logs = ref([])
const total = ref(0)
const page = ref(1)

const tab = ref('op')
const limits = ref([])
const limitTotal = ref(0)
const limitPage = ref(1)

async function load() {
  if (tab.value === 'budget') {
    const b = await http.get('/budget-limits', { params: { from: range.value?.[0], to: range.value?.[1], page: limitPage.value, size: 20 } })
    limits.value = b.records
    limitTotal.value = b.total
    return
  }
  const r = await http.get('/oplogs', { params: { from: range.value?.[0], to: range.value?.[1], targetType: ttype.value || undefined, page: page.value, size: 20 } })
  logs.value = r.records
  total.value = r.total
}
function exportCsv() {
  const esc = v => '"' + String(v ?? '').replace(/"/g, '""') + '"'
  const lines = ['﻿操作者,对象类型,对象ID,操作类型,操作内容,操作时间', ...logs.value.map(l => [l.username, TYPES[l.targetType], l.targetId, ACTIONS[l.action] || l.action, l.detail, l.createdAt].map(esc).join(','))]
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([lines.join('\n')], { type: 'text/csv' }))
  a.download = 'oplog.csv'
  a.click()
}
onMounted(load)
</script>
