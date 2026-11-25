<template>
  <div class="pb-page">
    <div class="grid">
      <div v-for="t in tools" :key="t.name" class="pb-panel tool">
        <div class="ill"><el-icon><component :is="t.icon" /></el-icon></div>
        <div class="info">
          <div class="name">{{ t.name }}</div>
          <div class="muted">{{ t.desc }}</div>
          <el-button @click="router.push(t.to)">{{ t.btn }}</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
// 工具中心构成参考公开资料 A：数据报告、历史操作记录、出价助手、智能引导
const tools = [
  { name: '数据报告', desc: '按账户/计划/单元/问答/中间页查看与下载推广数据', icon: 'DataAnalysis', btn: '查看', to: '/reports' },
  { name: '历史操作记录', desc: '查看账户的操作记录，默认最近一周，可下载', icon: 'Clock', btn: '查看', to: '/tools/oplog' },
  { name: '出价助手', desc: '提供出价建议，提升展现率', icon: 'Coin', btn: '使用', to: '/tools/bid' },
  { name: '智能引导', desc: '引导智能创建计划-单元-问答等物料，快速搭建账户', icon: 'Promotion', btn: '查看', to: '/tools/wizard' },
  { name: '业务规则', desc: '当前生效的数量上限、字数限制等可配置规则（重建版）', icon: 'Document', btn: '查看', to: '/tools/rules' },
  { name: '投放模拟', desc: '模拟检索、二价竞拍、点击计费全过程（重建版）', icon: 'Search', btn: '使用', to: '/simulator' }
]
</script>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, minmax(360px, 520px)); gap: 16px; }
.tool { display: flex; align-items: center; gap: 28px; padding: 28px 32px; margin: 0; }
.ill { width: 96px; height: 96px; border-radius: 50%; background: #f2f4f7; display: flex; align-items: center; justify-content: center; font-size: 44px; color: #8a94a6; flex-shrink: 0; }
.name { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.info .el-button { margin-top: 12px; width: 72px; }
</style>
