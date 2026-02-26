<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { register as registerApi } from "../services/auth.service";
import { toErrorMessage } from "../services/error-message";
import { getFirstIssueMessage, registerSchema } from "../schemas/auth.schemas";

const router = useRouter();
const username = ref("");
const password = ref("");
const role = ref<"STUDENT" | "TEACHER">("STUDENT");
const email = ref("");
const phone = ref("");
const loading = ref(false);
const error = ref("");
const success = ref("");

async function submit() {
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
    success.value = "注册成功，请登录。";
    setTimeout(() => router.push("/login"), 600);
  } catch (e: unknown) {
    error.value = toErrorMessage(e, "注册失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="container register-wrap">
    <section class="card register-card fade-up">
      <div class="register-header">
        <h2>创建 EduNexus 账号</h2>
        <p class="muted">学生和教师可自助注册，管理员账号由平台统一创建。</p>
      </div>

      <div class="row">
        <div>
          <label>用户名</label>
          <input v-model="username" autocomplete="username" placeholder="3-50 位，支持字母数字" />
        </div>
        <div>
          <label>身份类型</label>
          <select v-model="role">
            <option value="STUDENT">学生 STUDENT</option>
            <option value="TEACHER">教师 TEACHER</option>
          </select>
        </div>
      </div>

      <div class="row">
        <div>
          <label>密码</label>
          <input v-model="password" autocomplete="new-password" type="password" placeholder="至少 8 位" />
        </div>
        <div>
          <label>手机号（可选）</label>
          <input v-model="phone" placeholder="用于找回账号" />
        </div>
      </div>

      <div>
        <label>邮箱（可选）</label>
        <input v-model="email" placeholder="例如：name@school.edu.cn" />
      </div>

      <div class="action-row">
        <button aria-label="立即注册" :disabled="loading" @click="submit">{{ loading ? "提交中..." : "立即注册" }}</button>
        <button class="btn-secondary" @click="router.push('/login')">返回登录</button>
      </div>

      <p v-if="error" class="status-error">{{ error }}</p>
      <p v-if="success" class="status-success">{{ success }}</p>
    </section>
  </div>
</template>

<style scoped>
.register-wrap {
  max-width: 860px;
  padding-top: 34px;
}

.register-card {
  padding: 24px;
}

.register-header h2 {
  margin: 0;
}

.register-header p {
  margin: 8px 0 14px;
}

.action-row {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}

@media (max-width: 900px) {
  .register-wrap {
    padding-top: 10px;
  }

  .action-row {
    flex-direction: column;
  }
}
</style>
