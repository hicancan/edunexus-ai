<template>
  <div class="app-container">
    <section class="panel feedback-panel">
      <p class="feedback-code">403</p>
      <h1 class="workspace-title">无权限访问</h1>
      <p class="workspace-subtitle">当前账号没有权限访问该页面，请切换账号或联系管理员。</p>
      <router-link class="btn secondary feedback-link" to="/login">返回登录页</router-link>
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
  color: var(--color-danger);
}

.feedback-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
}
</style>
