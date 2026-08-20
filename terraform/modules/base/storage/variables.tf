variable "frontend_bucket_name" {
  description = "React 정적 빌드 결과물을 담을 버킷 이름 (전역 유일)"
  type        = string
}

variable "media_bucket_name" {
  description = "도서 미디어 에셋(표지 이미지 등)을 담을 버킷 이름 (전역 유일)"
  type        = string
}
