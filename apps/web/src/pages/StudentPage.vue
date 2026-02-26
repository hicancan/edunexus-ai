<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../services/api";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const section = computed(() => route.meta.section || "chat");
const sectionTitle = {
  chat: "智能问答",
  exercise: "练习做题",
  records: "做题记录",
  "wrong-book": "错题本",
  "ai-questions": "AI 出题",
  profile: "个人信息"
};

const navItems = [
  { path: "/student/chat", label: "智能问答" },
  { path: "/student/exercise", label: "练习做题" },
  { path: "/student/exercise/records", label: "做题记录" },
  { path: "/student/wrong-book", label: "错题本" },
  { path: "/student/ai-questions", label: "AI 出题" },
  { path: "/student/profile", label: "个人信息" }
];

const questionFilter = ref(JSON.parse(localStorage.getItem("student_question_filter") || "{\"subject\":\"物理\",\"difficulty\":\"MEDIUM\",\"page\":1,\"size\":10}"));
const wrongFilter = ref(JSON.parse(localStorage.getItem("student_wrong_filter") || "{\"subject\":\"\",\"status\":\"ACTIVE\",\"page\":1,\"size\":20}"));
const recordFilter = ref(JSON.parse(localStorage.getItem("student_record_filter") || "{\"startDate\":\"\",\"endDate\":\"\",\"page\":1,\"size\":15}"));
const aiHistoryFilter = ref(JSON.parse(localStorage.getItem("student_ai_history_filter") || "{\"subject\":\"\",\"page\":1,\"size\":10}"));

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
const wrongError = ref("");

const records = ref([]);
const recordsLoading = ref(false);
const recordsError = ref("");
const exerciseAnalysis = ref(null);

const aiForm = ref({ count: 3, subject: "物理", difficulty: "MEDIUM", conceptTags: ["牛顿第二定律"] });
const aiGenerated = ref([]);
const aiSessionId = ref("");
const aiAnswers = ref({});
const aiSubmitResult = ref(null);
const aiAnalysis = ref(null);
const aiLoading = ref(false);
const aiError = ref("");
const aiHistory = ref([]);
const aiHistoryLoading = ref(false);

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
    localStorage.setItem("student_question_filter", JSON.stringify(questionFilter.value));
    const res = await api.get("/student/exercise/questions", { params: questionFilter.value });
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
  wrongError.value = "";
  try {
    localStorage.setItem("student_wrong_filter", JSON.stringify(wrongFilter.value));
    const res = await api.get("/student/exercise/wrong-questions", { params: wrongFilter.value });
    wrongList.value = res.data.data.list || [];
  } catch (e) {
    wrongError.value = e?.response?.data?.message || "加载错题失败";
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
  recordsError.value = "";
  try {
    localStorage.setItem("student_record_filter", JSON.stringify(recordFilter.value));
    const res = await api.get("/student/exercise/records", { params: recordFilter.value });
    records.value = res.data.data.list || [];
  } catch (e) {
    recordsError.value = e?.response?.data?.message || "加载记录失败";
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
  aiError.value = "";
  try {
    const res = await api.post("/student/ai-questions/generate", aiForm.value);
    aiSessionId.value = res.data.data.sessionId;
    aiGenerated.value = res.data.data.questions || [];
    aiAnswers.value = {};
    aiSubmitResult.value = null;
    aiAnalysis.value = null;
    await loadAiHistory();
  } catch (e) {
    aiError.value = e?.response?.data?.message || "生成题目失败";
  } finally {
    aiLoading.value = false;
  }
}

async function submitAiQuestions() {
  aiLoading.value = true;
  aiError.value = "";
  try {
    const payload = aiGenerated.value.map((q) => ({ questionId: q.questionId, userAnswer: aiAnswers.value[q.questionId] || "" }));
    const res = await api.post("/student/ai-questions/submit", { sessionId: aiSessionId.value, answers: payload });
    aiSubmitResult.value = res.data.data;
    const analysis = await api.get(`/student/ai-questions/${res.data.data.recordId}/analysis`);
    aiAnalysis.value = analysis.data.data;
    await loadAiHistory();
  } catch (e) {
    aiError.value = e?.response?.data?.message || "提交 AI 题目失败";
  } finally {
    aiLoading.value = false;
  }
}

async function loadAiHistory() {
  aiHistoryLoading.value = true;
  aiError.value = "";
  try {
    localStorage.setItem("student_ai_history_filter", JSON.stringify(aiHistoryFilter.value));
    const res = await api.get("/student/ai-questions", { params: aiHistoryFilter.value });
    aiHistory.value = res.data.data.list || [];
  } catch (e) {
    aiError.value = e?.response?.data?.message || "加载 AI 出题历史失败";
  } finally {
    aiHistoryLoading.value = false;
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

async function logout() {
  try {
    await api.post("/auth/logout");
  } catch (_) {
    // ignore and clear local session
  }
  auth.clear();
  router.push("/login");
}

function formatJson(value) {
  if (value == null) return "";
  if (typeof value === "string") return value;
  return JSON.stringify(value, null, 2);
}

function formatPercent(value) {
  if (value == null || value === "") return "--";
  return `${value}%`;
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadQuestions(), loadWrong(), loadRecords(), loadAiHistory(), loadProfile()]);
  const remembered = localStorage.getItem("student_active_session");
  if (remembered) {
    await openSession(remembered);
  }
});
</script>

