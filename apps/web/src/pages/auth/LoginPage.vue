<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { roleHomePath } from "../../app/router/routes";
import { getFirstIssueMessage, loginSchema } from "../../schemas/auth.schemas";
import { login as loginApi } from "../../services/auth.service";
import { toErrorMessage } from "../../services/error-message";
import { useAuthStore } from "../../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const username = ref("");
const password = ref("");
const loading = ref(false);
const error = ref("");

async function submitLogin(): Promise<void> {
  error.value = "";
  const parsed = loginSchema.safeParse({
    username: username.value,
    password: password.value
  });

  if (!parsed.success) {
    error.value = getFirstIssueMessage(parsed);
    return;
  }

  loading.value = true;
  try {
    const result = await loginApi(parsed.data);
    auth.setSession(result);
    const redirect =
      typeof route.query.redirect === "string" && route.query.redirect.startsWith("/")
        ? route.query.redirect
        : roleHomePath[result.user.role || "STUDENT"];
    await router.replace(redirect);
  } catch (requestError) {
    error.value = toErrorMessage(requestError, "登录失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="app-container auth-layout">
    <section class="panel auth-intro">
      <p class="auth-kicker">EduNexus AI</p>
      <h1>学习、教学、治理一体化工作台</h1>
      <p class="panel-note">
        学生可进行问答、做题与 AI 出题，教师管理知识库并生成教案，管理员可治理用户与资源。
      </p>
      <div class="auth-accounts" aria-label="默认账号提示">
        <span>管理员：admin / 12345678</span>
        <span>教师：teacher01 / 12345678</span>
        <span>学生：student01 / 12345678</span>
      </div>
    </section>

    <section class="panel">
      <h2 class="panel-title">登录系统</h2>
      <p class="panel-note">请输入用户名和密码。</p>

      <div class="form-grid">
        <div class="field-block">
          <label for="login-username">用户名</label>
          <input
            id="login-username"
            v-model="username"
            autocomplete="username"
            placeholder="请输入用户名"
            @keyup.enter="submitLogin"
          />
        </div>
        <div class="field-block">
          <label for="login-password">密码</label>
          <input
            id="login-password"
            v-model="password"
            autocomplete="current-password"
            type="password"
            placeholder="请输入密码"
            @keyup.enter="submitLogin"
          />
        </div>
      </div>

      <div class="auth-actions">
        <button class="btn" type="button" :disabled="loading" aria-label="登录" @click="submitLogin">
          {{ loading ? "登录中..." : "登录" }}
        </button>
        <router-link to="/register">没有账号？立即注册</router-link>
      </div>

      <p v-if="error" class="status-box error" role="alert">{{ error }}</p>
    </section>
  </div>
</template>

<style scoped>
.auth-layout {
  min-height: calc(100vh - 20px);
  display: grid;
  align-items: center;
  grid-template-columns: 1.05fr 1fr;
  gap: 16px;
}

.auth-intro {
  background:
    radial-gradient(circle at 82% 15%, rgba(23, 103, 173, 0.17), transparent 45%),
    radial-gradient(circle at 9% 90%, rgba(45, 139, 67, 0.13), transparent 45%),
    var(--color-surface);
}

.auth-kicker {
  display: inline-flex;
  margin: 0;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid var(--color-border-strong);
  color: #35597b;
  font-size: 0.8rem;
  font-weight: 700;
  background: #edf6ff;
}

.auth-intro h1 {
  margin: 12px 0 10px;
  font-size: 1.95rem;
  line-height: 1.3;
}

.auth-accounts {
  display: grid;
  gap: 8px;
  margin-top: 18px;
}

.auth-accounts span {
  display: block;
  border: 1px solid #d4e4f2;
  border-radius: var(--radius-sm);
  background: #f3f9ff;
  color: #375c7f;
  padding: 7px 10px;
  font-size: 0.85rem;
}

.auth-actions {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 1279px) {
  .auth-layout {
    min-height: auto;
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}

@media (max-width: 767px) {
  .auth-intro h1 {
    font-size: 1.56rem;
  }

  .auth-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
