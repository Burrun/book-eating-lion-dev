export const MOCK_TOKEN_RESPONSE = {
  accessToken: "mock-access-token",
  refreshToken: "mock-refresh-token",
  tokenType: "Bearer",
  expiresIn: 3600,
};

export function mockLogin(email, password) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (password === "wrong") {
        reject({
          response: {
            data: {
              success: false,
              message: "이메일 또는 비밀번호가 올바르지 않습니다.",
              error: { code: "INVALID_CREDENTIALS", message: "이메일 또는 비밀번호가 올바르지 않습니다." },
            },
          },
        });
        return;
      }
      resolve(MOCK_TOKEN_RESPONSE);
    }, 400);
  });
}