<template>
  <div class="container student-shell">
    <header class="page-header">
      <div>
        <h1 class="page-title">学生学习空间</h1>
        <p class="page-subtitle">{{ sectionTitle[section] }} · {{ auth.user?.username || "学生" }}，保持练习节奏，系统会持续记录成长轨迹。</p>
      </div>
      <button class="btn-secondary" @click="logout">退出登录</button>
    </header>

    <section class="card fade-up">
      <div class="subnav">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path">{{ item.label }}</router-link>
      </div>
    </section>

    <section v-if="section === 'chat'" class="card fade-up">
      <div class="section-head">
        <h3>AI 智能问答</h3>
        <button :disabled="chatLoading" @click="newSession">{{ chatLoading ? "处理中..." : "新建会话" }}</button>
      </div>
      <p v-if="chatError" class="status-error">{{ chatError }}</p>

      <div class="chat-layout">
        <aside class="chat-sidebar">
          <p class="muted side-title">会话列表</p>
          <div class="session-list" v-if="sessions.length">
            <div v-for="s in sessions" :key="s.sessionId" class="session-item" :class="{ active: activeSessionId === s.sessionId }">
              <button class="btn-ghost btn-sm session-main" @click="openSession(s.sessionId)">{{ s.title || "新建对话" }}</button>
              <button class="btn-danger btn-sm" @click="deleteSession(s.sessionId)">删除</button>
            </div>
          </div>
          <p v-else-if="!chatLoading" class="muted">暂无会话，点击右上方“新建会话”开始。</p>
        </aside>

        <main class="chat-main">
          <div class="message-board">
            <div v-if="chatLoading && !messages.length" class="muted">正在加载会话...</div>
            <div v-else-if="!messages.length" class="muted">输入问题后即可开始和 AI 对话。</div>
            <article v-for="(m, idx) in messages" :key="idx" class="message-item" :class="{ user: m.role === 'USER', assistant: m.role !== 'USER' }">
              <header>{{ m.role === "USER" ? "我" : "AI 助教" }}</header>
              <p>{{ m.content }}</p>
              <div v-if="m.citations && m.citations.length" class="citation-wrap">
                <span v-for="(c, ci) in m.citations" :key="ci" class="citation-tag">{{ c.title }} · {{ Number(c.score || 0).toFixed(2) }}</span>
              </div>
            </article>
          </div>

          <div class="chat-composer">
            <input v-model="input" placeholder="输入你的问题，例如：牛顿第二定律在斜面题如何应用？" />
            <button :disabled="chatLoading" @click="send">{{ chatLoading ? "发送中..." : "发送" }}</button>
          </div>
        </main>
      </div>
    </section>

    <section v-if="section === 'exercise'" class="card fade-up">
      <div class="section-head">
        <h3>练习做题</h3>
        <button :disabled="exerciseLoading" @click="loadQuestions">{{ exerciseLoading ? "加载中..." : "刷新题目" }}</button>
      </div>

      <div class="row">
        <div>
          <label>学科</label>
          <input v-model="questionFilter.subject" placeholder="如：物理" />
        </div>
        <div>
          <label>难度</label>
          <select v-model="questionFilter.difficulty">
            <option value="">全部</option>
            <option value="EASY">EASY</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HARD">HARD</option>
          </select>
        </div>
      </div>

      <p v-if="exerciseError" class="status-error">{{ exerciseError }}</p>
      <p v-if="exerciseLoading" class="muted">题目加载中...</p>
      <p v-else-if="questions.length === 0" class="muted">暂无题目，请调整筛选条件后重试。</p>

      <article v-for="q in questions" :key="q.questionId" class="question-card">
        <p class="question-content">{{ q.content }}</p>
        <label>选择答案</label>
        <select v-model="answers[q.questionId]">
          <option value="">请选择答案</option>
          <option v-for="(v, k) in q.options || {}" :key="k" :value="k">{{ k }}. {{ v }}</option>
        </select>
      </article>

      <div class="action-area" v-if="questions.length">
        <button :disabled="exerciseLoading" @click="submitExercise">{{ exerciseLoading ? "提交中..." : "提交答案" }}</button>
      </div>

      <div class="result-grid" v-if="submitResult || exerciseAnalysis">
        <div class="result-card" v-if="submitResult">
          <h4>本次提交结果</h4>
          <pre class="code-block">{{ formatJson(submitResult) }}</pre>
        </div>
        <div class="result-card" v-if="exerciseAnalysis">
          <h4>解析内容</h4>
          <pre class="code-block">{{ formatJson(exerciseAnalysis) }}</pre>
        </div>
      </div>
    </section>

    <section v-if="section === 'records'" class="card fade-up">
      <div class="section-head">
        <h3>做题记录</h3>
        <button :disabled="recordsLoading" @click="loadRecords">{{ recordsLoading ? "加载中..." : "按条件查询" }}</button>
      </div>

      <div class="row">
        <div>
          <label>开始日期</label>
          <input v-model="recordFilter.startDate" type="date" />
        </div>
        <div>
          <label>结束日期</label>
          <input v-model="recordFilter.endDate" type="date" />
        </div>
      </div>

      <p v-if="recordsError" class="status-error">{{ recordsError }}</p>
      <p v-if="recordsLoading" class="muted">正在加载记录...</p>
      <p v-else-if="records.length === 0" class="muted">暂无记录。</p>

      <div class="list-table" v-else>
        <article v-for="r in records" :key="r.recordId" class="list-row">
          <div>
            <p class="row-title">{{ r.subject || "未分类" }}</p>
            <p class="muted row-meta">记录 ID：{{ r.recordId }}</p>
          </div>
          <div class="row-right">
            <span class="score-pill">得分 {{ r.totalScore ?? "--" }}</span>
            <button class="btn-secondary btn-sm" @click="viewExerciseAnalysis(r.recordId)">查看解析</button>
          </div>
        </article>
      </div>

      <div class="result-card" v-if="exerciseAnalysis">
        <h4>解析详情</h4>
        <pre class="code-block">{{ formatJson(exerciseAnalysis) }}</pre>
      </div>
    </section>

    <section v-if="section === 'wrong-book'" class="card fade-up">
      <div class="section-head">
        <h3>错题本</h3>
        <button :disabled="wrongLoading" @click="loadWrong">{{ wrongLoading ? "加载中..." : "刷新错题" }}</button>
      </div>

      <div class="row">
        <div>
          <label>学科</label>
          <input v-model="wrongFilter.subject" placeholder="可选" />
        </div>
        <div>
          <label>状态</label>
          <select v-model="wrongFilter.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="MASTERED">MASTERED</option>
          </select>
        </div>
      </div>

      <p v-if="wrongError" class="status-error">{{ wrongError }}</p>
      <p v-if="wrongLoading" class="muted">正在加载错题...</p>
      <p v-else-if="wrongList.length === 0" class="muted">暂无错题。</p>

      <div class="list-table" v-else>
        <article v-for="w in wrongList" :key="w.id" class="list-row">
          <div>
            <p class="row-title">{{ w.content }}</p>
            <p class="muted row-meta">累计错题次数：{{ w.wrongCount }}</p>
          </div>
          <button v-if="wrongFilter.status === 'ACTIVE'" class="btn-success btn-sm" @click="removeWrong(w.questionId)">标记掌握</button>
        </article>
      </div>
    </section>

    <section v-if="section === 'ai-questions'" class="card fade-up">
      <div class="section-head">
        <h3>AI 个性化出题</h3>
        <button :disabled="aiLoading" @click="generateAiQuestions">{{ aiLoading ? "生成中..." : "生成题目" }}</button>
      </div>

      <div class="row">
        <div>
          <label>科目</label>
          <input v-model="aiForm.subject" />
        </div>
        <div>
          <label>数量</label>
          <input v-model.number="aiForm.count" type="number" min="1" max="20" />
        </div>
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

      <p v-if="aiError" class="status-error">{{ aiError }}</p>
      <p class="muted" v-if="aiSessionId">当前会话 ID：{{ aiSessionId }}</p>
      <p v-if="!aiLoading && aiGenerated.length === 0" class="muted">尚未生成题目，填写参数后点击“生成题目”。</p>

      <article v-for="q in aiGenerated" :key="q.questionId" class="question-card">
        <p class="question-content">{{ q.content }}</p>
        <label>选择答案</label>
        <select v-model="aiAnswers[q.questionId]">
          <option value="">请选择答案</option>
          <option v-for="(v, k) in q.options || {}" :key="k" :value="k">{{ k }}. {{ v }}</option>
        </select>
      </article>

      <div class="action-area" v-if="aiGenerated.length">
        <button :disabled="aiLoading" @click="submitAiQuestions">{{ aiLoading ? "提交中..." : "提交 AI 答案" }}</button>
      </div>

      <div class="result-grid" v-if="aiSubmitResult || aiAnalysis">
        <div class="result-card" v-if="aiSubmitResult">
          <h4>AI 题目成绩</h4>
          <pre class="code-block">{{ formatJson(aiSubmitResult) }}</pre>
        </div>
        <div class="result-card" v-if="aiAnalysis">
          <h4>AI 题目解析</h4>
          <pre class="code-block">{{ formatJson(aiAnalysis) }}</pre>
        </div>
      </div>

      <div class="history-block">
        <div class="section-head mini">
          <h4>历史记录</h4>
          <button class="btn-secondary btn-sm" :disabled="aiHistoryLoading" @click="loadAiHistory">{{ aiHistoryLoading ? "加载中..." : "刷新历史" }}</button>
        </div>
        <div class="row">
          <div>
            <label>学科筛选</label>
            <input v-model="aiHistoryFilter.subject" placeholder="可选" />
          </div>
        </div>
        <p v-if="!aiHistoryLoading && aiHistory.length === 0" class="muted">暂无历史记录。</p>
        <div class="list-table" v-else>
          <article v-for="h in aiHistory" :key="h.sessionId" class="list-row">
            <div>
              <p class="row-title">{{ h.subject || "未分类" }}</p>
              <p class="muted row-meta">会话 ID：{{ h.sessionId }}</p>
            </div>
            <div class="row-right">
              <span class="badge">完成：{{ h.completed ? "是" : "否" }}</span>
              <span class="badge">正确率：{{ formatPercent(h.correctRate) }}</span>
              <span class="badge">分数：{{ h.score ?? "--" }}</span>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section v-if="section === 'profile'" class="card fade-up">
      <h3>个人信息</h3>
      <p v-if="profileLoading" class="muted">资料加载中...</p>
      <div v-else-if="me" class="profile-grid">
        <article class="profile-item">
          <h4>账号</h4>
          <p>{{ me.username || "--" }}</p>
        </article>
        <article class="profile-item">
          <h4>角色</h4>
          <p>{{ me.role || "--" }}</p>
        </article>
        <article class="profile-item">
          <h4>邮箱</h4>
          <p>{{ me.email || "--" }}</p>
        </article>
        <article class="profile-item">
          <h4>手机号</h4>
          <p>{{ me.phone || "--" }}</p>
        </article>
      </div>
      <p v-else class="muted">暂无个人信息。</p>
      <pre class="code-block" v-if="me">{{ formatJson(me) }}</pre>
    </section>
  </div>
