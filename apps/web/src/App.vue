<template>
  <router-view />
</template>

<style>
:root {
  --bg: #f2f7fb;
  --bg-soft: #e8f2fa;
  --card: #ffffff;
  --card-border: #d7e5f3;
  --text: #1f2a37;
  --muted: #5f7083;
  --primary: #1864ab;
  --primary-soft: #e7f0fb;
  --success: #2b8a3e;
  --warning: #e67700;
  --danger: #d6336c;
  --shadow-soft: 0 14px 40px rgba(31, 61, 94, 0.09);
  --radius-lg: 18px;
  --radius-md: 12px;
  --radius-sm: 9px;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-height: 100%;
}

body {
  margin: 0;
  font-family: "Noto Sans SC", "HarmonyOS Sans SC", "Source Han Sans CN", "Microsoft YaHei", sans-serif;
  background:
    radial-gradient(45rem 45rem at 0% -10%, rgba(24, 100, 171, 0.11), transparent 54%),
    radial-gradient(42rem 42rem at 100% 10%, rgba(43, 138, 62, 0.08), transparent 58%),
    linear-gradient(180deg, var(--bg-soft) 0%, var(--bg) 100%);
  color: var(--text);
  line-height: 1.55;
}

a {
  color: var(--primary);
  text-decoration: none;
}

a:hover {
  text-decoration: underline;
}

.container {
  max-width: 1220px;
  margin: 0 auto;
  padding: 28px;
}

.card {
  background: var(--card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--card-border);
  padding: 18px;
  box-shadow: var(--shadow-soft);
  margin-bottom: 18px;
}

button {
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  padding: 10px 15px;
  background: linear-gradient(135deg, #1864ab 0%, #1f78c7 100%);
  color: #fff;
  cursor: pointer;
  font-weight: 600;
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(24, 100, 171, 0.25);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.7;
  transform: none;
  box-shadow: none;
}

.btn-secondary {
  background: #f8fbfe;
  color: #2a425e;
  border-color: #c7dced;
}

.btn-danger {
  background: linear-gradient(135deg, #c2255c 0%, #e64980 100%);
}

.btn-success {
  background: linear-gradient(135deg, #2b8a3e 0%, #37b24d 100%);
}

.btn-warning {
  background: linear-gradient(135deg, #e67700 0%, #f59f00 100%);
}

.btn-ghost {
  background: transparent;
  border-color: #c7dced;
  color: #315171;
}

.btn-sm {
  padding: 7px 11px;
  font-size: 13px;
}

input,
select,
textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #c9d8e8;
  border-radius: var(--radius-sm);
  background: #fff;
  font: inherit;
  color: var(--text);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

input:focus,
select:focus,
textarea:focus {
  outline: none;
  border-color: #339af0;
  box-shadow: 0 0 0 3px rgba(51, 154, 240, 0.15);
}

label {
  display: inline-block;
  margin-bottom: 6px;
  color: #436180;
  font-size: 13px;
  font-weight: 600;
}

.row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.muted {
  color: var(--muted);
}

.status-error {
  color: var(--danger);
  background: #fff1f6;
  border: 1px solid #ffc9de;
  border-radius: var(--radius-sm);
  padding: 9px 11px;
}

.status-success {
  color: var(--success);
  background: #ebfbee;
  border: 1px solid #b2f2bb;
  border-radius: var(--radius-sm);
  padding: 9px 11px;
}

.code-block {
  margin: 0;
  background: #f6fbff;
  border: 1px solid #cfe0ef;
  border-radius: var(--radius-md);
  padding: 12px;
  font-family: "Consolas", "Monaco", monospace;
  font-size: 12px;
  color: #2d3e50;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 29px;
  line-height: 1.25;
}

.page-subtitle {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 14px;
}

.subnav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.subnav a {
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid #c8daeb;
  background: #f6fbff;
  color: #355372;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.18s ease;
}

.subnav a:hover {
  background: #edf6ff;
  text-decoration: none;
}

.subnav a.router-link-active {
  background: linear-gradient(135deg, #1864ab 0%, #1f78c7 100%);
  border-color: #1864ab;
  color: #fff;
}

.fade-up {
  animation: fade-up 0.36s ease;
}

@keyframes fade-up {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 900px) {
  .container {
    padding: 16px;
  }

  .row {
    grid-template-columns: 1fr;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-title {
    font-size: 24px;
  }
}
</style>
