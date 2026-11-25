<template>
  <div class="shell">
    <header class="top">
      <router-link to="/" class="brand">
        <span class="mark">P</span><span class="name">Phoebe</span><span class="sep" /><span class="product">知识营销</span>
        <span class="rebuild">重建版</span>
      </router-link>
      <nav class="nav">
        <template v-if="auth.hasAccount">
          <router-link v-for="n in accountNav" :key="n.key" :to="n.to" :class="{ on: nav === n.key }">{{ n.text }}</router-link>
        </template>
        <router-link to="/simulator" :class="{ on: nav === 'simulator' }">投放模拟</router-link>
        <router-link v-if="['AUDITOR','ADMIN'].includes(auth.role)" to="/audit" :class="{ on: nav === 'audit' }">审核台</router-link>
        <router-link v-if="auth.role==='ADMIN'" to="/admin" :class="{ on: nav === 'admin' }">系统管理</router-link>
      </nav>
      <div class="right">
        <router-link v-if="auth.hasAccount" to="/tools/wizard" class="wizard">智能引导<sup>NEW</sup></router-link>
        <el-select v-if="auth.role==='ADMIN'" v-model="acting" clearable placeholder="代登录广告账户" style="width:170px" @change="switchAccount">
          <el-option v-for="a in accounts" :key="a.id" :label="a.companyName" :value="a.id" />
        </el-select>
        <span v-if="auth.account" class="acc">{{ auth.account.companyName }}<span class="bal">余额 ¥{{ auth.account.balance }}</span></span>
        <el-dropdown @command="onCmd">
          <span class="user">{{ auth.user?.username }}<el-icon><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ roleText }}</el-dropdown-item>
              <el-dropdown-item command="about">关于重建</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main class="main">
      <router-view :key="route.path + reloadKey" />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../auth'
import http from '../api'

const route = useRoute()
const router = useRouter()
const accounts = ref([])
const acting = ref(auth.actingAccountId)
const reloadKey = ref(0)
const nav = computed(() => route.meta.nav)
const roleText = computed(() => ({ ADVERTISER: '广告主', AUDITOR: '审核员', ADMIN: '运营管理员' })[auth.role])
// 一级导航参考公开截图：推广管理 / 工具中心 / 数据报告 / 资源管理
const accountNav = [
  { key: 'home', text: '首页', to: '/home' },
  { key: 'promotion', text: '推广管理', to: '/promotion/plan' },
  { key: 'tools', text: '工具中心', to: '/tools' },
  { key: 'reports', text: '数据报告', to: '/reports' },
  { key: 'assets', text: '资源管理', to: '/assets' }
]

async function loadAccount() {
  const me = await http.get('/auth/me')
  auth.account = me.account
}
provide('refreshAccount', loadAccount)

async function switchAccount(id) {
  auth.actingAccountId = id || null
  auth.save()
  await loadAccount()
  reloadKey.value++
  router.push(id ? '/home' : '/admin')
}
function onCmd(c) {
  if (c === 'logout') { auth.logout(); router.push('/login') }
  if (c === 'about') router.push('/about')
}
onMounted(async () => {
  await loadAccount()
  if (auth.role === 'ADMIN') accounts.value = await http.get('/admin/accounts')
})
</script>

<style scoped>
.shell { min-height: 100%; display: flex; flex-direction: column; }
.top, .top * { white-space: nowrap; }
.top { overflow-x: auto; overflow-y: hidden; }
@media (max-width: 1100px) { .acc, .wizard, .rebuild { display: none; } .nav a { padding: 0 10px; } .brand { margin-right: 16px; } }
.top { height: 56px; background: #fff; border-bottom: 1px solid #e6e8eb; display: flex; align-items: center; padding: 0 20px; position: sticky; top: 0; z-index: 100; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.brand { display: flex; align-items: center; color: #333; text-decoration: none !important; margin-right: 36px; }
.mark { width: 24px; height: 24px; border-radius: 6px; background: linear-gradient(135deg, #3385ff, #1a5fd6); color: #fff; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; font-size: 15px; }
.name { font-size: 18px; font-weight: 700; margin-left: 6px; letter-spacing: .5px; }
.sep { width: 1px; height: 16px; background: #d0d3d9; margin: 0 10px; }
.product { font-size: 15px; color: #666; }
.rebuild { font-size: 11px; color: #ff9900; border: 1px solid #ffd699; border-radius: 2px; padding: 0 4px; margin-left: 6px; line-height: 16px; }
.nav { display: flex; height: 100%; }
.nav a { display: flex; align-items: center; padding: 0 18px; color: #333; font-size: 14px; border-bottom: 2px solid transparent; text-decoration: none; }
.nav a:hover { color: #3385ff; }
.nav a.on { color: #3385ff; border-bottom-color: #3385ff; }
.right { margin-left: auto; display: flex; align-items: center; gap: 16px; font-size: 12px; }
.wizard { color: #333; }
.wizard sup { color: #fff; background: #f2463d; border-radius: 6px; padding: 0 3px; font-size: 9px; margin-left: 2px; }
.acc { color: #666; }
.bal { margin-left: 8px; color: #333; font-weight: 600; }
.user { cursor: pointer; display: flex; align-items: center; gap: 2px; color: #333; }
.main { flex: 1; }
</style>
