/** 목업 응답에 약간의 지연을 주어 로딩 상태(스켈레톤)를 확인할 수 있게 한다. */
export function mockDelay<T>(value: T, ms = 400): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}
