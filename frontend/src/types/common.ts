/** 페이지네이션 결과 (백엔드 Spring Page를 UI가 쓰기 좋은 형태로 줄인 것) */
export interface Paged<T> {
  items: T[]
  /** 0부터 시작 */
  page: number
  totalPages: number
  totalElements: number
}
