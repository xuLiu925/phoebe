import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
for (const [k, c] of Object.entries(Icons)) app.component(k, c)
// 广告投放后台信息密度高：全局使用 small 尺寸
app.use(ElementPlus, { locale: zhCn, size: 'small' }).use(router).mount('#app')
