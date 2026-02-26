<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const section = computed(() => route.meta.section || "chat");

const sessions = ref([]);
const activeSessionId = ref("");
const messages = ref([]);
const input = ref("");
const chatLoading = ref(false);
const chatError = ref("");

const questions = ref([]);
const answers = ref({});
const submitResult = ref(null);
const exerciseLoading = ref(false);
const exerciseError = ref("");

const wrongList = ref([]);
const wrongLoading = ref(false);

const records = ref([]);
const recordsLoading = ref(false);
const exerciseAnalysis = ref(null);

const aiForm = ref({ count: 3, subject: "物理", difficulty: "MEDIUM", conceptTags: ["牛顿第二定律"] });
const aiGenerated = ref([]);
const aiSessionId = ref("");
const aiAnswers = ref({});
const aiSubmitResult = ref(null);
const aiAnalysis = ref(null);
const aiLoading = ref(false);

const me = ref(null);
const profileLoading = ref(false);

async function loadSessions() {
  chatLoading.value = true;
  chatError.value = "";
  try {
    const res = await api.get("/student/chat/sessions");
    sessions.value = res.data.data.list || [];
  } catch (e) {
    chatError.value = e?.response?.data?.message || "加载会话失败";
  } finally {
    chatLoading.value = false;
  }
}

async function newSession() {
  chatError.value = "";
  try {
    const res = await api.post("/student/chat/session");
    activeSessionId.value = res.data.data.sessionId;
    messages.value = [];
    await loadSessions();
  } catch (e) {
    chatError.value = e?.response?.data?.message || "新建会话失败";
  }
}

async function openSession(id) {
  activeSessionId.value = id;
  chatLoading.value = true;
  chatError.value = "";
  try {
    const res = await api.get(`/student/chat/session/${id}`);
    messages.value = res.data.data.messages || [];
    localStorage.setItem("student_active_session", id);
  } catch (e) {
    chatError.value = e?.response?.data?.message || "读取会话失败";
  } finally {
    chatLoading.value = false;
  }
}

async function deleteSession(id) {
  if (!window.confirm("确认删除该会话吗？")) return;
  try {
    await api.delete(`/student/chat/session/${id}`);
    if (activeSessionId.value === id) {
      activeSessionId.value = "";
      messages.value = [];
      localStorage.removeItem("student_active_session");
    }
    await loadSessions();
  } catch (e) {
    chatError.value = e?.response?.data?.message || "删除会话失败";
  }
}

async function send() {
  if (!input.value.trim()) return;
  if (!activeSessionId.value) await newSession();
  chatLoading.value = true;
  chatError.value = "";
  try {
    const message = input.value;
    input.value = "";
    messages.value.push({ role: "USER", content: message });
    const res = await api.post(`/student/chat/session/${activeSessionId.value}/message`, { message });
    messages.value.push({ role: "ASSISTANT", content: res.data.data.aiResponse, citations: res.data.data.sources });
    await loadSessions();
  } catch (e) {
    chatError.value = e?.response?.data?.message || "发送失败";
  } finally {
    chatLoading.value = false;
  }
}

async function loadQuestions() {
  exerciseLoading.value = true;
  exerciseError.value = "";
  try {
    const res = await api.get("/student/exercise/questions", { params: { subject: "物理", difficulty: "MEDIUM" } });
    questions.value = res.data.data.list || [];
  } catch (e) {
    exerciseError.value = e?.response?.data?.message || "加载题目失败";
  } finally {
    exerciseLoading.value = false;
  }
}

async function submitExercise() {
  exerciseLoading.value = true;
  exerciseError.value = "";
  try {
    const payload = (questions.value || []).map((q) => ({ questionId: q.questionId, userAnswer: answers.value[q.questionId] || "" }));
    const res = await api.post("/student/exercise/submit", { answers: payload, timeSpent: 600 });
    submitResult.value = res.data.data;
    await viewExerciseAnalysis(res.data.data.recordId);
    await loadWrong();
    await loadRecords();
  } catch (e) {
    exerciseError.value = e?.response?.data?.message || "提交失败";
  } finally {
    exerciseLoading.value = false;
  }
}

