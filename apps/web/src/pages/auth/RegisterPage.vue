<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { getFirstIssueMessage, registerSchema } from "../../schemas/auth.schemas";
import { register as registerApi } from "../../services/auth.service";
import { toErrorMessage } from "../../services/error-message";

const router = useRouter();

const username = ref("");
const password = ref("");
const role = ref<"STUDENT" | "TEACHER">("STUDENT");
const email = ref("");
const phone = ref("");
const loading = ref(false);
const error = ref("");
const success = ref("");

async function submitRegister(): Promise<void> {
  error.value = "";
  success.value = "";

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
    success.value = "注册成功，请使用新账号登录。";
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
  <div class="app-container">
    <section class="panel register-panel">
      <h1 class="workspace-title">创建账号</h1>
      <p class="workspace-subtitle">支持学生与教师自助注册，管理员账号由平台端创建。</p>

      <div class="form-grid">
        <div class="field-block">
          <label for="register-username">用户名</label>
          <input id="register-username" v-model="username" autocomplete="username" placeholder="3-50 位字母、数字或下划线" />
        </div>
        <div class="field-block">
          <label for="register-role">身份类型</label>
          <select id="register-role" v-model="role">
            <option value="STUDENT">学生 STUDENT</option>
            <option value="TEACHER">教师 TEACHER</option>
          </select>
        </div>
        <div class="field-block">
          <label for="register-password">密码</label>
          <input id="register-password" v-model="password" type="password" autocomplete="new-password" placeholder="8-64 位" />
        </div>
        <div class="field-block">
          <label for="register-phone">手机号（可选）</label>
          <input id="register-phone" v-model="phone" placeholder="用于找回账号" />
        </div>
        <div class="field-block">
          <label for="register-email">邮箱（可选）</label>
          <input id="register-email" v-model="email" placeholder="name@school.edu.cn" />
        </div>
      </div>

      <div class="register-actions">
        <button class="btn" type="button" :disabled="loading" @click="submitRegister">{{ loading ? "提交中..." : "立即注册" }}</button>
        <button class="btn secondary" type="button" @click="router.push('/login')">返回登录</button>
      </div>

      <p v-if="error" class="status-box error" role="alert">{{ error }}</p>
      <p v-if="success" class="status-box success">{{ success }}</p>
    </section>
  </div>
</template>

<style scoped>
.register-panel {
  max-width: 860px;
  margin: 20px auto;
}

.register-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}

@media (max-width: 767px) {
  .register-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
