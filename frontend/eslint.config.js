import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier";

export default tseslint.config(
  { ignores: ["dist", "node_modules", "coverage"] },

  // JS/JSX: 기본 권장 규칙만 적용 (타입 정보 없이 검사)
  {
    files: ["**/*.{js,jsx}"],
    extends: [js.configs.recommended],
  },

  // TS/TSX: 타입스크립트 권장 규칙 추가
  {
    files: ["**/*.{ts,tsx}"],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
  },

  // 공통 설정 (React 훅 규칙, 브라우저 전역)
  {
    files: ["**/*.{js,jsx,ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: globals.browser,
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],
    },
  },

  // Prettier와 충돌하는 포맷팅 규칙 비활성화 (반드시 마지막에 위치)
  prettier,
);
