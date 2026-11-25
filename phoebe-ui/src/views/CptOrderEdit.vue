<template>
  <div class="pb-page">
    <div class="pb-crumb"><a @click="router.back()">返回</a> › <b>新建 CPT 包词</b></div>
    <div class="pb-panel" style="padding:24px 32px">
      <el-form label-width="120px" class="pb-form" style="max-width:900px">
        <el-form-item label="推广单元：" required>
          <el-select v-model="unitId" placeholder="请选择" style="width:320px" @change="loadQas">
            <el-option v-for="u in units" :key="u.unit.id" :label="u.planName + ' / ' + u.unit.name" :value="u.unit.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="展示问答：" required>
          <el-select v-model="qaId" placeholder="选择包词期间展示的问答（需审核通过）" style="width:480px">
            <el-option v-for="q in qas" :key="q.id" :label="q.question" :value="q.id" :disabled="q.auditStatus !== 'APPROVED'">
              {{ q.question }} <span v-if="q.auditStatus !== 'APPROVED'" class="muted">（未审核通过）</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="包断词：" required>
          <el-input v-model="keyword" placeholder="例如：眼霜" style="width:240px" />
          <span class="hint">检索词包含该词即命中；同一个词同一天只卖给一家</span>
        </el-form-item>
        <el-form-item label="投放日期：" required>
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" range-separator="~" :disabled-date="d => d < todayStart" />
          <span class="hint">按天售卖，单个订单最长 90 天</span>
        </el-form-item>
        <el-form-item>
          <el-button @click="doQuote" :disabled="!keyword || !range">询价</el-button>
        </el-form-item>
      </el-form>

      <div v-if="quote" class="quote">
        <div class="q-row">
          <div><div class="muted">日单价</div><b>¥{{ quote.dailyPrice }}</b></div>
          <div><div class="muted">天数</div><b>{{ quote.days }}</b></div>
          <div><div class="muted">总价（下单时一次性扣款）</div><b class="total">¥{{ quote.total }}</b></div>
          <div><div class="muted">“{{ quote.keyword }}”相关问题月检索量</div><b>{{ quote.monthlyPv }}</b></div>
        </div>
        <el-alert v-if="quote.available" type="success" :closable="false" show-icon title="所选日期均可购买" />
        <el-alert v-else type="error" :closable="false" show-icon title="以下日期已被包断，不可购买（同一个词同一天只卖给一家）">
          <div v-for="(c, i) in quote.conflicts" :key="i">{{ c.from }} ~ {{ c.to }}<span v-if="c.mine">（你已购买）</span></div>
        </el-alert>
        <p class="muted">定价规则为重建示例：基础价 + 词相关问题月检索量折算加价（原产品定价方式未知）。</p>
      </div>
    </div>
    <div class="pb-form-actions">
      <el-button type="primary" :disabled="!quote || !quote.available || !qaId" @click="submit">确认下单</el-button>
      <el-button @click="router.back()">取消</el-button>
      <span v-if="auth.account" class="muted">当前余额 ¥{{ auth.account.balance }}</span>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, inject, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'
import { auth } from '../auth'
import { fmtDate } from '../labels'

const route = useRoute()
const router = useRouter()
const refreshAccount = inject('refreshAccount', () => {})
const todayStart = new Date(new Date().setHours(0, 0, 0, 0))
const units = ref([])
const qas = ref([])
const unitId = ref(route.query.unitId ? Number(route.query.unitId) : null)
const qaId = ref(null)
const keyword = ref('')
const range = ref([fmtDate(new Date()), fmtDate(new Date(Date.now() + 6 * 86400000))])
const quote = ref(null)

watch([keyword, range], () => { quote.value = null })
async function loadQas() {
  qas.value = unitId.value ? await http.get(`/units/${unitId.value}/qas`) : []
  qaId.value = qas.value.find(q => q.auditStatus === 'APPROVED')?.id || null
  const u = units.value.find(x => x.unit.id === unitId.value)
  if (u && !keyword.value) keyword.value = u.subscribeWords[0] || ''
}
async function doQuote() {
  quote.value = await http.get('/cpt/quote', { params: { keyword: keyword.value, startDate: range.value[0], endDate: range.value[1] } })
}
async function submit() {
  await ElMessageBox.confirm(`包断「${quote.value.keyword}」${quote.value.startDate} ~ ${quote.value.endDate}，共 ${quote.value.days} 天，将从余额一次性扣除 ¥${quote.value.total}。确认下单？`, '确认下单', { type: 'warning' })
  await http.post('/cpt/orders', { qaId: qaId.value, keyword: keyword.value, startDate: range.value[0], endDate: range.value[1] })
  ElMessage.success('下单成功')
  refreshAccount()
  router.push('/promotion/cpt')
}
onMounted(async () => {
  units.value = await http.get('/units')
  if (unitId.value) await loadQas()
})
</script>

<style scoped>
.quote { border-top: 1px solid #ebeef5; padding-top: 16px; max-width: 900px; margin-left: 120px; }
.q-row { display: flex; gap: 48px; margin-bottom: 12px; }
.q-row b { font-size: 20px; font-family: Arial; }
.q-row .total { color: #f2463d; }
</style>
