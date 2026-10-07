<template>
  <header class="topbar">
    <div class="nav-wrap">
      <RouterLink to="/applications" class="brand"><span class="brand-icon">W</span>workHelper<span class="brand-caption">求职助手</span></RouterLink>
      <nav aria-label="主导航"><RouterLink to="/home">首页提醒</RouterLink><RouterLink to="/applications">投递管理</RouterLink></nav>
      <span class="local-badge"><i></i> 本地工作空间</span>
    </div>
  </header>
  <main><RouterView /></main>
  <footer>workHelper · 让每一次机会，都有迹可循</footer>
</template>
