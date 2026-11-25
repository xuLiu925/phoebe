<template>
  <div class="pb-page">
    <div class="pb-crumb"><a @click="router.back()">返回</a> › <b>{{ id ? '编辑中间页' : '新建中间页' }}</b>
      <span v-if="v" :class="['st', pageStatus(v).cls]" style="margin-left:12px">{{ pageStatus(v).text }}</span>
      <span v-if="v" class="muted" style="margin-left:8px">编辑版本 v{{ v.latest.versionNo }}<template v-if="v.online"> · 线上版本 v{{ v.online.versionNo }}</template></span>
    </div>

    <el-alert v-if="locked" type="warning" :closable="false" show-icon style="margin-bottom:10px" title="中间页正在审核中，暂不可编辑（审核中：可预览，不可编辑/启用/导入）" />
    <el-alert v-if="v?.latest.rejectReason" type="error" :closable="false" show-icon style="margin-bottom:10px" :title="'审核拒绝理由：' + v.latest.rejectReason" />
    <el-alert v-if="v?.online && v.latest.id !== v.online.id && !locked" type="info" :closable="false" show-icon style="margin-bottom:10px"
      title="您正在修改已生效的中间页：新版审核通过之前，向用户展现的是上一个审核通过的版本；新版审核通过后自动上线替换旧版。" />

    <div class="pb-panel">
      <el-form inline label-width="100px" :disabled="locked">
        <el-form-item label="推广计划：" required>
          <el-select v-model="planId" :disabled="!!id" placeholder="请选择" style="width:220px">
            <el-option v-for="p in plans" :key="p.plan.id" :label="p.plan.name" :value="p.plan.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="中间页名称：" required>
          <el-input v-model="name" placeholder="不能超过30个字符" maxlength="30" style="width:220px" />
        </el-form-item>
        <el-form-item label="导入中间页：">
          <el-button @click="importDlg = true">选择中间页</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="pb-panel">
      <div class="side-tabs">
        <a :class="{ on: side === 'pc' }" @click="side = 'pc'">PC中间页内容 <i :class="['ok', done('pc') ? 'y' : 'n']">{{ done('pc') ? '已完成' : '未完成' }}</i></a>
        <a :class="{ on: side === 'mobile' }" @click="side = 'mobile'">无线中间页内容 <i :class="['ok', done('mobile') ? 'y' : 'n']">{{ done('mobile') ? '已完成' : '未完成' }}</i></a>
        <span class="spacer" />
        <el-button v-if="side === 'mobile'" :disabled="locked" @click="copyPc">导入PC中间页内容</el-button>
        <el-button icon="View" @click="fullDlg = true">整页预览</el-button>
      </div>

      <el-form label-width="100px" :disabled="locked" class="mods">
        <el-collapse v-model="openMods">
          <!-- ① 顶部 banner -->
          <el-collapse-item name="banner">
            <template #title><span class="mod-t">{{ side === 'pc' ? '顶部 banner 区域' : '顶部 banner' }}</span><span :class="['ok', modOk('banners') ? 'y' : 'n']">{{ modOk('banners') ? '已完成' : '未完成' }}</span></template>
            <div class="mod">
              <div class="mod-form">
                <div v-for="(b, i) in c.banners" :key="'b' + i" class="group">
                  <div class="group-h">Banner {{ i + 1 }} <el-button link type="danger" @click="c.banners.splice(i, 1)">删除</el-button></div>
                  <el-form-item label="Banner图片：" required><ImagePicker v-model="b.image" :images="images" /></el-form-item>
                  <el-form-item label="跳转链接："><el-input v-model="b.url" placeholder="https://" /></el-form-item>
                </div>
                <el-button size="small" @click="c.banners.push({ image: '', url: '' })">+ 添加 Banner</el-button>
              </div>
              <div class="mod-prev">
                <div class="prev-h">预览 <span class="muted">{{ side === 'pc' ? '960 × 90' : '750 × 300' }}</span></div>
                <img v-if="c.banners[0]?.image" :src="c.banners[0].image" class="prev-banner" />
                <div v-else class="ph">{{ side === 'pc' ? '960 × 90' : '750 × 300' }}</div>
              </div>
            </div>
          </el-collapse-item>

          <!-- ② 企业信息 -->
          <el-collapse-item name="company">
            <template #title><span class="mod-t">{{ side === 'pc' ? '企业信息区域（右侧）' : '顶部企业信息' }}</span><span :class="['ok', modOk('companyInfo') ? 'y' : 'n']">{{ modOk('companyInfo') ? '已完成' : '未完成' }}</span></template>
            <div class="mod">
              <div class="mod-form">
                <el-form-item label="企业名称：" required><el-input v-model="c.companyInfo.name" /></el-form-item>
                <el-form-item label="企业Logo：" required><ImagePicker v-model="c.companyInfo.logo" :images="images" /></el-form-item>
                <el-form-item label="企业简介："><el-input v-model="c.companyInfo.intro" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="企业官网：" required><el-input v-model="c.companyInfo.website" placeholder="默认为账户官网" /></el-form-item>
                <el-form-item label="咨询链接："><el-input v-model="c.companyInfo.consultUrl" placeholder="点击“咨询”按钮进入在线沟通" /></el-form-item>
                <el-form-item label="咨询电话："><el-input v-model="c.companyInfo.phone" placeholder="不会自动填充，需要电话咨询请手动填写" /></el-form-item>
                <el-form-item v-if="side === 'pc'" label="二维码："><ImagePicker v-model="c.companyInfo.qrCode" :images="images" /><div class="muted">二维码易触发“主域一致性”审核问题，请确认内容指向官网主域</div></el-form-item>
                <el-form-item v-else label="APP下载："><el-input v-model="c.appDownloadUrl" placeholder="选填" /></el-form-item>
              </div>
              <div class="mod-prev">
                <div class="prev-h">预览</div>
                <div class="comp">
                  <img v-if="c.companyInfo.logo" :src="c.companyInfo.logo" />
                  <b>{{ c.companyInfo.name || '企业名称' }}</b>
                  <p class="muted">{{ c.companyInfo.intro }}</p>
                  <span class="consult">咨询</span>
                </div>
              </div>
            </div>
          </el-collapse-item>

          <!-- ③ 产品介绍 -->
          <el-collapse-item name="products">
            <template #title><span class="mod-t">{{ side === 'pc' ? '产品介绍区域（下方）' : '底部产品区域' }}</span><span :class="['ok', modOk('products') ? 'y' : 'n']">{{ modOk('products') ? '已完成' : '未完成' }}</span></template>
            <div class="mod">
              <div class="mod-form">
                <el-form-item v-if="side === 'pc'" label="样式：">
                  <el-radio-group v-model="pcStyle"><el-radio value="IMAGE_TEXT">图文形式</el-radio><el-radio value="TEXT_LINK">文字链形式</el-radio></el-radio-group>
                </el-form-item>
                <div v-for="(p, i) in c.products" :key="'p' + i" class="group">
                  <div class="group-h">产品 {{ i + 1 }} <el-button link type="danger" @click="c.products.splice(i, 1)">删除</el-button></div>
                  <el-form-item v-if="side !== 'pc' || pcStyle !== 'TEXT_LINK'" label="产品图片：" required><ImagePicker v-model="p.image" :images="images" /></el-form-item>
                  <el-form-item label="产品名称：" required><el-input v-model="p.title" /></el-form-item>
                  <el-form-item label="产品描述："><el-input v-model="p.desc" /></el-form-item>
                  <el-form-item label="推广链接：" required><el-input v-model="p.url" placeholder="点击跳转广告主落地页" /></el-form-item>
                </div>
                <el-button size="small" @click="c.products.push({ image: '', title: '', desc: '', url: '' })">+ 添加产品</el-button>
              </div>
              <div class="mod-prev">
                <div class="prev-h">预览</div>
                <div class="prods">
                  <div v-for="(p, i) in c.products.slice(0, 4)" :key="i" class="prod">
                    <img v-if="p.image && (side !== 'pc' || pcStyle !== 'TEXT_LINK')" :src="p.image" />
                    <a>{{ p.title || '产品名称' }}</a>
                  </div>
                </div>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-form>
    </div>

    <div class="pb-form-actions">
      <el-button type="primary" :disabled="locked" @click="save(true)">保存并提交</el-button>
      <el-button type="primary" plain :disabled="locked" @click="save(false)">仅保存</el-button>
      <el-button @click="router.push({ path: '/promotion/page', query: planId ? { planId } : {} })">取消</el-button>
      <span v-if="savedAt" class="saved">已保存 {{ savedAt }}</span>
      <span v-if="v?.missing?.length" class="muted">未完成：{{ v.missing.join('、') }}</span>
    </div>

    <el-dialog v-model="importDlg" title="选择中间页物料" width="420px">
      <div class="muted" style="margin-bottom:8px">仅支持导入审核通过（状态为“有效”）的中间页；已填写的信息将被覆盖。</div>
      <div v-for="p in validPages" :key="p.page.id" :class="['imp', { on: importFrom === p.page.id }]" @click="importFrom = p.page.id">› {{ p.page.name }}</div>
      <el-empty v-if="!validPages.length" description="暂无有效中间页" :image-size="60" />
      <template #footer><el-button @click="importDlg = false">取消</el-button><el-button type="primary" :disabled="!importFrom" @click="doImport">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="fullDlg" :title="(side === 'pc' ? 'PC' : '无线') + '中间页预览'" :width="side === 'pc' ? 980 : 420" top="4vh">
      <PagePreview :content="c" :device="side === 'pc' ? 'PC' : 'MOBILE'" :pc-style="pcStyle"
        question="此处为预览，真实展示时以您提交的问题为准" answer="<p>此处为预览，真实展示时以您系统提交的问题为准，请关注中间页物料是否展示正常。</p>" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElSelect, ElOption } from 'element-plus'
