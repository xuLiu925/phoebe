import { reactive } from 'vue'

const saved = JSON.parse(localStorage.getItem('phoebe-auth') || '{}')

export const auth = reactive({
  token: saved.token || '',
  user: saved.user || null,
  actingAccountId: saved.actingAccountId || null,
  account: null,
  save() {
    localStorage.setItem('phoebe-auth', JSON.stringify({ token: this.token, user: this.user, actingAccountId: this.actingAccountId }))
  },
  login(token, user) {
    this.token = token
    this.user = user
    this.actingAccountId = null
    this.save()
  },
  logout() {
    this.token = ''
    this.user = null
    this.account = null
    this.actingAccountId = null
    localStorage.removeItem('phoebe-auth')
  },
  get role() { return this.user?.role },
  /** 是否有广告账户上下文（广告主，或代登录的管理员） */
  get hasAccount() { return !!(this.user?.accountId || this.actingAccountId) }
})
