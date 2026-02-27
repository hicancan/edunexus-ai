import { createPinia } from "pinia";
import piniaPluginPersistedstate from "pinia-plugin-persistedstate";
import { createApp } from "vue";
import "./styles/tokens.css";
import "./styles/base.css";
import App from "./App.vue";
import { createAppRouter } from "./app/router";

const app = createApp(App);
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);
const router = createAppRouter();

app.use(pinia);
app.use(router);

// Provide global naive ui setup if needed (Naive UI primarily uses NConfigProvider in App.vue)
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
