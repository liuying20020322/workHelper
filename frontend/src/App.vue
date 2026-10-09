<script setup lang="ts">
import { ref } from 'vue'
import BackupPanel from './components/BackupPanel.vue'
const backupOpen = ref(false)
const dataEpoch = ref(0)
</script>
<template>
  <header class="topbar">
    <div class="nav-wrap">
      <RouterLink to="/applications" class="brand"><span class="brand-icon">W</span>workHelper<span class="brand-caption">求职助手</span></RouterLink>
      <nav aria-label="主导航"><RouterLink to="/home">首页提醒</RouterLink><RouterLink to="/applications">投递管理</RouterLink><RouterLink to="/unapplied">看过未投</RouterLink></nav>
      <div class="auxiliary"><span class="local-badge"><i></i> 本地工作空间</span><el-button text @click="backupOpen = true">备份与恢复</el-button></div>
    </div>
  </header>
  <main><RouterView :key="dataEpoch" /></main>
  <BackupPanel v-if="backupOpen" @closed="backupOpen = false" @restored="dataEpoch++" />
  <footer>workHelper · 让每一次机会，都有迹可循</footer>
</template>
<style scoped>
.nav-wrap{gap:28px;flex-wrap:wrap}nav a{white-space:nowrap}.auxiliary{display:flex;align-items:center;gap:12px;margin-left:auto}.auxiliary .el-button{font-size:12px}@media(max-width:720px){.auxiliary{margin-left:0}.nav-wrap{gap:8px 16px;padding-top:8px;padding-bottom:8px}nav{min-height:40px}.auxiliary .el-button{padding:6px}}
</style>
