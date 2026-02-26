import { createPinia } from "pinia";
import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import { createAppRouter } from "./app/router";

const app = createApp(App);
const pinia = createPinia();
const router = createAppRouter();

app.use(pinia);
app.use(ElementPlus);
app.use(router);
app.mount("#app");
