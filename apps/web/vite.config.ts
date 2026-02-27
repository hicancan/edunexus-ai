import { resolve } from "node:path";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            "@": resolve(__dirname, "src")
        }
    },
    server: {
        host: process.env.WEB_HOST || "0.0.0.0",
        port: Number(process.env.WEB_PORT || 5173),
        proxy: {
            "/api/v1": {
                target: process.env.API_BASE_URL || "http://localhost:8080",
                changeOrigin: true,
                secure: false,
                rewrite: (path) => path
            }
        }
    }
});
