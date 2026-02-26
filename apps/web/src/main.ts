import { createPinia } from "pinia";
import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import "./styles/tokens.css";
import "./styles/base.css";
import App from "./App.vue";
import { createAppRouter } from "./app/router";

const app = createApp(App);
const pinia = createPinia();
const router = createAppRouter();

app.use(pinia);
app.use(ElementPlus);
app.use(router);

app.config.errorHandler = (error, instance, info) => {
  const componentName = (instance as { $options?: { name?: string } } | null)?.$options?.name || "unknown";
  console.error(
    "[web-runtime]",
    JSON.stringify({
      route: router.currentRoute.value.fullPath,
      action: info,
      component: componentName,
      latency: 0,
      traceId: ""
    })
  );
  console.error(error);
};

app.mount("#app");
