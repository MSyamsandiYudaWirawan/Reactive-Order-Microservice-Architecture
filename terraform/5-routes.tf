resource "aws_route_table" "private" {
  vpc_id = aws_vpc.vpc.id

  // whitelist all network to go outside
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.nat.id
  }
  tags = {
    Name = "route_table_private"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.vpc.id

  // whitelist all network to go inside
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }
  tags = {
  Name = "route_table_public" }
}

// connect subnets to route tables to gateway
// Private subnets → private route table → NAT Gateway
// Public subnets → public route table → Internet Gateway

resource "aws_route_table_association" "private-ap-southeast-3a" {
  subnet_id = aws_subnet.private-ap-southeast-3a.id
  route_table_id = aws_route_table.private.id
  depends_on = [aws_subnet.private-ap-southeast-3a]
}
resource "aws_route_table_association" "private-ap-southeast-3b" {
  subnet_id = aws_subnet.private-ap-southeast-3b.id
  route_table_id = aws_route_table.private.id
  depends_on = [aws_subnet.private-ap-southeast-3b]
}
resource "aws_route_table_association" "public-ap-southeast-3a" {
  subnet_id = aws_subnet.public-ap-southeast-3a.id
  route_table_id = aws_route_table.public.id
  depends_on = [aws_subnet.public-ap-southeast-3a]
}
resource "aws_route_table_association" "public-ap-southeast-3b" {
  subnet_id = aws_subnet.public-ap-southeast-3b.id
  route_table_id = aws_route_table.public.id
  depends_on = [aws_subnet.public-ap-southeast-3b]
}
