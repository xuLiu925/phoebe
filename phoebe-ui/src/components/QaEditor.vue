<template>
  <!-- 问答撰写块：左侧问题 + 答案富文本，右侧“答案推荐 / 企业知识 / 答案示例”（布局参考公开资料 A 截图） -->
  <div class="qa-editor">
    <div class="left">
      <div class="head">
        <b>{{ title }}</b>
        <span class="spacer" />
        <el-button v-if="removable" link type="danger" @click="emit('remove')">移除问答</el-button>
      </div>
      <div class="label"><span class="req" />问题描述</div>
      <el-input v-model="m.question" placeholder="请输入问题标题（竞价对象）" />
      <div :class="['counter', qBad ? 'bad' : '']">{{ qTip }}</div>

      <div class="label"><span class="req" />答案描述</div>
      <div class="rte">
        <div class="bar">
          <el-tooltip content="项目符号"><span @mousedown.prevent="cmd('insertUnorderedList')">• ≡</span></el-tooltip>
          <el-tooltip content="项目编号"><span @mousedown.prevent="cmd('insertOrderedList')">1. ≡</span></el-tooltip>
          <el-tooltip content="插入超链接"><span @mousedown.prevent="link"><el-icon><Link /></el-icon></span></el-tooltip>
        </div>
        <div ref="area" class="area" contenteditable="true" @input="sync" @blur="sync" />
      </div>
      <div :class="['counter', aBad ? 'bad' : '']">{{ aTip }}</div>

      <div class="label">问答出价（元）<span class="muted">不填则使用单元出价</span></div>
      <el-input-number v-model="m.bid" :min="0.3" :max="999.99" :step="0.1" :precision="2" :value-on-clear="null" style="width:160px" />
    </div>
    <div class="right">
      <el-tabs v-model="side">
        <el-tab-pane label="答案推荐" name="rec">
          <el-button @click="recommend" style="margin-bottom:8px">点击获取答案推荐</el-button>
          <div v-if="!recs.length" class="muted">填写问题后点击获取，系统根据问题推荐相关答案供参考</div>
          <div v-for="(a, i) in recs" :key="i" class="rec">
            <div>{{ a }}</div>
            <el-button link type="primary" @click="setAnswer('<p>' + a + '</p>')">引用该答案</el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="企业知识" name="kn">
          <div v-if="!knowledge.length" class="muted">暂无企业知识，可在“资源管理 › 企业知识管理”中新建</div>
          <div v-for="k in knowledge" :key="k.id" class="rec">
            <b>{{ k.title }}</b>
            <div class="muted">{{ k.content }}</div>
            <el-button link type="primary" @click="setAnswer(m.answer + '<p>' + k.content + '</p>')">插入答案末尾</el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="答案示例" name="ex">
          <div class="rec muted">
            <p>· 先直接回答问题，再展开说明，避免开篇即推销。</p>
            <p>· 答案不少于 80 个汉字，内容客观、有知识价值（“知识类可售，口碑类不可售”）。</p>
            <p>· 不得包含黄反毒等违禁词，不能出现“？？”“！！”等连续标点。</p>
            <p>· 可使用项目符号/编号梳理要点，可插入超链接。</p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import http from '../api'
import { countChars, stripHtml } from '../labels'

const props = defineProps({ modelValue: Object, title: { type: String, default: '问答' }, removable: Boolean, knowledge: { type: Array, default: () => [] } })
const emit = defineEmits(['remove'])
const m = props.modelValue
const area = ref()
const side = ref('rec')
const recs = ref([])

const qn = computed(() => countChars(m.question))
const an = computed(() => countChars(stripHtml(m.answer)))
const qBad = computed(() => qn.value < 10 || qn.value > 98)
const aBad = computed(() => an.value < 160 || an.value > 19998)
const qTip = computed(() => (qn.value < 10 ? `当前输入${qn.value}个字节，不足10个字节` : qn.value > 98 ? `已超出${qn.value - 98}个字节` : `还可以输入${98 - qn.value}个字节`))
const aTip = computed(() => (an.value < 160 ? `当前输入${an.value}个字节，不足160个字节` : an.value > 19998 ? `已超出${an.value - 19998}个字节` : `还可以输入${19998 - an.value}个字节`))

function sync() { m.answer = area.value.innerHTML }
function setAnswer(html) { m.answer = html; area.value.innerHTML = html }
function cmd(c) { area.value.focus(); document.execCommand(c); sync() }
function link() {
  const url = prompt('链接地址', 'https://')
  if (url) { area.value.focus(); document.execCommand('createLink', false, url); sync() }
}
async function recommend() { recs.value = await http.get('/recommend/answers', { params: { question: m.question } }) }

onMounted(() => { area.value.innerHTML = m.answer || '' })
watch(() => m.answer, v => { if (area.value && area.value.innerHTML !== v) area.value.innerHTML = v || '' })
</script>

<style scoped>
.qa-editor { display: flex; border: 1px solid #e6e8eb; background: #fff; margin-bottom: 12px; }
.left { flex: 1; padding: 14px 18px; border-right: 1px solid #e6e8eb; min-width: 0; }
.right { width: 300px; padding: 4px 14px 14px; font-size: 12px; }
.head { display: flex; align-items: center; margin-bottom: 8px; }
.spacer { flex: 1; }
.label { margin: 10px 0 6px; color: #666; font-size: 12px; }
.counter { text-align: right; font-size: 12px; color: #999; margin-top: 4px; }
.counter.bad { color: #f2463d; }
.rte { border: 1px solid #dcdfe6; border-radius: 2px; }
.bar { background: #f7f8fa; border-bottom: 1px solid #ebeef5; padding: 4px 8px; display: flex; gap: 12px; color: #666; }
.bar span { cursor: pointer; display: inline-flex; align-items: center; }
.bar span:hover { color: #3385ff; }
.area { min-height: 160px; max-height: 360px; overflow: auto; padding: 8px 10px; outline: none; line-height: 1.7; }
.area :deep(p) { margin: 4px 0; }
.rec { padding: 8px 0; border-bottom: 1px dashed #e6e8eb; line-height: 1.6; }
.rec p { margin: 4px 0; }
</style>
