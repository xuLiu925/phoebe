<template>
  <div class="pb-page">
    <div class="pb-crumb"><a @click="router.back()">返回</a> › <b>{{ id ? '编辑高级创意' : '新建高级创意' }}</b></div>
    <div class="wrap">
      <div class="pb-panel form">
        <div class="pb-panel-title">单图创意</div>
        <el-form label-width="110px" class="pb-form">
          <el-form-item label="推广单元：" required>
            <el-select v-model="unitId" :disabled="!!id" placeholder="请选择" style="width:300px" @change="loadQas">
              <el-option v-for="u in units" :key="u.unit.id" :label="u.planName + ' / ' + u.unit.name" :value="u.unit.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="创意名称：" required><el-input v-model="f.title" placeholder="例如：XX修护眼霜" style="width:300px" /></el-form-item>
          <div class="grey">
            <el-form-item label="企业产品图片：" required>
              <div class="pick">
                <div v-for="img in images" :key="img.id" :class="['opt', { on: f.imageUrls[0] === img.url }]" @click="f.imageUrls = [img.url]">
                  <img :src="img.url" /><span>{{ img.cropRatio || '原图' }}</span>
                </div>
                <router-link to="/assets" class="opt add">+ 图片管理</router-link>
              </div>
            </el-form-item>
            <el-form-item label="企业产品描述：" required>
              <el-input v-model="f.description" type="textarea" :rows="3" maxlength="80" show-word-limit style="width:360px" />
            </el-form-item>
          </div>
          <el-form-item label="落地页链接："><el-input v-model="f.targetUrl" placeholder="https://" style="width:360px" /></el-form-item>
          <el-form-item label="关联问答：">
            <el-select v-model="f.qaIds" multiple placeholder="可选：关联同单元下的问答" style="width:360px">
              <el-option v-for="q in qas" :key="q.id" :label="q.question" :value="q.id" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <div class="pb-panel preview">
        <div class="pb-panel-title">单图样式（259×194px）</div>
        <div class="card">
          <img v-if="f.imageUrls[0]" :src="f.imageUrls[0]" />
          <div v-else class="ph">产品图片</div>
          <div class="desc">{{ f.description || '企业产品描述' }}</div>
        </div>
        <p class="muted">适用于 PC 搜索结果右侧“单图+问答”（华表位）样式<span class="evidence">公开资料 A/C</span></p>
      </div>
    </div>
    <div class="pb-form-actions">
      <el-button type="primary" @click="save">保存并提交</el-button>
      <el-button @click="router.back()">返回上一步</el-button>
      <el-button @click="router.push('/promotion/creative')">取消</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const units = ref([])
const images = ref([])
const qas = ref([])
const unitId = ref(route.query.unitId ? Number(route.query.unitId) : null)
const f = reactive({ title: '', description: '', imageUrls: [], targetUrl: '', qaIds: [] })

async function loadQas() { qas.value = unitId.value ? await http.get(`/units/${unitId.value}/qas`) : [] }
onMounted(async () => {
  ;[units.value, images.value] = await Promise.all([http.get('/units'), http.get('/images')])
  if (id) {
    const c = await http.get(`/creatives/${id}`)
    unitId.value = c.unitId
    Object.assign(f, { title: c.title, description: c.description, imageUrls: c.imageUrls || [], targetUrl: c.targetUrl, qaIds: c.qaIds || [] })
  }
  loadQas()
})
async function save() {
  if (!unitId.value) return ElMessage.warning('请选择推广单元')
  if (id) await http.put(`/creatives/${id}`, f)
  else await http.post(`/units/${unitId.value}/creatives`, f)
  ElMessage.success('已提交审核')
  router.push({ path: '/promotion/creative', query: { unitId: unitId.value } })
}
</script>

<style scoped>
.wrap { display: flex; gap: 12px; align-items: flex-start; }
.form { flex: 1; }
.preview { width: 320px; }
.grey { background: #f7f8fa; padding: 16px 0 1px; margin-bottom: 22px; }
.pick { display: flex; flex-wrap: wrap; gap: 8px; }
.opt { width: 86px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; text-align: center; font-size: 11px; color: #999; }
.opt img { width: 100%; height: 64px; object-fit: cover; display: block; }
.opt.on { border-color: #3385ff; box-shadow: 0 0 0 1px #3385ff; }
.opt.add { display: flex; align-items: center; justify-content: center; height: 82px; }
.card { width: 259px; border: 1px solid #e6e8eb; padding: 8px; }
.card img, .ph { width: 100%; height: 160px; object-fit: cover; display: block; background: #f2f3f5; color: #bbb; line-height: 160px; text-align: center; }
.desc { font-size: 12px; color: #666; margin-top: 6px; text-align: center; }
</style>
