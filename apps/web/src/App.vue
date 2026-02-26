<template>
  <router-view />
</template>

<style>
:root {
  --bg: #f5f7fb;
  --card: #ffffff;
  --text: #1f2937;
  --primary: #0b7285;
  --accent: #f59f00;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: "Source Han Sans CN", "Microsoft YaHei", sans-serif;
  background: radial-gradient(circle at 0% 0%, #dff3f7, transparent 45%), var(--bg);
  color: var(--text);
}
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.card {
  background: var(--card);
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 8px 30px rgba(11, 114, 133, 0.08);
  margin-bottom: 16px;
}
button {
  border: none;
  border-radius: 10px;
  padding: 10px 14px;
  background: var(--primary);
  color: #fff;
  cursor: pointer;
}
input, select, textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
}
.row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 900px) {
  .row { grid-template-columns: 1fr; }
}
</style>
