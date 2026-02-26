<template>
  <div class="container feedback-wrap">
    <section class="card feedback-card fade-up">
      <p class="feedback-code">403</p>
      <h2>无权限访问</h2>
      <p class="muted">你已登录，但当前身份没有权限访问该页面。请切换账号或联系管理员开通权限。</p>
      <div class="feedback-actions">
        <router-link class="feedback-link" to="/login">返回登录页</router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.feedback-wrap {
  max-width: 760px;
  padding-top: 58px;
}

.feedback-card {
  text-align: center;
  padding: 34px 24px;
}

.feedback-code {
  margin: 0;
  font-size: 52px;
  font-weight: 800;
  line-height: 1;
  color: #d6336c;
}

.feedback-card h2 {
  margin: 10px 0;
}

.feedback-actions {
  margin-top: 14px;
}

.feedback-link {
  display: inline-block;
  padding: 9px 15px;
  border-radius: 10px;
  border: 1px solid #c7dced;
  background: #f7fbff;
  color: #294969;
  font-weight: 600;
  text-decoration: none;
}
</style>
