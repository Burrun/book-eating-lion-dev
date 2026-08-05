import axios from "axios";
import { readTokens } from "./authStorage.js";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api",
});

apiClient.interceptors.request.use((config) => {
  const tokens = readTokens();
  if (tokens?.accessToken) {
    config.headers.Authorization = `${tokens.tokenType ?? "Bearer"} ${tokens.accessToken}`;
  }
  return config;
});

export default apiClient;
