variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access the ALB. Restrict to your IP for demo."
  type        = list(string)
  default     = ["118.99.80.45/32"] # Change to ["YOUR_IP/32"] for demo security
}