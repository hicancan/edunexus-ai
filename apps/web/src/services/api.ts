import axios from "axios";
import type { InternalAxiosRequestConfig } from "axios";

const fallbackBaseURL = `${window.location.protocol}//${window.location.hostname}:8080/api/v1`;
const baseURL = import.meta.env.VITE_API_BASE_URL || fallbackBaseURL;

const api = axios.create({
  baseURL
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
