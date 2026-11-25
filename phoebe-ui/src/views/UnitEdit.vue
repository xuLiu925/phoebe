<template>
  <div class="pb-page">
    <div class="pb-crumb"><a @click="router.back()">返回</a> › <b>{{ id ? '编辑推广单元' : '新建推广单元' }}</b></div>
    <div class="pb-panel" style="padding:24px 32px">
      <el-form label-width="130px" class="pb-form" style="max-width:860px">
        <el-form-item label="推广计划：" required>
          <el-select v-model="planId" :disabled="!!id" placeholder="请选择" style="width:280px">
            <el-option v-for="p in plans" :key="p.plan.id" :label="p.plan.name" :value="p.plan.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="推广单元名称：" required>
          <el-input v-model="f.name" placeholder="推广单元名称" maxlength="30" style="width:280px" />
          <span class="hint">不能超过30个字符</span>
        </el-form-item>
        <el-form-item label="单元出价：" required>
          <el-input-number v-model="f.bid" :min="0.3" :max="999.99" :step="0.1" :precision="2" :controls="false" style="width:280px" />
          <span class="hint">元/点击。作为单元下问答的默认出价，问答可单独设置出价</span>
        </el-form-item>
        <el-form-item label="订阅词：">
          <div style="width:520px">
            <el-select v-model="f.subscribeWords" multiple filterable allow-create default-first-option :reserve-keyword="false"
              placeholder="输入订阅词后回车" style="width:100%" />
            <div class="muted" style="line-height:1.7;margin-top:4px">
              订阅词是持续性推荐：订阅后每次进入新建问答，系统都会推荐相关的高 PV 问题；关键词推荐是一次性工具。<span class="evidence">公开资料 B</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </div>
    <div class="pb-form-actions">
      <el-button v-if="!id" type="primary" @click="save('qa')">保存并新建问答</el-button>
      <el-button :type="id ? 'primary' : ''" @click="save('list')">{{ id ? '保存' : '仅保存' }}</el-button>
      <el-button @click="router.back()">取消</el-button>
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
const plans = ref([])
const planId = ref(route.query.planId ? Number(route.query.planId) : null)
const f = reactive({ name: '', bid: 1, subscribeWords: [] })

onMounted(async () => {
  plans.value = await http.get('/plans')
  if (id) {
    const u = await http.get(`/units/${id}`)
    planId.value = u.unit.planId
    Object.assign(f, { name: u.unit.name, bid: u.unit.bid, subscribeWords: [...u.subscribeWords] })
  }
})

async function save(next) {
  if (!planId.value) return ElMessage.warning('请选择推广计划')
  const u = id ? await http.put(`/units/${id}`, f) : await http.post(`/plans/${planId.value}/units`, f)
  ElMessage.success('保存成功')
  if (next === 'qa') router.push({ path: '/qas/new', query: { unitId: u.id } })
  else router.push({ path: '/promotion/unit', query: { planId: planId.value } })
}
</script>