</template>

<style scoped>
.student-shell {
  padding-bottom: 22px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.section-head h3,
.section-head h4 {
  margin: 0;
}

.section-head.mini {
  margin-bottom: 8px;
}

.chat-layout {
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  gap: 12px;
}

.chat-sidebar,
.chat-main {
  border: 1px solid #d7e5f3;
  border-radius: 14px;
  background: #fafeff;
  padding: 12px;
}

.side-title {
  margin-top: 0;
}

.session-list {
  display: grid;
  gap: 8px;
}

.session-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px;
  border-radius: 10px;
  border: 1px solid #d8e7f5;
  background: #fff;
}

.session-item.active {
  border-color: #74c0fc;
  background: #edf6ff;
}

.session-main {
  flex: 1;
  justify-content: flex-start;
  text-align: left;
}

.message-board {
  min-height: 280px;
  max-height: 430px;
  overflow: auto;
  padding-right: 4px;
  display: grid;
  gap: 10px;
}

.message-item {
  border-radius: 12px;
  padding: 11px 12px;
  border: 1px solid #d4e4f2;
  background: #fff;
}

.message-item.user {
  background: #edf6ff;
  border-color: #b6ddff;
}

.message-item.assistant {
  background: #f8fcff;
}

.message-item header {
  font-weight: 700;
  color: #2c4a67;
  margin-bottom: 5px;
}

