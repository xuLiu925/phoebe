<template>
  <div class="pb-page">
    <div class="pb-panel hello">
      <div class="avatar"><el-icon><UserFilled /></el-icon></div>
      <div>
        <div class="hi">Hi，{{ auth.user?.displayName || auth.user?.username }}</div>
        <div class="muted">{{ account?.companyName }} · 行业：{{ account?.industry }}</div>
      </div>
      <div class="bal"><div class="muted">推广余额（元）</div><div class="num">{{ account?.balance }}</div></div>
      <div class="acts">
        <el-button type="primary" @click="router.push('/plans/new')">新建推广计划</el-button>
        <el-button @click="router.push('/tools/wizard')">智能引导</el-button>
      </div>
    </div>

    <el-row :gutter="12">
      <el-col :span="16">
        <div class="pb-panel">
          <div class="card-h"><b>知识营销</b><span class="muted">近 7 日（截至昨日）</span><span class="spacer" /><el-button size="small" @click="router.push('/promotion/plan')">进入</el-button></div>
          <div class="kpis">
            <div><div class="muted">消费（元）</div><div class="num">{{ s.cost ?? 0 }}</div></div>
            <div><div class="muted">展现</div><div class="num">{{ s.impressions ?? 0 }}</div></div>
            <div><div class="muted">点击</div><div class="num">{{ s.clicks ?? 0 }}</div></div>
            <div><div class="muted">点击率</div><div class="num">{{ s.ctr ?? 0 }}%</div></div>
            <div><div class="muted">平均点击价格</div><div class="num">{{ s.cpc ?? 0 }}</div></div>
          </div>
          <div ref="chart" style="height:240px" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="pb-panel">
          <div class="pb-panel-title">投放诊断</div>
          <div v-for="p in plans" :key="p.plan.id" class="row">
            <a @click="router.push({ path: '/promotion/unit', query: { planId: p.plan.id } })">{{ p.plan.name }}</a>
            <el-tooltip :disabled="!planStatus(p).tip" :content="planStatus(p).tip"><span :class="['st', planStatus(p).cls]">{{ planStatus(p).text }}</span></el-tooltip>
          </div>
          <el-empty v-if="!plans.length" description="还没有推广计划" :image-size="50" />
        </div>
        <div class="pb-panel">
          <div class="pb-panel-title">物料提醒</div>
          <div class="row"><span>审核拒绝的问答</span><a @click="router.push('/promotion/qa')" class="st-red">{{ rejected }} 条</a></div>
          <div class="row"><span>审核中的问答</span><span class="st-orange">{{ pending }} 条</span></div>
          <div class="row"><span>无有效中间页的计划</span><span class="st-orange">{{ plans.filter(p => p.blockers.some(b => b.includes('中间页'))).length }} 个</span></div>
          <p class="muted" style="margin:8px 0 0">前台展现必须有一条审核通过的问答和一套审核通过并启用的中间页。<span class="evidence">公开资料 B</span></p>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import http from '../api'
import { auth } from '../auth'
import { planStatus } from '../labels'

const router = useRouter()
const account = ref(null)
const s = ref({})
const plans = ref([])
const rejected = ref(0)
const pending = ref(0)
const chart = ref()

onMounted(async () => {
  const [acc, rep, ps, qas] = await Promise.all([http.get('/account'), http.get('/reports', { params: { level: 'ACCOUNT' } }), http.get('/plans'), http.get('/qas')])
  account.value = acc
  s.value = rep.summary
  plans.value = ps
  rejected.value = qas.filter(q => q.auditStatus === 'REJECTED').length
  pending.value = qas.filter(q => q.auditStatus === 'PENDING').length
  await nextTick()
  echarts.init(chart.value).setOption({
    tooltip: { trigger: 'axis' }, legend: { right: 0, data: ['消费', '点击'] }, grid: { left: 40, right: 40, top: 30, bottom: 24 },
    xAxis: { type: 'category', data: rep.trend.map(t => t.name.slice(5)), axisLine: { lineStyle: { color: '#ccc' } } },
    yAxis: [{ type: 'value', splitLine: { lineStyle: { color: '#f0f0f0' } } }, { type: 'value', splitLine: { show: false } }],
    series: [
      { name: '消费', type: 'line', smooth: true, data: rep.trend.map(t => t.cost), itemStyle: { color: '#3385ff' }, areaStyle: { color: 'rgba(51,133,255,.08)' } },
      { name: '点击', type: 'bar', yAxisIndex: 1, barWidth: 14, data: rep.trend.map(t => t.clicks), itemStyle: { color: '#c2daff' } }
    ]
  })
})
</script>

<style scoped>
.hello { display: flex; align-items: center; gap: 16px; padding: 20px 24px; }
.avatar { width: 52px; height: 52px; border-radius: 50%; background: #ebf3ff; color: #3385ff; display: flex; align-items: center; justify-content: center; font-size: 26px; }
.hi { font-size: 16px; font-weight: 600; margin-bottom: 4px; }
.bal { margin-left: 60px; }
.num { font-size: 24px; font-weight: 600; font-family: Arial; margin-top: 2px; }
.acts { margin-left: auto; }
.card-h { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.card-h b { font-size: 15px; }
.spacer { flex: 1; }
.kpis { display: flex; gap: 48px; padding: 4px 0 8px; }
.row { display: flex; justify-content: space-between; padding: 7px 0; border-bottom: 1px dashed #ebeef5; }
.row a { cursor: pointer; }
</style>
