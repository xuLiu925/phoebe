<template>
  <div class="pb-page">
    <div class="pb-crumb"><a @click="router.back()">返回</a> › <b>新建知识问答</b></div>
    <div class="pb-panel">
      <el-form inline>
        <el-form-item label="推广单元" required>
          <el-select v-model="unitId" placeholder="请选择" style="width:300px" @change="onUnit">
            <el-option v-for="u in units" :key="u.unit.id" :label="u.planName + ' / ' + u.unit.name" :value="u.unit.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="unit"><span class="muted">单元出价 ¥{{ unit.unit.bid }} · 订阅词：{{ unit.subscribeWords.join('、') || '无' }} · 已有问答 {{ unit.qaCount }}/100</span></el-form-item>
      </el-form>

      <el-tabs v-model="tab">
        <el-tab-pane label="优质问答" name="quality">
          <div class="pb-toolbar">
            <el-input v-model="kw" placeholder="关键词" prefix-icon="Search" style="width:240px" @keyup.enter="loadQuality" />
            <el-button @click="loadQuality">搜索</el-button>
            <span class="muted">系统从知识类媒体提取的优质问答，可直接采纳或编辑后采纳<span class="evidence">公开资料 A/E</span></span>
          </div>
          <el-table :data="quality" border @selection-change="qSel = $event" max-height="340">
            <el-table-column type="selection" width="38" :selectable="r => !r.used" />
            <el-table-column prop="question" label="问题描述" min-width="200" />
            <el-table-column label="答案" min-width="300"><template #default="{ row }"><span class="muted">{{ row.answer?.slice(0, 50) }}…</span></template></el-table-column>
            <el-table-column label="推荐度" width="140"><template #default="{ row }"><div class="bar"><i :style="{ width: score(row) + '%' }" /></div></template></el-table-column>
            <el-table-column label="推荐理由" width="110"><template #default="{ row }"><span v-if="row.used" class="st-gray">已使用</span><span v-else>{{ reason(row) }}</span></template></el-table-column>
          </el-table>
          <div class="sel-actions">
            <el-button type="primary" :disabled="!qSel.length" @click="adoptSelected">采纳已选问答</el-button>
            <el-button :disabled="!qSel.length" @click="editSelected(qSel, 'QUALITY')">编辑已选问答</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="推荐问题" name="recommend">
          <div class="pb-toolbar">
            <span>搜索问题：</span>
            <el-input v-model="kw" placeholder="输入关键词，推荐高PV问题" prefix-icon="Search" style="width:260px" @keyup.enter="loadRecommend(false)" />
            <el-button @click="loadRecommend(false)">按关键词推荐</el-button>
            <el-button @click="loadRecommend(true)" :disabled="!unitId">按订阅词推荐</el-button>
          </div>
          <el-table :data="recommend" border @selection-change="rSel = $event" max-height="340">
            <el-table-column type="selection" width="38" :selectable="r => !r.used" />
            <el-table-column prop="question" label="问题描述" min-width="280" />
            <el-table-column label="推荐度" width="160" sortable :sort-by="r => r.pv"><template #default="{ row }"><div class="bar"><i :style="{ width: score(row) + '%' }" /></div></template></el-table-column>
            <el-table-column label="推荐理由" width="140"><template #default="{ row }"><span v-if="row.used" class="st-gray">已使用</span><span v-else>{{ reason(row) }}</span></template></el-table-column>
            <el-table-column label="渠道" width="90"><template #default="{ row }">{{ CHANNEL[row.channel] }}</template></el-table-column>
          </el-table>
          <div class="sel-actions">
            <el-button type="primary" :disabled="!rSel.length" @click="editSelected(rSel, 'RECOMMEND')">编辑已选问答</el-button>
            <span class="muted">共 {{ recommend.length }} 条</span>
          </div>
        </el-tab-pane>

        <el-tab-pane label="手动创建" name="manual">
          <p class="muted">在下方问答编辑区直接填写问题与答案。</p>
          <el-button type="primary" @click="addEmpty">+ 新增问答</el-button>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template v-if="items.length">
      <div class="pb-panel-title" style="margin:16px 0 8px">问答编辑（{{ items.length }}）</div>
      <QaEditor v-for="(it, i) in items" :key="it.key" :model-value="it" :title="'问答' + (i + 1)" removable :knowledge="knowledge" @remove="items.splice(i, 1)" />
      <el-button link type="primary" @click="addEmpty">+ 新增问答</el-button>
    </template>

    <div class="pb-form-actions" v-if="items.length" style="margin-top:12px">
      <el-button type="primary" :loading="saving" @click="saveAll">全部保存并提交</el-button>
      <el-button @click="router.back()">取消</el-button>
      <span class="muted">提交后进入审核，审核通过后参与竞价展现</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'
