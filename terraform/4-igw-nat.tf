resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.vpc.id

  tags = {
    Name = "reactive-order-igw"
  }
}

resource "aws_eip" "eip" {
  tags = {
    Name = "reactive-order-eip"
  }
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.eip.id
  subnet_id     = aws_subnet.public-ap-southeast-3a
  tags = {
    Name = "reactive-order-nat"
  }

  depends_on = [aws_internet_gateway.igw]
}
