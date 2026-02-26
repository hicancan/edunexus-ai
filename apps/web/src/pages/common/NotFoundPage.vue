<template>
  <div class="app-container">
    <section class="panel feedback-panel">
      <p class="feedback-code">404</p>
      <h1 class="workspace-title">页面不存在</h1>
      <p class="workspace-subtitle">你访问的地址无效或已变更，请返回登录页重新进入系统。</p>
      <router-link class="btn secondary feedback-link" to="/login">前往登录页</router-link>
    </section>
  </div>
</template>

<style scoped>
.feedback-panel {
  max-width: 760px;
  margin: 60px auto;
  text-align: center;
}

.feedback-code {
  margin: 0;
  font-size: 3.5rem;
  line-height: 1;
  font-weight: 800;
  color: var(--color-primary);
}

.feedback-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
}
</style>
