import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// 값이 없으면 Number(undefined)=NaN 이 되고 `length > NaN` 은 항상 false 라, 상한 검사가
// 조용히 사라진다(챕터 전체가 통째로 긁힌다). 런타임에 터뜨리면 사용자가 흰 화면을 보므로
// 빌드를 세운다 — 배선이 끊긴 채로는 배포가 나가지 않는다.
const REQUIRED_NUMERIC_ENV = ["VITE_HIGHLIGHT_MAX_SELECTED_CHARS"];

function assertRequiredEnv(mode: string) {
  // envDir 은 vite 실행 디렉터리(= 이 파일이 있는 frontend/) 기준이라 "." 로 충분하다.
  // process.cwd() 를 쓰면 @types/node 가 필요해진다.
  const env = loadEnv(mode, ".", "VITE_");
  for (const key of REQUIRED_NUMERIC_ENV) {
    const value = Number(env[key]);
    if (!Number.isFinite(value) || value <= 0) {
      throw new Error(
        `${key} 가 없거나 양수가 아닙니다 (mode=${mode}, 값=${env[key] ?? "미설정"}).`,
      );
    }
  }
}

// nginx/default.conf 의 경로 라우팅을 로컬 dev 서버에서 그대로 흉내낸다.
// (도커 없이 각 backend 서비스를 gradlew bootRun 등으로 개별 기동했을 때 기준.
//  docker compose 스택 전체를 띄웠다면 nginx:80 을 직접 쓰는 편이 낫다.)
// 서비스 하나가 추가/이동되면 nginx/default.conf 와 함께 고칠 것 — 안 그러면
// "로컬 dev 서버에선 되는데 docker/prod 에선 404" 가 재현된다.
const backendProxy = {
  // ai-service (8084)
  "/api/ai": { target: "http://localhost:8084", changeOrigin: true },
  // 상담 채팅. ws: true 가 없으면 핸드셰이크가 101 로 넘어가지 못하고 끊긴다.
  "/ws/ai": { target: "http://localhost:8084", changeOrigin: true, ws: true },

  // order-service (8082)
  "/api/orders": { target: "http://localhost:8082", changeOrigin: true },
  "/api/cart": { target: "http://localhost:8082", changeOrigin: true },
  "/api/coupons": { target: "http://localhost:8082", changeOrigin: true },
  "/api/payments": { target: "http://localhost:8082", changeOrigin: true },

  // member-service (8083)
  "/api/members": { target: "http://localhost:8083", changeOrigin: true },
  "/api/auth": { target: "http://localhost:8083", changeOrigin: true },
  "/api/cards": { target: "http://localhost:8083", changeOrigin: true },

  // catalog-service (8081) — 외부 API 는 /api/catalog 접두사 하나로 모인다.
  "/api/catalog": { target: "http://localhost:8081", changeOrigin: true },
};

export default defineConfig(({ mode }) => {
  assertRequiredEnv(mode);

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 3000,
      proxy: backendProxy,
    },
  };
});