import { CHANNEL } from '../labels'
import QaEditor from '../components/QaEditor.vue'

const route = useRoute()
const router = useRouter()
const units = ref([])
const unitId = ref(route.query.unitId ? Number(route.query.unitId) : null)
const unit = computed(() => units.value.find(u => u.unit.id === unitId.value))
const tab = ref('quality')
const kw = ref('')
const quality = ref([])
const recommend = ref([])
const qSel = ref([])
const rSel = ref([])
const items = reactive([])
const knowledge = ref([])
const saving = ref(false)
let seq = 0

const maxPv = computed(() => Math.max(1, ...[...quality.value, ...recommend.value].map(r => r.pv)))
const score = r => Math.max(8, Math.round((r.pv / maxPv.value) * 100))
const reason = r => {
  const words = unit.value?.subscribeWords || []
  if (words.some(w => r.question.includes(w))) return '订阅词：' + words.find(w => r.question.includes(w))
  if (r.quality) return '优质问答'
  return r.pv > 20000 ? '高PV' : '相关问题'
}

async function onUnit() {
  kw.value = unit.value?.subscribeWords[0] || ''
  await Promise.all([loadQuality(), loadRecommend(true)])
}
async function loadQuality() { quality.value = await http.get('/recommend/quality-qas', { params: { keyword: kw.value } }) }
async function loadRecommend(bySub) {
  recommend.value = bySub && unitId.value
    ? await http.get(`/recommend/questions/by-unit/${unitId.value}`)
    : await http.get('/recommend/questions', { params: { keyword: kw.value } })
}
function addEmpty() { items.push(reactive({ key: ++seq, question: '', answer: '', bid: null, source: 'MANUAL' })) }
function editSelected(rows, source) {
  for (const r of rows) items.push(reactive({ key: ++seq, question: r.question, answer: r.answer ? `<p>${r.answer}</p>` : '', bid: null, source }))
  ElMessage.success(`已添加 ${rows.length} 条到下方编辑区`)
}
async function adoptSelected() {
  if (!unitId.value) return ElMessage.warning('请先选择推广单元')
  for (const r of qSel.value) await http.post(`/units/${unitId.value}/qas`, { question: r.question, answer: `<p>${r.answer}</p>`, source: 'QUALITY' })
  ElMessage.success(`已采纳 ${qSel.value.length} 条，已提交审核`)
  await onUnit()
}
async function saveAll() {
  if (!unitId.value) return ElMessage.warning('请先选择推广单元')
  saving.value = true
  const errors = []
  let ok = 0
  try {
    for (const it of [...items]) {
      try {
        await http.post(`/units/${unitId.value}/qas`, { question: it.question, answer: it.answer, bid: it.bid, source: it.source }, { silent: true })
        items.splice(items.indexOf(it), 1)
        ok++
      } catch (e) {
        errors.push(`「${it.question || '(空问题)'}」：${e.response?.data?.message || e.message}`)
      }
    }
  } finally { saving.value = false }
  if (!errors.length) {
    ElMessage.success(`${ok} 条问答已提交审核`)
    router.push({ path: '/promotion/qa', query: { planId: unit.value.unit.planId, unitId: unitId.value } })
  } else {
    ElMessageBox.alert(`成功 ${ok} 条；以下问答未通过校验，已保留在编辑区：<br>${errors.join('<br>')}`, '提交结果', { dangerouslyUseHTMLString: true })
  }
}

onMounted(async () => {
  ;[units.value, knowledge.value] = await Promise.all([http.get('/units'), http.get('/knowledge')])
  if (unitId.value) await onUnit()
  else await Promise.all([loadQuality(), loadRecommend(false)])
})
</script>

<style scoped>
.bar { height: 6px; background: #ebeef5; border-radius: 3px; overflow: hidden; width: 110px; }
.bar i { display: block; height: 100%; background: #3385ff; }
.sel-actions { margin-top: 10px; display: flex; align-items: center; gap: 10px; }
</style>
