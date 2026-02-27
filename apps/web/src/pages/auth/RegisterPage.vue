<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { NCard, NForm, NFormItem, NInput, NSelect, NButton, NAlert, NText, NSpace, useMessage } from "naive-ui";
import { getFirstIssueMessage, registerSchema } from "../../features/auth/model/auth.schemas";
import { register as registerApi } from "../../features/auth/api/auth.service";
import { toErrorMessage } from "../../services/error-message";

const router = useRouter();
const message = useMessage();

const username = ref("");
const password = ref("");
const role = ref<"STUDENT" | "TEACHER">("STUDENT");
const email = ref("");
const phone = ref("");
const loading = ref(false);
const error = ref("");

const roleOptions = [
  { label: "学生 STUDENT", value: "STUDENT" },
  { label: "教师 TEACHER", value: "TEACHER" }
];

async function submitRegister(): Promise<void> {
  error.value = "";

  const parsed = registerSchema.safeParse({
    username: username.value,
    password: password.value,
    role: role.value,
    email: email.value,
    phone: phone.value
  });

  if (!parsed.success) {
    error.value = getFirstIssueMessage(parsed);
    return;
  }

  loading.value = true;
  try {
    await registerApi({
      username: parsed.data.username,
      password: parsed.data.password,
      role: parsed.data.role,
      email: parsed.data.email || undefined,
      phone: parsed.data.phone || undefined
    });
    message.success("注册成功，请使用新账号登录");
    setTimeout(() => {
      router.push("/login");
    }, 700);
  } catch (requestError) {
    error.value = toErrorMessage(requestError, "注册失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="register-layout">
    <div class="register-container">
      <n-card class="register-card glass-card" :bordered="false" size="large">
        <template #header>
          <div class="header-titles">
            <n-text tag="h1" class="main-title">拓扑身份注册</n-text>
            <n-text depth="3" class="sub-title">支持学生与教师自助接入，管理员中枢须由内网分配。</n-text>
          </div>
        </template>

        <n-space vertical :size="20">
          <n-alert v-if="error" type="error" :show-icon="false">
            {{ error }}
          </n-alert>

          <n-form @submit.prevent="submitRegister">
            <n-form-item label="用户名" path="username">
              <n-input v-model:value="username" placeholder="3-50 位字母、数字或下划线" size="large" />
            </n-form-item>
            <n-form-item label="身份类型" path="role">
              <n-select v-model:value="role" :options="roleOptions" size="large" />
            </n-form-item>
            <n-form-item label="密码" path="password">
              <n-input
                v-model:value="password"
                type="password"
                show-password-on="click"
                placeholder="8-64 位"
                size="large"
              />
            </n-form-item>
            <n-form-item label="手机号（可选）" path="phone">
              <n-input v-model:value="phone" placeholder="用于找回账号" size="large" />
            </n-form-item>
            <n-form-item label="邮箱（可选）" path="email">
              <n-input v-model:value="email" placeholder="name@school.edu.cn" size="large" />
            </n-form-item>

            <n-space justify="end" class="form-actions" :size="16">
              <n-button
                text
                class="animate-pop"
                @click="router.push('/login')"
              >
                返回登录节点
              </n-button>
              <n-button
                type="primary"
                attr-type="submit"
                :loading="loading"
                size="large"
                class="animate-pop hover-glow"
                @click="submitRegister"
              >
                铸造身份凭证
              </n-button>
            </n-space>
          </n-form>
        </n-space>
      </n-card>
    </div>
  </div>
</template>

<style scoped>
.register-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg-light);
  background-image:
    radial-gradient(at 100% 0%, hsla(180, 100%, 85%, 0.4) 0px, transparent 50%),
    radial-gradient(at 0% 100%, hsla(237, 80%, 90%, 0.3) 0px, transparent 50%);
  padding: 24px;
}

.register-container {
  width: 100%;
  max-width: 600px;
}

.register-card {
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.04);
}

.header-titles {
  display: flex;
  flex-direction: column;
}

.main-title {
  font-size: 2rem;
  font-weight: 800;
  margin: 0 0 8px;
  background: linear-gradient(135deg, var(--color-primary) 0%, #10b981 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.sub-title {
  font-size: 0.95rem;
}

.form-actions {
  margin-top: 12px;
}
</style>
