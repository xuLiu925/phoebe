import { createRouter, createWebHashHistory } from 'vue-router'
import { auth } from './auth'
import Layout from './components/Layout.vue'

const acc = { account: true }
const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  {
    path: '/',
    component: Layout,
    children: [
      { path: '', redirect: () => (auth.role === 'AUDITOR' ? '/audit' : auth.role === 'ADMIN' && !auth.hasAccount ? '/admin' : '/home') },
      { path: 'home', component: () => import('./views/Home.vue'), meta: { ...acc, nav: 'home' } },
      { path: 'promotion', redirect: '/promotion/plan' },
      { path: 'promotion/:level(plan|unit|page|qa|creative)', component: () => import('./views/Promotion.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'plans/new', component: () => import('./views/PlanEdit.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'plans/:id/edit', component: () => import('./views/PlanEdit.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'units/new', component: () => import('./views/UnitEdit.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'units/:id/edit', component: () => import('./views/UnitEdit.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'qas/new', component: () => import('./views/QaCreate.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'creatives/new', component: () => import('./views/CreativeEdit.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'creatives/:id/edit', component: () => import('./views/CreativeEdit.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'middle-pages/new', component: () => import('./views/MiddlePageEditor.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'middle-pages/:id', component: () => import('./views/MiddlePageEditor.vue'), meta: { ...acc, nav: 'promotion' } },
      { path: 'tools', component: () => import('./views/ToolCenter.vue'), meta: { ...acc, nav: 'tools' } },
      { path: 'tools/oplog', component: () => import('./views/OpLog.vue'), meta: { ...acc, nav: 'tools' } },
      { path: 'tools/bid', component: () => import('./views/BidAssistant.vue'), meta: { ...acc, nav: 'tools' } },
      { path: 'tools/wizard', component: () => import('./views/Wizard.vue'), meta: { ...acc, nav: 'tools' } },
      { path: 'tools/rules', component: () => import('./views/Rules.vue'), meta: { nav: 'tools' } },
      { path: 'reports', component: () => import('./views/Reports.vue'), meta: { ...acc, nav: 'reports' } },
      { path: 'assets', component: () => import('./views/Assets.vue'), meta: { ...acc, nav: 'assets' } },
      { path: 'simulator', component: () => import('./views/Simulator.vue'), meta: { nav: 'simulator' } },
      { path: 'audit', component: () => import('./views/Audit.vue'), meta: { roles: ['AUDITOR', 'ADMIN'], nav: 'audit' } },
      { path: 'admin', component: () => import('./views/Admin.vue'), meta: { roles: ['ADMIN'], nav: 'admin' } },
      { path: 'about', component: () => import('./views/About.vue'), meta: { nav: 'about' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHashHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach(to => {
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) return '/'
  if (to.meta.account && !auth.hasAccount) return auth.role === 'ADMIN' ? '/admin' : '/audit'
})

export default router