.message-item p {
  margin: 0;
  white-space: pre-wrap;
}

.citation-wrap {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.citation-tag {
  display: inline-block;
  font-size: 12px;
  color: #396083;
  padding: 4px 8px;
  border: 1px solid #cce1f4;
  border-radius: 999px;
  background: #f4faff;
}

.chat-composer {
  margin-top: 10px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.question-card {
  margin-top: 10px;
  padding: 13px;
  border: 1px solid #d5e5f3;
  border-radius: 12px;
  background: #fff;
}

.question-content {
  margin: 0 0 9px;
}

.action-area {
  margin-top: 12px;
}

.result-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.result-card {
  margin-top: 12px;
  border: 1px solid #d8e7f5;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}

.result-card h4 {
  margin: 0 0 8px;
}

.list-table {
  margin-top: 10px;
  display: grid;
  gap: 9px;
}

.list-row {
  border: 1px solid #d6e6f4;
  border-radius: 12px;
  padding: 11px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  background: #fff;
}

.row-title {
  margin: 0;
  font-weight: 600;
}

.row-meta {
  margin: 3px 0 0;
  font-size: 12px;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.score-pill {
  display: inline-block;
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid #b6ddff;
  color: #24507b;
  background: #edf6ff;
  font-size: 13px;
}

.history-block {
  margin-top: 18px;
  border-top: 1px dashed #d0dfec;
  padding-top: 14px;
}

.badge {
  display: inline-block;
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid #d3e4f4;
  background: #f6fbff;
  color: #406283;
  font-size: 12px;
}

.profile-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.profile-item {
  border: 1px solid #d8e7f5;
  border-radius: 12px;
  padding: 11px;
  background: #fff;
}

.profile-item h4 {
  margin: 0;
  color: #3c5f80;
  font-size: 13px;
}

.profile-item p {
  margin: 5px 0 0;
  font-size: 15px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }

  .chat-composer {
    grid-template-columns: 1fr;
  }

  .result-grid,
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .list-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
