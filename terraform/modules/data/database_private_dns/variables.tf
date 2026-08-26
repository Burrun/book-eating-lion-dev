variable "environment" {
  description = "Deployment environment name (dev or prod)."
  type        = string
}

variable "vpc_id" {
  description = "VPC associated with the Route 53 private hosted zone."
  type        = string
}

variable "zone_name" {
  description = "Private hosted zone name for database records."
  type        = string
}

variable "writer_target" {
  description = "DNS target used by the database writer record."
  type        = string
}

variable "reader_target" {
  description = "DNS target used by the database reader record."
  type        = string
}
