import js from "@eslint/js";
import tseslint from "typescript-eslint";
import pluginVue from "eslint-plugin-vue";
import prettier from "eslint-config-prettier";

export default tseslint.config(
  { ignores: ["dist/", "node_modules/", "src/types/api.d.ts"] },

  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs["flat/recommended"],

  {
    languageOptions: {
      globals: {
        window: "readonly",
        document: "readonly",
        navigator: "readonly",
        console: "readonly",
        setTimeout: "readonly",
        clearTimeout: "readonly",
        setInterval: "readonly",
        clearInterval: "readonly",
        fetch: "readonly",
        URL: "readonly",
        URLSearchParams: "readonly",
        FormData: "readonly",
        Blob: "readonly",
        File: "readonly",
        FileReader: "readonly",
        AbortController: "readonly",
        Event: "readonly",
        EventSource: "readonly",
        HTMLElement: "readonly",
        HTMLInputElement: "readonly",
        MutationObserver: "readonly",
        IntersectionObserver: "readonly",
        ResizeObserver: "readonly",
        requestAnimationFrame: "readonly",
        TextDecoder: "readonly",
        TextEncoder: "readonly",
        structuredClone: "readonly",
        btoa: "readonly",
        atob: "readonly",
        alert: "readonly",
        confirm: "readonly"
      }
    }
  },

  {
    files: ["**/*.vue"],
    languageOptions: {
      parserOptions: { parser: tseslint.parser }
    }
  },

  {
    rules: {
      // Relax rules that conflict with project conventions
      "vue/multi-word-component-names": "off",
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }
      ],
      "@typescript-eslint/no-explicit-any": "warn"
    }
  },

  // Must be last — disables formatting rules that conflict with Prettier
  prettier
);
