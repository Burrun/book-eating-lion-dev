moved {
  from = module.edge_routing
  to   = module.edge_routing[0]
}

moved {
  from = aws_ssm_parameter.cloudfront_distribution_id
  to   = aws_ssm_parameter.cloudfront_distribution_id[0]
}
