<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import api from "../services/api";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const auth = useAuthStore();
const username = ref("student01");
const password = ref("12345678");
const error = ref("");

async function login() {
  error.value = "";
  try {
    const res = await api.post("/auth/login", { username: username.value, password: password.value });
    const data = res.data.data;
    auth.setAuth(data.accessToken, data.user);
    const rolePath = { STUDENT: "/student", TEACHER: "/teacher", ADMIN: "/admin" }[data.user.role] || "/login";
    router.push(rolePath);
  } catch (e) {
    error.value = e?.response?.data?.message || "登录失败";
  }
}
</script>

<template>
  <div class="container" style="max-width: 520px; padding-top: 80px">
    <div class="card">
      <h2>EduNexus AI 登录</h2>
      <p>默认账号：admin / teacher01 / student01，密码均为 12345678</p>
      <div style="display:flex;flex-direction:column;gap:10px">
        <input v-model="username" placeholder="用户名" />
        <input v-model="password" type="password" placeholder="密码" />
        <button @click="login">登录</button>
        <router-link to="/register">没有账号？去注册</router-link>
      </div>
      <p v-if="error" style="color:#e03131">{{ error }}</p>
    </div>
  </div>
</template>
