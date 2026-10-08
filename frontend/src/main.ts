import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import Applications from './pages/Applications.vue'
import Home from './pages/Home.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/applications', component: Applications },
    { path: '/home', component: Home },
    { path: '/:pathMatch(.*)*', redirect: '/applications' },
  ],
})
createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
