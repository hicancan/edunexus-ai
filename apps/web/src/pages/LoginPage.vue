<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import api from "../services/api";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const auth = useAuthStore();
const username = ref("student01");
const password = ref("12345678");
const loading = ref(false);
const error = ref("");

async function login() {
  error.value = "";
  loading.value = true;
  try {
    const res = await api.post("/auth/login", { username: username.value, password: password.value });
    const data = res.data.data;
    auth.setAuth(data.accessToken, data.user);
    const rolePath = { STUDENT: "/student", TEACHER: "/teacher", ADMIN: "/admin" }[data.user.role] || "/login";
    router.push(rolePath);
  } catch (e) {
    error.value = e?.response?.data?.message || "登录失败";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="container auth-container">
    <div class="auth-grid fade-up">
      <section class="auth-intro card">
        <p class="intro-pill">EduNexus AI</p>
        <h1>清爽、智能、可追踪的学习体验</h1>
        <p class="muted">
          面向学生、教师、管理员的一体化平台。支持智能问答、AI 出题、教案生成和学情分析，让教学与学习形成完整闭环。
        </p>
        <div class="account-list">
          <span>管理员：admin / 12345678</span>
          <span>教师：teacher01 / 12345678</span>
          <span>学生：student01 / 12345678</span>
        </div>
      </section>

      <section class="card auth-form-card">
        <h2>登录系统</h2>
        <p class="muted">欢迎回来，请输入账号信息。</p>
        <div class="form-stack">
          <div>
            <label>用户名</label>
            <input v-model="username" placeholder="请输入用户名" />
          </div>
          <div>
            <label>密码</label>
            <input v-model="password" type="password" placeholder="请输入密码" />
          </div>
          <button :disabled="loading" @click="login">{{ loading ? "登录中..." : "登录" }}</button>
          <router-link to="/register">没有账号？立即注册</router-link>
        </div>
        <p v-if="error" class="status-error">{{ error }}</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-container {
  max-width: 1100px;
  min-height: calc(100vh - 40px);
  display: flex;
  align-items: center;
}

.auth-grid {
  width: 100%;
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 18px;
}

.auth-intro {
  padding: 28px;
  background:
    radial-gradient(circle at 85% 10%, rgba(24, 100, 171, 0.15), transparent 44%),
    radial-gradient(circle at 8% 90%, rgba(43, 138, 62, 0.12), transparent 45%),
    #ffffff;
}

.intro-pill {
  display: inline-block;
  margin: 0;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #c7dced;
  background: #eef6ff;
  color: #375877;
  font-weight: 700;
  font-size: 12px;
}

.auth-intro h1 {
  margin: 14px 0 10px;
  font-size: 30px;
  line-height: 1.35;
}

.account-list {
  margin-top: 20px;
  display: grid;
  gap: 8px;
}

.account-list span {
  display: block;
  font-size: 14px;
  color: #3d5d7b;
  background: #f4faff;
  border: 1px solid #d6e8f7;
  border-radius: 10px;
  padding: 8px 10px;
}

.auth-form-card {
  padding: 24px;
}

.auth-form-card h2 {
  margin: 0 0 4px;
}

.form-stack {
  margin-top: 14px;
  display: grid;
  gap: 11px;
}

@media (max-width: 900px) {
  .auth-container {
    min-height: auto;
    padding-top: 14px;
  }

  .auth-grid {
    grid-template-columns: 1fr;
  }

  .auth-intro h1 {
    font-size: 24px;
  }
}
</style>
