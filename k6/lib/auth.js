// 로그인 헬퍼. member-v1.yaml: POST /api/auth/login → TokenEnvelope.
//
// 로컬(application-local.yml)도 실제 AWS Cognito를 탄다(목이 아니다).
// VU마다 로그인하면 Cognito 자체 rate limit에 걸리고, 그게 병목이 되어
// "우리 서버의 한계"가 아니라 "Cognito의 한계"를 측정하게 된다.
// 그래서 반드시 setup()에서 1회만 호출하고 전 VU가 토큰을 공유해야 한다.
import http from 'k6/http';
import { check } from 'k6';
import { MEMBER_URL, LOGIN_EMAIL, LOGIN_PASSWORD, requireAuthEnv } from './config.js';

/**
 * 임의의 email/password로 1회 로그인한다. 여러 계정을 setup()에서 한 번씩만 로그인해
 * 풀로 공유해야 하는 시나리오(예: 06-chat-concurrency.js — 1인 1방 강제 때문에 고객
 * 계정이 여러 개 필요하다)에서 login()/LOGIN_EMAIL 하나만으로는 부족해서 분리했다.
 */
export function loginAs(email, password) {
  const res = http.post(
    `${MEMBER_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  if (res.status !== 200) {
    throw new Error(`login failed(${email}): ${res.status} ${res.body}`);
  }
  // TokenEnvelope 의 정확한 필드 경로는 member-v1.yaml TokenEnvelope 스키마를 보고
  // 맞춰 넣을 것. 아래는 ApiResponse<T> 공통 포맷(success/message/data) 가정.
  const token = res.json('data.accessToken');
  if (!token) {
    throw new Error(`login 응답에서 accessToken을 못 찾음(${email}): ${res.body}`);
  }
  return token;
}

export function login() {
  requireAuthEnv();
  return loginAs(LOGIN_EMAIL, LOGIN_PASSWORD);
}

export function authHeaders(token) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}
