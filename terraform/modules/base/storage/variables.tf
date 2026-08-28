variable "frontend_bucket_name" {
  description = "React 정적 빌드 결과물을 담을 버킷 이름 (전역 유일)"
  type        = string
}

variable "media_bucket_name" {
  description = "도서 미디어 에셋(표지 이미지 등)을 담을 버킷 이름 (전역 유일)"
  type        = string
}

variable "media_cors_allowed_origins" {
  description = <<-EOT
    presigned GET URL(EbookAccess)을 브라우저가 직접 fetch 할 때 필요한 CORS 허용 origin.
    S3 버킷 기본값은 CORS 설정이 아예 없어 응답에 Access-Control-Allow-Origin 이 없고,
    브라우저는 200을 받고도 "CORS Missing Allow Origin"으로 스크립트에서 응답 본문을
    못 읽는다(2026-08-28 dev 실배포에서 실제로 겪음 - presign 자체는 IRSA 수정 후
    정상 발급됐는데 이 단계에서 막힘).
  EOT
  type        = list(string)
}
