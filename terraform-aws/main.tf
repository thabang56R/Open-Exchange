# RDS Postgres
resource "aws_db_instance" "openex_db" {
  allocated_storage    = 20
  engine               = "postgres"
  instance_class       = "db.t3.micro"    
  db_name              = "openex"         
  username             = "openex_user"
  password             = "securepassword123"
  skip_final_snapshot  = true
}