async function loadWrong() {
  wrongLoading.value = true;
  try {
    const res = await api.get("/student/exercise/wrong-questions", { params: { status: "ACTIVE" } });
    wrongList.value = res.data.data.list || [];
  } finally {
    wrongLoading.value = false;
  }
}

async function removeWrong(questionId) {
  if (!window.confirm("确认将该题标记为已掌握吗？")) return;
  await api.delete(`/student/exercise/wrong-questions/${questionId}`);
  await loadWrong();
}

async function loadRecords() {
  recordsLoading.value = true;
  try {
    const res = await api.get("/student/exercise/records");
    records.value = res.data.data.list || [];
  } finally {
    recordsLoading.value = false;
  }
}

async function viewExerciseAnalysis(recordId) {
  const res = await api.get(`/student/exercise/${recordId}/analysis`);
  exerciseAnalysis.value = res.data.data;
}

async function generateAiQuestions() {
  aiLoading.value = true;
  try {
    const res = await api.post("/student/ai-questions/generate", aiForm.value);
    aiSessionId.value = res.data.data.sessionId;
    aiGenerated.value = res.data.data.questions || [];
    aiAnswers.value = {};
    aiSubmitResult.value = null;
    aiAnalysis.value = null;
  } finally {
    aiLoading.value = false;
  }
}

async function submitAiQuestions() {
  aiLoading.value = true;
  try {
    const payload = aiGenerated.value.map((q) => ({ questionId: q.questionId, userAnswer: aiAnswers.value[q.questionId] || "" }));
    const res = await api.post("/student/ai-questions/submit", { sessionId: aiSessionId.value, answers: payload });
    aiSubmitResult.value = res.data.data;
    const analysis = await api.get(`/student/ai-questions/${res.data.data.recordId}/analysis`);
    aiAnalysis.value = analysis.data.data;
  } finally {
    aiLoading.value = false;
  }
}

async function loadProfile() {
  profileLoading.value = true;
  try {
    const res = await api.get("/auth/me");
    me.value = res.data.data;
  } finally {
    profileLoading.value = false;
  }
}

function logout() {
  auth.clear();
  router.push("/login");
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadQuestions(), loadWrong(), loadRecords(), loadProfile()]);
  const remembered = localStorage.getItem("student_active_session");
  if (remembered) {
    await openSession(remembered);
  }
});
</script>

