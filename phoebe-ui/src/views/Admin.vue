<template>
  <div class="pb-page">
    <div class="pb-toolbar">
      <el-button type="primary" icon="Plus" @click="dlg = true">开户</el-button>
      <span class="muted">医疗服务、成人用品、彩票行业暂不开放（公开资料 A/C）。可在页头“代登录账户”中切换到某个广告账户进行操作。</span>
    </div>
    <el-table :data="accounts" border>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="companyName" label="企业名称" min-width="160" />
      <el-table-column prop="industry" label="行业" width="110" />
      <el-table-column prop="website" label="官网" min-width="180" />
      <el-table-column prop="balance" label="余额" width="110" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }"><el-button link type="primary" @click="recharge(row)">模拟充值</el-button></template>
      </el-table-column>
    </el-table>
    <h4>用户</h4>
    <el-table :data="users" border size="small">
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="displayName" label="名称" width="160" />
      <el-table-column prop="role" label="角色" width="120" />
      <el-table-column prop="accountId" label="账户ID" width="90" />
    </el-table>

    <el-dialog v-model="dlg" title="开户" width="520px">
      <el-form :model="f" label-width="100px">
        <el-form-item label="账户名"><el-input v-model="f.name" /></el-form-item>
        <el-form-item label="企业名称"><el-input v-model="f.companyName" /></el-form-item>
        <el-form-item label="行业">
          <el-select v-model="f.industry" filterable allow-create style="width:100%">
            <el-option v-for="i in ['美妆护肤','母婴用品','教育培训','家居家电','医疗服务','成人用品','彩票']" :key="i" :label="i" :value="i" />
          </el-select>
        </el-form-item>
        <el-form-item label="官网"><el-input v-model="f.website" placeholder="https://..." /></el-form-item>
        <el-form-item label="咨询链接"><el-input v-model="f.consultUrl" /></el-form-item>
        <el-form-item label="初始余额"><el-input-number v-model="f.initialBalance" :min="0" :step="100" /></el-form-item>
        <el-form-item label="登录用户名"><el-input v-model="f.username" /></el-form-item>
        <el-form-item label="登录密码"><el-input v-model="f.password" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dlg=false">取消</el-button><el-button type="primary" @click="create">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

const accounts = ref([])
const users = ref([])
const dlg = ref(false)
const f = reactive({ name: '', companyName: '', industry: '美妆护肤', website: '', consultUrl: '', initialBalance: 1000, username: '', password: '' })

async function load() { ;[accounts.value, users.value] = await Promise.all([http.get('/admin/accounts'), http.get('/admin/users')]) }
async function create() { await http.post('/admin/accounts', f); dlg.value = false; ElMessage.success('开户成功'); load() }
async function recharge(row) {
  const { value } = await ElMessageBox.prompt('充值金额（模拟）', `充值：${row.companyName}`, { inputPattern: /^\d+(\.\d{1,2})?$/, inputValue: '1000' })
  await http.post(`/admin/accounts/${row.id}/recharge`, { amount: Number(value) })
  load()
}
onMounted(load)
</script>
