import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    host: process.env.WEB_HOST || "0.0.0.0",
    port: Number(process.env.WEB_PORT || 5173)
  }
});