<template>
  <div class="container">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <h2>学生端</h2>
      <button @click="logout">退出</button>
    </div>

    <div class="card" style="display:flex;gap:10px;flex-wrap:wrap">
      <router-link to="/student/chat">智能问答</router-link>
      <router-link to="/student/exercise">练习做题</router-link>
      <router-link to="/student/exercise/records">做题记录</router-link>
      <router-link to="/student/wrong-book">错题本</router-link>
      <router-link to="/student/ai-questions">AI 出题</router-link>
      <router-link to="/student/profile">个人信息</router-link>
    </div>

    <div v-if="section === 'chat'" class="card">
      <h3>AI 智能问答</h3>
      <button :disabled="chatLoading" @click="newSession">新建会话</button>
      <p v-if="chatError" style="color:#e03131">{{ chatError }}</p>
      <div style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap">
        <div v-for="s in sessions" :key="s.sessionId" style="display:flex;gap:6px;align-items:center">
          <button @click="openSession(s.sessionId)">{{ s.title }}</button>
          <button style="background:#e03131" @click="deleteSession(s.sessionId)">删</button>
        </div>
      </div>
      <p v-if="!chatLoading && sessions.length === 0">暂无会话，点击“新建会话”开始。</p>
      <div class="card" style="margin-top:12px;max-height:320px;overflow:auto">
        <div v-for="(m,idx) in messages" :key="idx" style="margin-bottom:8px">
          <b>{{ m.role }}:</b> {{ m.content }}
        </div>
      </div>
      <div style="display:flex;gap:8px">
        <input v-model="input" placeholder="输入问题..." />
        <button :disabled="chatLoading" @click="send">{{ chatLoading ? "发送中..." : "发送" }}</button>
      </div>
    </div>

    <div v-if="section === 'exercise'" class="card">
      <h3>练习做题</h3>
      <p v-if="exerciseError" style="color:#e03131">{{ exerciseError }}</p>
      <p v-if="exerciseLoading">题目加载中...</p>
      <p v-else-if="questions.length === 0">暂无题目。</p>
      <div v-for="q in questions" :key="q.questionId" class="card">
        <div>{{ q.content }}</div>
        <select v-model="answers[q.questionId]">
          <option value="">请选择答案</option>
          <option v-for="(v,k) in q.options || {}" :key="k" :value="k">{{ k }}. {{ v }}</option>
        </select>
      </div>
      <button :disabled="exerciseLoading" @click="submitExercise">{{ exerciseLoading ? "提交中..." : "提交答案" }}</button>
      <pre v-if="submitResult" style="white-space:pre-wrap">{{ submitResult }}</pre>
      <pre v-if="exerciseAnalysis" style="white-space:pre-wrap">{{ exerciseAnalysis }}</pre>
    </div>

    <div v-if="section === 'records'" class="card">
      <h3>做题记录</h3>
      <p v-if="recordsLoading">加载中...</p>
      <p v-else-if="records.length === 0">暂无记录。</p>
      <ul>
        <li v-for="r in records" :key="r.recordId">
          {{ r.subject }} - 得分 {{ r.totalScore }}
          <button style="margin-left:6px" @click="viewExerciseAnalysis(r.recordId)">查看解析</button>
        </li>
      </ul>
      <pre v-if="exerciseAnalysis" style="white-space:pre-wrap">{{ exerciseAnalysis }}</pre>
    </div>

    <div v-if="section === 'wrong-book'" class="card">
      <h3>错题本</h3>
      <p v-if="wrongLoading">加载中...</p>
      <p v-else-if="wrongList.length === 0">暂无错题。</p>
      <ul>
        <li v-for="w in wrongList" :key="w.id">
          {{ w.content }}（错 {{ w.wrongCount }} 次）
          <button style="margin-left:6px;background:#2f9e44" @click="removeWrong(w.questionId)">标记掌握</button>
        </li>
      </ul>
    </div>

    <div v-if="section === 'ai-questions'" class="card">
      <h3>AI 个性化出题</h3>
      <div class="row">
        <div><label>科目</label><input v-model="aiForm.subject" /></div>
        <div><label>数量</label><input v-model.number="aiForm.count" type="number" min="1" max="20" /></div>
      </div>
      <div class="row">
        <div>
          <label>难度</label>
          <select v-model="aiForm.difficulty">
            <option value="EASY">EASY</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HARD">HARD</option>
          </select>
        </div>
      </div>
      <button :disabled="aiLoading" @click="generateAiQuestions">{{ aiLoading ? "生成中..." : "生成题目" }}</button>
      <p>会话ID: {{ aiSessionId }}</p>
      <p v-if="!aiLoading && aiGenerated.length === 0">尚未生成题目。</p>
      <div v-for="q in aiGenerated" :key="q.questionId" class="card">
        <div>{{ q.content }}</div>
        <select v-model="aiAnswers[q.questionId]">
          <option value="">请选择答案</option>
          <option v-for="(v,k) in q.options || {}" :key="k" :value="k">{{ k }}. {{ v }}</option>
        </select>
      </div>
      <button v-if="aiGenerated.length" :disabled="aiLoading" @click="submitAiQuestions">{{ aiLoading ? "提交中..." : "提交 AI 题目答案" }}</button>
      <pre v-if="aiSubmitResult" style="white-space:pre-wrap">{{ aiSubmitResult }}</pre>
      <pre v-if="aiAnalysis" style="white-space:pre-wrap">{{ aiAnalysis }}</pre>
    </div>

    <div v-if="section === 'profile'" class="card">
      <h3>个人信息</h3>
      <p v-if="profileLoading">加载中...</p>
      <pre v-else style="white-space:pre-wrap">{{ me }}</pre>
    </div>
  </div>
</template>
