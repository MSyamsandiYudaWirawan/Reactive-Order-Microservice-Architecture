// we need minimal 4 subnet -> 2 private + 2 public to fulfill aws requirement for ALB (Application load balancer and other)
// subnet A is the main subnet , subnet B is just empty

resource "aws_subnet" "private-ap-southeast-3a" {
  vpc_id = aws_vpc.vpc.id
  cidr_block = "10.0.0.0/19" // 19  means 19-16 = 3 -> 2^3 = 8 possible subnet
  availability_zone = "ap-southeast-3a"

  tags = {
    Name = "private-ap-southeast-3a"
  }
}
resource "aws_subnet" "private-ap-southeast-3b" {
  vpc_id = aws_vpc.vpc.id
  cidr_block = "10.0.32.0/19"
  availability_zone = "ap-southeast-3b"

  tags = {
    Name = "private-ap-southeast-3b"
  }
}

resource "aws_subnet" "public-ap-southeast-3a" {
  vpc_id = aws_vpc.vpc.id
  cidr_block = "10.0.64.0/19"
  availability_zone = "ap-southeast-3a"

  tags = {
    Name = "public-ap-southeast-3a"
  }
}

resource "aws_subnet" "public-ap-southeast-3b" {
  vpc_id = aws_vpc.vpc.id
  cidr_block = "10.0.96.0/19"
  availability_zone = "ap-southeast-3b"

  tags = {
    Name = "public-ap-southeast-3b"
  }
}