<template>
  <div class="pb-page">
    <div class="pb-crumb"><router-link to="/tools">工具中心</router-link> › <b>智能引导</b></div>
    <div class="pb-panel" style="padding:24px 32px">
      <el-steps :active="step" finish-status="success" simple style="margin-bottom:24px;max-width:760px">
        <el-step title="填写推广信息" /><el-step title="生成计划-单元-问答" /><el-step title="完善中间页" />
      </el-steps>
      <el-form v-if="step === 0" label-width="130px" class="pb-form" style="max-width:760px">
        <el-form-item label="推广关键词：" required>
          <el-input v-model="w.keyword" placeholder="例如：眼霜、辅食" style="width:280px" /><span class="hint">系统将据此推荐优质问答，并设为单元订阅词</span>
        </el-form-item>
        <el-form-item label="计划日预算：" required><el-input-number v-model="w.budget" :min="50" :step="50" :controls="false" style="width:280px" /><span class="hint">元</span></el-form-item>
        <el-form-item label="单元出价：" required><el-input-number v-model="w.bid" :min="0.3" :step="0.1" :precision="2" :controls="false" style="width:280px" /><span class="hint">元/点击</span></el-form-item>
        <el-form-item label="问答数量："><el-input-number v-model="w.maxQas" :min="1" :max="20" style="width:140px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="run">一键搭建</el-button></el-form-item>
      </el-form>
      <div v-else style="max-width:760px">
        <el-result icon="success" title="账户结构已生成" :sub-title="r.next">
          <template #extra>
            <p>计划 #{{ r.planId }} · 单元 #{{ r.unitId }} · {{ r.qaIds.length }} 条问答（审核中）· 中间页草稿 #{{ r.middlePageId }}</p>
            <p v-if="r.skipped.length" class="muted">未生成：{{ r.skipped.join('；') }}</p>
            <el-button type="primary" @click="router.push(`/middle-pages/${r.middlePageId}`)">去完善中间页</el-button>
            <el-button @click="router.push({ path: '/promotion/qa', query: { planId: r.planId, unitId: r.unitId } })">查看问答</el-button>
          </template>
        </el-result>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api'

const router = useRouter()
const step = ref(0)
const w = reactive({ keyword: '眼霜', budget: 200, bid: 1, maxQas: 5 })
const r = ref(null)
async function run() { r.value = await http.post('/tools/wizard', w); step.value = 2 }
</script>
