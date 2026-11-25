<template>
  <div class="pb-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="企业知识管理" name="k">
        <div class="pb-toolbar"><el-button type="primary" icon="Plus" @click="editK(null)">新建企业知识</el-button><span class="muted">编辑答案时可将企业知识插入答案末尾</span></div>
        <el-table :data="knowledge" border>
          <el-table-column prop="title" label="标题" width="200" />
          <el-table-column prop="content" label="内容" min-width="300" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="editK(row)">编辑</el-button>
              <el-popconfirm title="删除？" @confirm="delK(row)"><template #reference><el-button link type="danger">删除</el-button></template></el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="图片管理" name="i">
        <div class="pb-toolbar">
          <el-select v-model="crop" placeholder="裁剪尺寸" clearable style="width:120px">
            <el-option v-for="r in ['1:1','4:3','16:9','3:1']" :key="r" :label="r" :value="r" />
          </el-select>
          <el-upload :show-file-list="false" accept="image/*" :http-request="upload"><el-button type="primary" icon="Upload">本地上传</el-button></el-upload>
          <el-button @click="recDlg = true">系统推荐</el-button>
          <span class="muted">系统提供 4 种裁剪尺寸（具体比例为重建示例），图片可在中间页、创意中快速引用</span>
        </div>
        <div class="imgs">
          <el-card v-for="img in images" :key="img.id" shadow="hover" class="img" :body-style="{ padding: '6px' }">
            <img :src="img.url" />
            <div class="muted">{{ img.name }} · {{ img.cropRatio || '原图' }} · {{ img.source === 'RECOMMEND' ? '系统推荐' : '本地上传' }}</div>
            <el-button link type="danger" size="small" @click="delImg(img)">删除</el-button>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="kDlg" :title="kf.id ? '编辑企业知识' : '新建企业知识'" width="520px">
      <el-form label-width="60px">
        <el-form-item label="标题"><el-input v-model="kf.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="kf.content" type="textarea" :rows="5" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="kDlg=false">取消</el-button><el-button type="primary" @click="saveK">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="recDlg" title="系统推荐图片" width="640px">
      <div class="imgs">
        <el-card v-for="r in recs" :key="r.url" class="img" :body-style="{ padding: '6px' }">
          <img :src="r.url" /><div class="muted">{{ r.name }}</div>
          <el-button size="small" @click="addRec(r)">添加到图片库</el-button>
        </el-card>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const tab = ref('k')
const knowledge = ref([])
const images = ref([])
const recs = ref([])
const crop = ref(null)
const kDlg = ref(false)
const recDlg = ref(false)
const kf = reactive({ id: null, title: '', content: '' })

async function load() {
  ;[knowledge.value, images.value, recs.value] = await Promise.all([http.get('/knowledge'), http.get('/images'), http.get('/images/recommendations')])
}
function editK(row) { Object.assign(kf, row ? { id: row.id, title: row.title, content: row.content } : { id: null, title: '', content: '' }); kDlg.value = true }
async function saveK() {
  if (kf.id) await http.put(`/knowledge/${kf.id}`, kf)
  else await http.post('/knowledge', kf)
  kDlg.value = false
  load()
}
async function delK(row) { await http.delete(`/knowledge/${row.id}`); load() }
async function upload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  if (crop.value) fd.append('cropRatio', crop.value)
  await http.post('/images', fd)
  ElMessage.success('已上传')
  load()
}
async function addRec(r) { await http.post('/images/recommend', r); ElMessage.success('已添加'); load() }
async function delImg(img) { await http.delete(`/images/${img.id}`); load() }
onMounted(load)
</script>

<style scoped>
.imgs { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 10px; }
.img img { width: 100%; height: 110px; object-fit: contain; background: #fafafa; }
</style>