import http from '../api'
import { auth } from '../auth'
import { pageStatus } from '../labels'
import PagePreview from '../components/PagePreview.vue'

/** 图片选择：从图片库选择，或直接输入 URL */
const ImagePicker = {
  props: ['modelValue', 'images'],
  emits: ['update:modelValue'],
  setup(p, { emit }) {
    return () => h('div', { style: 'display:flex;gap:8px;align-items:center;width:100%' }, [
      p.modelValue ? h('img', { src: p.modelValue, style: 'width:40px;height:40px;object-fit:cover;border:1px solid #eee' }) : null,
      h(ElSelect, {
        modelValue: p.modelValue, filterable: true, allowCreate: true, clearable: true, placeholder: '从图片库选择或输入图片地址', style: 'flex:1',
        'onUpdate:modelValue': val => emit('update:modelValue', val)
      }, () => (p.images || []).map(i => h(ElOption, { key: i.id, label: `${i.name} ${i.cropRatio || ''}`, value: i.url })))
    ])
  }
}

const empty = () => ({ companyInfo: {}, banners: [], products: [], appDownloadUrl: null, videoUrl: null })
const route = useRoute()
const router = useRouter()
const id = ref(route.params.id ? Number(route.params.id) : null)
const v = ref(null)
const plans = ref([])
const planId = ref(route.query.planId ? Number(route.query.planId) : null)
const name = ref('')
const side = ref('pc')
const pc = ref(empty())
const mobile = ref(empty())
const pcStyle = ref('IMAGE_TEXT')
const images = ref([])
const validPages = ref([])
const importDlg = ref(false)
const importFrom = ref(null)
const fullDlg = ref(false)
const savedAt = ref('')
const openMods = ref(['banner', 'company', 'products'])
const c = computed(() => (side.value === 'pc' ? pc.value : mobile.value))
const locked = computed(() => v.value?.latest.auditStatus === 'PENDING')

