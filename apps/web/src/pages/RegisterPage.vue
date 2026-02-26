<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import api from "../services/api";

const router = useRouter();
const username = ref("");
const password = ref("");
const role = ref("STUDENT");
const email = ref("");
const phone = ref("");
const loading = ref(false);
const error = ref("");
const success = ref("");

async function submit() {
  error.value = "";
  success.value = "";
  loading.value = true;
  try {
    await api.post("/auth/register", {
      username: username.value,
      password: password.value,
      role: role.value,
      email: email.value || undefined,
      phone: phone.value || undefined
    });
    success.value = "注册成功，请登录。";
    setTimeout(() => router.push("/login"), 600);
  } catch (e) {
    error.value = e?.response?.data?.message || "注册失败";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="container" style="max-width: 560px; padding-top: 48px">
    <div class="card">
      <h2>注册账号</h2>
      <p>仅支持学生/教师注册，管理员由平台创建。</p>
      <div style="display:flex;flex-direction:column;gap:10px">
        <input v-model="username" placeholder="用户名（3-50位）" />
        <input v-model="password" type="password" placeholder="密码（至少8位）" />
        <select v-model="role">
          <option value="STUDENT">STUDENT</option>
          <option value="TEACHER">TEACHER</option>
        </select>
        <input v-model="email" placeholder="邮箱（可选）" />
        <input v-model="phone" placeholder="手机号（可选）" />
        <button :disabled="loading" @click="submit">{{ loading ? "提交中..." : "注册" }}</button>
        <button style="background:#495057" @click="router.push('/login')">返回登录</button>
      </div>
      <p v-if="error" style="color:#e03131">{{ error }}</p>
      <p v-if="success" style="color:#2f9e44">{{ success }}</p>
    </div>
  </div>
</template>
