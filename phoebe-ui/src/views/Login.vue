<template>
  <div class="login">
    <header><span class="mark">P</span><b>Phoebe</b><span class="sep" />知识营销<span class="rebuild">重建版</span></header>
    <div class="hero">
      <div class="pitch">
        <div class="bar" />
        <h1>知识营销 CPC 业务端</h1>
        <p>问答广告 · 企业商业专属中间页 · 按问题竞价、入口点击计费</p>
        <p class="muted2">本系统根据公开资料重建，非百度原始系统；所有企业与数据均为虚构演示。</p>
      </div>
      <div class="card">
        <div class="title">账号登录</div>
        <el-form @submit.prevent="submit">
          <el-form-item><el-input v-model="username" placeholder="用户名" prefix-icon="User" size="large" /></el-form-item>
          <el-form-item><el-input v-model="password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password /></el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" size="large" style="width:100%">登 录</el-button>
        </el-form>
        <div class="demo">
          <div class="muted">测试账号（点击填入）</div>
          <a v-for="a in demo" :key="a.u" @click="fill(a)">{{ a.label }}<span>{{ a.u }} / {{ a.p }}</span></a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api'
import { auth } from '../auth'

const router = useRouter()
const username = ref('demo')
const password = ref('demo123')
const loading = ref(false)
const demo = [
  { label: '广告主 · 青禾美妆', u: 'demo', p: 'demo123' },
  { label: '广告主 · 悦颜护肤', u: 'demo2', p: 'demo123' },
  { label: '广告主 · 小树母婴', u: 'demo3', p: 'demo123' },
  { label: '审核员', u: 'auditor', p: 'auditor123' },
  { label: '运营管理员', u: 'admin', p: 'admin123' }
]
function fill(a) { username.value = a.u; password.value = a.p }
async function submit() {
  loading.value = true
  try {
    const r = await http.post('/auth/login', { username: username.value, password: password.value })
    auth.login(r.token, r.user)
    router.push('/')
  } finally { loading.value = false }
}
</script>

<style scoped>
.login { min-height: 100%; background: #fff; display: flex; flex-direction: column; }
header { height: 64px; display: flex; align-items: center; padding: 0 40px; font-size: 15px; color: #666; border-bottom: 1px solid #eee; }
header b { font-size: 20px; color: #333; margin-left: 6px; }
.mark { width: 26px; height: 26px; border-radius: 6px; background: linear-gradient(135deg, #3385ff, #1a5fd6); color: #fff; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
.sep { width: 1px; height: 16px; background: #d0d3d9; margin: 0 12px; }
.rebuild { font-size: 11px; color: #ff9900; border: 1px solid #ffd699; border-radius: 2px; padding: 0 4px; margin-left: 8px; }
.hero { flex: 1; display: flex; align-items: center; justify-content: center; gap: 0; background: linear-gradient(120deg, #0e1a33 0%, #1d3a6e 60%, #2f5fb3 100%); padding: 40px; }
.pitch { color: #fff; width: 520px; padding-right: 60px; }
.pitch .bar { width: 36px; height: 3px; background: #ff4d3a; margin-bottom: 18px; }
.pitch h1 { font-size: 32px; margin: 0 0 14px; font-weight: 600; }
.pitch p { color: rgba(255,255,255,.8); font-size: 15px; margin: 6px 0; }
.muted2 { font-size: 12px !important; color: rgba(255,255,255,.5) !important; margin-top: 18px !important; }
.card { width: 340px; background: #fff; padding: 28px 28px 20px; box-shadow: 0 8px 24px rgba(0,0,0,.2); }
.title { font-size: 16px; font-weight: 600; margin-bottom: 18px; }
.demo { margin-top: 16px; border-top: 1px solid #eee; padding-top: 10px; display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.demo a { cursor: pointer; display: flex; justify-content: space-between; color: #333; padding: 2px 0; text-decoration: none; }
.demo a:hover { color: #3385ff; }
.demo a span { color: #999; }
</style>