// 模块完成度：已保存版本以服务端为准，未保存时前端粗判
const modOk = m => {
  const x = c.value
  if (m === 'banners') return x.banners.length > 0 && x.banners.every(b => b.image)
  if (m === 'companyInfo') return !!(x.companyInfo.name && x.companyInfo.logo && x.companyInfo.website && (x.companyInfo.consultUrl || x.companyInfo.phone))
  return x.products.length > 0 && x.products.every(p => p.title && p.url && (p.image || (side.value === 'pc' && pcStyle.value === 'TEXT_LINK')))
}
const done = s => {
  const cs = v.value?.completeness
  return cs ? ['companyInfo', 'banners', 'products'].every(k => cs[`${s}.${k}`]) : false
}

function apply(view) {
  v.value = view
  name.value = view.page.name
  planId.value = view.page.planId
  pc.value = view.latest.pcContent || empty()
  pc.value.companyInfo ||= {}
  mobile.value = view.latest.mobileContent || empty()
  mobile.value.companyInfo ||= {}
  pcStyle.value = view.latest.pcStyle
}

async function ensureCreated() {
  if (id.value) return
  if (!planId.value) throw ElMessage.warning('请选择推广计划')
  const view = await http.post(`/plans/${planId.value}/middle-pages`, { name: name.value })
  id.value = view.page.id
  router.replace(`/middle-pages/${id.value}`)
}
async function save(submit) {
  await ensureCreated()
  const body = { name: name.value, pcStyle: pcStyle.value, pc: pc.value, mobile: v.value?.latest.mobileContent || side.value === 'mobile' || mobile.value.banners.length ? mobile.value : null }
  const view = submit ? await http.post(`/middle-pages/${id.value}/submit`, body) : await http.put(`/middle-pages/${id.value}/draft`, body)
  apply(view)
  savedAt.value = new Date().toLocaleTimeString()
  ElMessage.success(submit ? '已提交审核' : '已保存（未提交审核）')
}
async function copyPc() {
  await ensureCreated()
  await http.put(`/middle-pages/${id.value}/draft`, { name: name.value, pcStyle: pcStyle.value, pc: pc.value })
  apply(await http.post(`/middle-pages/${id.value}/copy-pc-to-mobile`))
  ElMessage.success('已根据 PC 中间页自动生成无线中间页')
}
async function doImport() {
  await ensureCreated()
  apply(await http.post(`/middle-pages/${id.value}/import`, null, { params: { fromId: importFrom.value } }))
  importDlg.value = false
  ElMessage.success('已导入')
}

