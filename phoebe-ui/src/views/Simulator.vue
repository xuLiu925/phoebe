<template>
  <div class="pb-page">
    <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px"
      title="投放模拟：模拟网民检索 → 触发词匹配 → 二价竞拍 → 与自然结果混排 → 问答入口点击计费。全部为本地模拟，未接入任何真实百度系统；自然结果来自虚构问答库。" />
    <div class="pb-panel searchbox">
      <div class="channels">
        <a v-for="(t, k) in CHANNEL" :key="k" :class="{ on: q.channel === k }" @click="q.channel = k">{{ t }}</a>
      </div>
      <div class="sb">
        <input v-model="q.query" placeholder="输入检索词，例如：眼霜什么牌子好" @keyup.enter="search" />
        <button @click="search">搜索一下</button>
      </div>
      <div class="opts">
        <el-radio-group v-model="q.device"><el-radio value="PC">计算机</el-radio><el-radio value="MOBILE">移动设备</el-radio></el-radio-group>
        <el-radio-group v-model="q.pageType"><el-radio value="SEARCH_LIST">检索结果页</el-radio><el-radio value="QUESTION_PAGE">问题详情页（相似问题）</el-radio></el-radio-group>
        <el-select v-model="q.region" clearable placeholder="网民地域（可选）" style="width:150px"><el-option v-for="p in PROVINCES" :key="p" :label="p" :value="p" /></el-select>
        <el-input v-model="q.ip" clearable placeholder="网民IP（可选）" style="width:150px" />
        <el-date-picker v-model="q.time" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="模拟检索时间（默认现在）" style="width:210px" />
        <span class="spacer" />
        <el-button @click="traffic">批量造流量（100 次检索）</el-button>
      </div>
    </div>

    <el-row :gutter="12" v-if="r">
      <el-col :span="11">
        <div class="pb-panel serp">
          <div class="muted" style="margin-bottom:8px">{{ CHANNEL[r.request.channel] }}{{ r.request.device === 'MOBILE' ? '（移动）' : '' }} · {{ r.rule }}</div>
          <div v-for="it in r.items" :key="it.position" :class="['item', { ad: it.type === 'AD' }]">
            <a class="t" @click="it.type === 'AD' ? click(it) : null">{{ it.title }}</a>
            <div class="snip">{{ it.snippet }}</div>
            <div class="src">
              <template v-if="it.type === 'AD'">{{ it.advertiser }} · 企业回答 <span class="adtag">广告</span></template>
              <template v-else>网友回答 · 模拟自然结果</template>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="13">
        <div class="pb-panel">
          <div class="pb-panel-title">竞价明细 <span class="muted">得分 = 有效出价 × 质量度；计费 = 下一名得分 / 自身质量度 + 0.01</span></div>
          <el-table :data="r.auction" border>
            <el-table-column prop="rank" label="排名" width="50" />
            <el-table-column prop="accountName" label="广告主" width="110" />
            <el-table-column prop="question" label="问题" min-width="140" />
            <el-table-column label="匹配" width="72"><template #default="{ row }">{{ MATCH[row.matchType] }}</template></el-table-column>
            <el-table-column prop="bid" label="出价" width="56" />
            <el-table-column prop="effectiveBid" label="有效出价" width="70" />
            <el-table-column prop="quality" label="质量度" width="62" />
            <el-table-column label="得分" width="56"><template #default="{ row }">{{ row.score.toFixed(2) }}</template></el-table-column>
            <el-table-column label="计费价" width="62"><template #default="{ row }">{{ row.chargePrice ?? '—' }}</template></el-table-column>
            <el-table-column label="结果" width="86"><template #default="{ row }"><span v-if="row.won" class="st-green">胜出展现</span><span v-else class="st-gray">{{ row.note || '未胜出' }}</span></template></el-table-column>
          </el-table>
          <el-empty v-if="!r.auction.length" description="没有可参与竞价的问答" :image-size="50" />
        </div>
        <div class="pb-panel" v-if="r.excluded.length">
          <div class="pb-panel-title">匹配但不具备投放资格</div>
          <el-table :data="r.excluded" border>
            <el-table-column prop="accountName" label="广告主" width="120" />
            <el-table-column prop="question" label="问题" min-width="150" />
            <el-table-column label="原因" min-width="200"><template #default="{ row }"><span class="st-orange">{{ row.reasons.join('；') }}</span></template></el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="mpDlg" :title="'企业商业专属问答页 · ' + (clickRes?.middlePage?.name || '')" :width="clickRes?.middlePage?.device === 'MOBILE' ? 440 : 980" top="4vh">
      <div class="charge">
        <template v-if="clickRes?.duplicateClick">同一展现重复点击，不再计费</template>
        <template v-else>问答入口点击计费 <b>¥{{ clickRes?.charged }}</b>（有效出价 ¥{{ clickRes?.bidPrice }}），广告主余额 ¥{{ clickRes?.balance }}。中间页内点击不计费。</template>
      </div>
      <PagePreview v-if="clickRes" :content="clickRes.middlePage.content" :device="clickRes.middlePage.device" :pc-style="clickRes.middlePage.pcStyle"
        :question="clicked?.title" :answer="clickedAnswer" @inner="inner" />
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, inject } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import { CHANNEL, MATCH, PROVINCES } from '../labels'
import PagePreview from '../components/PagePreview.vue'

