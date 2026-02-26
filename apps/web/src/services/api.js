import axios from "axios";

const baseURL = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080/api/v1";

const api = axios.create({
  baseURL
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export default api;
