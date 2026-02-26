<script setup lang="ts">
import { onMounted, ref } from "vue";
import { getMe } from "../../services/auth.service";
import { toErrorMessage } from "../../services/error-message";
import type { UserVO } from "../../services/contracts";
import { useAuthStore } from "../../stores/auth";

const auth = useAuthStore();
const profile = ref<UserVO | null>(null);
const loading = ref(false);
const error = ref("");

async function loadProfile(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    profile.value = await getMe();
    if (profile.value) {
      auth.setUser(profile.value);
    }
  } catch (requestError) {
    error.value = toErrorMessage(requestError, "加载个人信息失败");
  } finally {
    loading.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2 class="panel-title">个人信息</h2>
        <p class="panel-note">来自 `GET /auth/me`，用于身份态校验和页面展示。</p>
      </div>
      <button class="btn secondary" type="button" :disabled="loading" @click="loadProfile">
        {{ loading ? "刷新中..." : "刷新资料" }}
      </button>
    </header>

    <p v-if="error" class="status-box error" role="alert">{{ error }}</p>
    <div v-if="loading && !profile" class="status-box info">正在加载个人信息...</div>
    <div v-else-if="!profile" class="status-box empty">暂无个人信息。</div>
    <div v-else class="profile-grid">
      <article class="profile-item">
        <h3>用户名</h3>
        <p>{{ profile.username || "--" }}</p>
      </article>
      <article class="profile-item">
        <h3>角色</h3>
        <p>{{ profile.role || "--" }}</p>
      </article>
      <article class="profile-item">
        <h3>状态</h3>
        <p>{{ profile.status || "--" }}</p>
      </article>
      <article class="profile-item">
        <h3>邮箱</h3>
        <p>{{ profile.email || "--" }}</p>
      </article>
      <article class="profile-item">
        <h3>手机号</h3>
        <p>{{ profile.phone || "--" }}</p>
      </article>
      <article class="profile-item">
        <h3>用户 ID</h3>
        <p>{{ profile.id || "--" }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.profile-item {
  border: 1px solid var(--color-border);
  background: #ffffff;
  border-radius: var(--radius-md);
  padding: 12px;
}

.profile-item h3 {
  margin: 0;
  color: #416482;
  font-size: 0.84rem;
}

.profile-item p {
  margin: 6px 0 0;
  font-weight: 700;
  overflow-wrap: anywhere;
}

@media (max-width: 767px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
