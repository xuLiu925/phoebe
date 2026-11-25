<template>
  <div class="pb-page">
    <div class="pb-crumb"><a @click="router.back()">返回</a> › <b>{{ id ? '编辑推广计划' : '新建推广计划' }}</b></div>
    <div class="pb-panel" style="padding:24px 32px">
      <el-form :model="f" label-width="130px" class="pb-form" style="max-width:860px">
        <el-form-item label="推广计划名称：" required>
          <el-input v-model="f.name" placeholder="推广计划名称" maxlength="30" style="width:280px" />
          <span class="hint">不能超过30个字符</span>
        </el-form-item>
        <el-form-item label="推广计划预算：" required>
          <el-radio-group v-model="unlimited">
            <el-radio :value="true">不限预算</el-radio>
            <el-radio :value="false">日预算（元）</el-radio>
          </el-radio-group>
          <el-input-number v-if="!unlimited" v-model="f.budget" :min="50" :step="50" :precision="2" style="margin-left:12px;width:150px" />
          <span class="hint">预算消耗完后当日停止展现</span>
        </el-form-item>
        <el-form-item label="移动出价比例：" required>
          <el-input-number v-model="f.mobileBidRatio" :min="0.1" :max="10" :step="0.1" :precision="2" :controls="false" style="width:280px" />
          <span class="hint">请输入0.10~10.00之间，保留2位小数的数值。移动端出价 = 出价 × 比例</span>
        </el-form-item>
        <el-form-item label="设置推广电话：">
          <el-input v-model="f.mobilePhone" placeholder="电话" style="width:280px" />
          <span class="hint">建议填写推广电话，以提升无线投放效果</span>
        </el-form-item>
        <el-form-item label="投放时段：" required>
          <div>
            <el-radio-group v-model="allTime">
              <el-radio :value="true">不限时间</el-radio>
              <el-radio :value="false">自选时间</el-radio>
            </el-radio-group>
            <ScheduleGrid v-if="!allTime" v-model="schedule" style="margin-top:8px" />
          </div>
        </el-form-item>
        <el-form-item label="投放地域：" required>
          <div>
            <el-radio-group v-model="allRegion">
              <el-radio :value="true">不限地域</el-radio>
              <el-radio :value="false">自选地域</el-radio>
            </el-radio-group>
            <div v-if="!allRegion" class="regions">
              <el-checkbox-group v-model="f.regions">
                <el-checkbox v-for="p in PROVINCES" :key="p" :value="p" :label="p" />
              </el-checkbox-group>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="IP排除：">
          <div>
            <el-radio-group v-model="allIp">
              <el-radio :value="true">不限IP</el-radio>
              <el-radio :value="false">排除IP</el-radio>
            </el-radio-group>
            <div v-if="!allIp" style="margin-top:8px">
              <el-input v-model="ipText" type="textarea" :rows="5" placeholder="每行一个，例如：&#10;202.108.22.5&#10;61.135.169.*" style="width:360px" />
              <div class="muted">支持精确 IP 与 IP 段（末尾用 * 通配，如 61.135.169.*），最多 100 条。来自被排除 IP 的检索不展现本计划的问答。</div>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </div>
    <div class="pb-form-actions">
      <el-button type="primary" @click="save('page')" v-if="!id">保存并新建中间页</el-button>
      <el-button :type="id ? 'primary' : ''" @click="save('list')">{{ id ? '保存' : '仅保存' }}</el-button>
      <el-button @click="router.back()">取消</el-button>
      <span class="muted">投放流程：新建计划 → 新建中间页（审核）→ 新建单元 → 编写问答（审核）→ 竞价展现</span>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'
import { PROVINCES } from '../labels'
import ScheduleGrid from '../components/ScheduleGrid.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const f = reactive({ name: '', budget: 300, mobileBidRatio: 1, regions: [], mobilePhone: '' })
const unlimited = ref(true)
const allRegion = ref(true)
const allTime = ref(true)
const schedule = ref('1'.repeat(168))
const allIp = ref(true)
const ipText = ref('')

onMounted(async () => {
  if (!id) return
  const { plan: p } = await http.get(`/plans/${id}`)
  Object.assign(f, { name: p.name, budget: p.budget ?? 300, mobileBidRatio: p.mobileBidRatio, regions: p.regions ? p.regions.split(',') : [], mobilePhone: p.mobilePhone || '' })
  unlimited.value = p.budget == null
  allRegion.value = !p.regions
  allTime.value = !p.schedule
  if (p.schedule) schedule.value = p.schedule
  allIp.value = !p.excludedIps
  ipText.value = (p.excludedIps || '').split(',').filter(Boolean).join(String.fromCharCode(10))
})

async function save(next) {
  const body = {
    ...f, budget: unlimited.value ? null : f.budget, regions: allRegion.value ? [] : f.regions,
    schedule: allTime.value ? null : schedule.value,
    excludedIps: allIp.value ? [] : ipText.value.split(/[,，\s]+/).filter(Boolean)
  }
  const p = id ? await http.put(`/plans/${id}`, body) : await http.post('/plans', body)
  ElMessage.success('保存成功')
  if (next === 'page') router.push({ path: '/middle-pages/new', query: { planId: p.id } })
  else router.push({ path: '/promotion/plan', query: id ? {} : { planId: p.id } })
}
</script>

<style scoped>
.regions { margin-top: 8px; padding: 10px 12px; border: 1px solid #ebeef5; background: #fafbfc; width: 620px; }
.regions .el-checkbox { width: 80px; margin-right: 6px; }
</style>