onMounted(async () => {
  const [ps, imgs, valid] = await Promise.all([http.get('/plans'), http.get('/images'), http.get('/middle-pages/valid')])
  plans.value = ps
  images.value = imgs
  validPages.value = valid.filter(p => p.page.id !== id.value)
  if (id.value) apply(await http.get(`/middle-pages/${id.value}`))
  else {
    // 默认值 [P:E]：企业名称取客户企业名称；网址默认官网；咨询链接默认在线咨询链接；电话不自动填充
    const a = auth.account || {}
    pc.value.companyInfo = { name: a.companyName, website: a.website, consultUrl: a.consultUrl }
    name.value = (plans.value.find(p => p.plan.id === planId.value)?.plan.name || '') + '-中间页'
  }
})
</script>

<style scoped>
.side-tabs { display: flex; align-items: center; gap: 8px; border-bottom: 1px solid #ebeef5; margin: -4px 0 8px; }
.side-tabs a { padding: 8px 14px; cursor: pointer; color: #333; border-bottom: 2px solid transparent; margin-bottom: -1px; text-decoration: none; }
.side-tabs a.on { color: #3385ff; border-bottom-color: #3385ff; font-weight: 600; }
.spacer { flex: 1; }
.ok { font-style: normal; font-size: 11px; margin-left: 8px; padding: 0 5px; border-radius: 2px; line-height: 16px; display: inline-block; }
.ok.y { color: #2bb24c; background: #eaf7ee; }
.ok.n { color: #999; background: #f2f3f5; }
.mod-t { font-weight: 600; color: #333; }
.mod { display: flex; gap: 16px; }
.mod-form { flex: 1; min-width: 0; padding-right: 8px; }
.mod-prev { width: 360px; border: 1px solid #ebeef5; padding: 8px; background: #fafbfc; align-self: flex-start; }
.prev-h { font-size: 12px; color: #666; margin-bottom: 6px; }
.prev-banner { width: 100%; display: block; max-height: 90px; object-fit: cover; }
.ph { height: 60px; background: #e9ecf0; color: #b5bcc6; font-size: 22px; display: flex; align-items: center; justify-content: center; }
.group { border: 1px dashed #dcdfe6; padding: 10px 10px 0; margin-bottom: 10px; }
.group-h { font-size: 12px; color: #666; margin-bottom: 8px; display: flex; justify-content: space-between; }
.comp { text-align: center; background: #fff; padding: 10px; }
.comp img { width: 56px; height: 56px; border-radius: 4px; display: block; margin: 0 auto 6px; }
.consult { display: inline-block; background: #2bb24c; color: #fff; padding: 2px 18px; border-radius: 2px; font-size: 12px; }
.prods { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; background: #fff; padding: 6px; }
.prod img { width: 100%; height: 60px; object-fit: cover; display: block; }
.prod a { font-size: 12px; }
.saved { color: #2bb24c; font-size: 12px; }
.imp { padding: 6px 8px; cursor: pointer; }
.imp:hover, .imp.on { background: #ebf3ff; color: #3385ff; }
.mods :deep(.el-collapse-item__header) { background: #f7f8fa; padding-left: 12px; height: 38px; }
.mods :deep(.el-collapse-item__content) { padding: 14px 4px 6px; }
</style>