const refreshAccount = inject('refreshAccount', () => {})
const q = reactive({ query: '眼霜什么牌子好', channel: 'ZHIDAO', device: 'PC', pageType: 'SEARCH_LIST', region: null, ip: '', time: null })
const r = ref(null)
const mpDlg = ref(false)
const clickRes = ref(null)
const clicked = ref(null)
const clickedAnswer = ref('')

async function search() { r.value = await http.post('/simulator/search', q) }
async function click(it) {
  clicked.value = it
  clickRes.value = await http.post(`/simulator/click/${it.impressionId}`)
  clickedAnswer.value = it.answer || '<p>' + it.snippet + '</p>'
  mpDlg.value = true
  refreshAccount()
}
async function inner(target) {
  await http.post(`/simulator/inner-click/${clicked.value.impressionId}`, null, { params: { target } })
  ElMessage.info(`中间页内点击「${target}」已记录，不计费`)
}
async function traffic() {
  const t = await http.post('/simulator/traffic', { searches: 100, clickRate: 0.15 })
  ElMessage.success(`模拟 ${t.searches} 次检索：广告展现 ${t.adImpressions}，点击 ${t.clicks}，消费 ¥${t.cost}`)
  refreshAccount()
}
</script>

<style scoped>
.searchbox { padding: 18px 24px; }
.channels { display: flex; gap: 20px; margin-bottom: 8px; font-size: 13px; }
.channels a { cursor: pointer; color: #666; padding-bottom: 4px; text-decoration: none; }
.channels a.on { color: #333; font-weight: 600; border-bottom: 2px solid #3385ff; }
.sb { display: flex; max-width: 640px; }
.sb input { flex: 1; height: 38px; border: 2px solid #c4c7ce; border-right: none; border-radius: 8px 0 0 8px; padding: 0 12px; font-size: 15px; outline: none; }
.sb input:focus { border-color: #3385ff; }
.sb button { width: 110px; border: none; background: #3385ff; color: #fff; font-size: 15px; border-radius: 0 8px 8px 0; cursor: pointer; }
.opts { display: flex; align-items: center; gap: 24px; margin-top: 12px; }
.spacer { flex: 1; }
.serp .item { padding: 10px 0; border-bottom: 1px solid #f2f2f2; }
.serp .t { font-size: 16px; color: #2440b3; text-decoration: underline; text-underline-offset: 2px; cursor: default; }
.serp .ad .t { cursor: pointer; }
.serp .snip { color: #333; font-size: 13px; line-height: 1.6; margin-top: 4px; }
.serp .src { color: #626675; font-size: 12px; margin-top: 2px; }
.adtag { border: 1px solid #ccc; color: #999; font-size: 10px; padding: 0 3px; border-radius: 2px; margin-left: 4px; }
.charge { background: #f0f9eb; color: #529b2e; padding: 8px 12px; margin-bottom: 10px; font-size: 13px; }
</style>
