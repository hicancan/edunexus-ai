<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { NCard, NForm, NFormItem, NInput, NButton, NAlert, NText, NSpace, useMessage } from "naive-ui";
import { roleHomePath } from "../../app/router/routes";
import { getFirstIssueMessage, loginSchema } from "../../features/auth/model/auth.schemas";
import { login as loginApi } from "../../features/auth/api/auth.service";
import { toErrorMessage } from "../../services/error-message";
import { useAuthStore } from "../../features/auth/model/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const message = useMessage();

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
    message.success("登录成功");
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
  <div class="auth-layout">
    <div class="auth-intro">
      <div class="intro-content">
        <n-text depth="3" class="auth-kicker">EduNexus AI</n-text>
        <h1 class="intro-title">学习、教学、治理<br />一体化工作台</h1>
        <p class="intro-desc">
          学生可进行问答、做题与 AI 出题，教师管理知识库并生成教案，管理员可治理用户与资源。
        </p>
        <div class="auth-accounts">
          <n-text depth="3" class="account-hint">测试账号提示</n-text>
          <div class="account-list">
            <span class="account-badge glass-pill">管理员：admin / 12345678</span>
            <span class="account-badge glass-pill">教师：teacher01 / 12345678</span>
            <span class="account-badge glass-pill">学生：student01 / 12345678</span>
          </div>
        </div>
      </div>
    </div>

    <div class="auth-form-area">
      <n-card class="login-card glass-card" :bordered="false" size="large">
        <template #header>
          <n-text tag="h2" class="login-title">全域单点登录</n-text>
        </template>
        <n-space vertical :size="20">
          <n-alert v-if="error" type="error" :show-icon="false">
            {{ error }}
          </n-alert>

          <n-form @submit.prevent="submitLogin">
            <n-form-item label="用户名" path="username">
              <n-input
                v-model:value="username"
                placeholder="请输入用户名"
                @keyup.enter="submitLogin"
                size="large"
              />
            </n-form-item>
            <n-form-item label="密码" path="password">
              <n-input
                v-model:value="password"
                type="password"
                show-password-on="click"
                placeholder="请输入密码"
                @keyup.enter="submitLogin"
                size="large"
              />
            </n-form-item>
            <div class="form-actions">
              <n-button
                type="primary"
                attr-type="submit"
                :loading="loading"
                block
                size="large"
                class="animate-pop hover-glow"
                @click="submitLogin"
              >
                授权连接
              </n-button>
            </div>
          </n-form>

          <div class="form-footer">
            <n-text depth="3">尚未注册身份拓扑？</n-text>
            <n-button text type="primary" class="animate-pop" @click="router.push('/register')">
              立即注册
            </n-button>
          </div>
        </n-space>
      </n-card>
    </div>
  </div>
</template>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background-color: var(--color-bg-light);
  background-image:
    radial-gradient(at 0% 0%, hsla(237, 100%, 85%, 0.4) 0px, transparent 50%),
    radial-gradient(at 100% 100%, hsla(237, 80%, 90%, 0.3) 0px, transparent 50%);
}

.auth-intro {
  display: flex;
  align-items: center;
  justify-content: center;
}

.intro-content {
  max-width: 480px;
  padding: 40px;
}

.auth-kicker {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 16px;
  background-color: rgba(5, 80, 140, 0.08);
  color: var(--color-primary);
  font-weight: 600;
  font-size: 0.85rem;
  margin-bottom: 16px;
}

.intro-title {
  margin: 0 0 16px;
  font-size: 2.8rem;
  line-height: 1.2;
  font-weight: 800;
  background: linear-gradient(135deg, var(--color-primary) 0%, #1e293b 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.intro-desc {
  font-size: 1.1rem;
  line-height: 1.6;
  color: #4b6278;
  margin-bottom: 40px;
}

.account-hint {
  font-size: 0.9rem;
  margin-bottom: 12px;
  display: block;
}

.account-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.account-badge {
  display: inline-block;
  padding: 8px 16px;
  font-family: var(--font-code);
  font-size: 0.9rem;
  color: var(--color-primary);
  font-weight: 600;
}

.auth-form-area {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.04);
}

.login-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
}

.form-footer {
  text-align: center;
  margin-top: 16px;
}

@media (max-width: 991px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }
  .auth-intro {
    padding: 60px 20px 40px;
    align-items: flex-end;
  }
  .auth-form-area {
    align-items: flex-start;
  }
}
</style>
