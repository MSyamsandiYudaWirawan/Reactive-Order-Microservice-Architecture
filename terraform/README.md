# AWS ECS Fargate Deployment (Terraform)

Infrastructure as Code for deploying the Reactive Order Microservice Architecture to AWS using ECS Fargate and managed services.

![Architecture Diagram](../diagram/AWS_ECS_Architecture.png)

---

## Why AWS ECS Fargate?

| Decision | Rationale |
|----------|-----------|
| **ECS Fargate over EKS** | No cluster management overhead. Pay only for running containers. EKS adds ~$73/month just for the control plane — overkill for this architecture. Fargate is serverless containers: define CPU/memory, AWS handles the rest. |
| **ECS Fargate over EC2** | No server patching, no capacity planning. Each service gets exactly the resources it needs (256 CPU / 512MB). Perfect for microservices that scale independently. |
| **Single cluster, multiple services** | All 6 services share one ECS cluster but run as independent services with their own task definitions. Simpler management, independent deployments. |

---

## Why These AWS Managed Services?

### Amazon MSK (Managed Kafka) over Self-Hosted Kafka

- Kafka is operationally complex — broker rebalancing, ZooKeeper management, partition reassignment
- MSK handles all of this: patching, monitoring, broker replacement
- KRaft mode (`3.7.x.kraft`) — no ZooKeeper dependency, simpler architecture
- The application already uses 13 Kafka topics; MSK is a drop-in replacement with zero code changes

### Amazon ElastiCache (Redis) over Self-Hosted Redis

- Redis is used for idempotency keys (24h TTL) and event deduplication
- ElastiCache provides automatic failover, patching, and backup
- Single-node `cache.t3.micro` for demo — production would use Multi-AZ replication

### Amazon RDS (PostgreSQL) over Self-Hosted PostgreSQL

- 5 separate RDS instances enforce the **database-per-service pattern** at the infrastructure level
- Each service has full schema autonomy — no shared database, no coupling
- RDS handles backups, patching, and point-in-time recovery
- `db.t3.micro` with gp3 storage — smallest footprint for demo/testing

### AWS Secrets Manager over Environment Variables

- No hardcoded credentials anywhere in code or Terraform state
- DB passwords are randomly generated (`random_password`, 24 chars, no special chars for R2DBC compatibility)
- JWT RSA keys stored as secrets — ECS injects them at container startup
- `recovery_window_in_days = 0` allows instant cleanup on `terraform destroy`

### ECS Service Connect over Eureka/Cloud Map API

- Replaces Docker Compose internal DNS with zero application code changes
- Services call each other by name: `http://auth:8081`, `http://order:8082`
- No Eureka server needed — aligns with the Kubernetes-native design philosophy
- Service Connect handles health checking and traffic routing internally

---

## Why This Network Architecture?

### VPC with Public + Private Subnets

```
Internet → ALB (public subnet) → gateway-service (private subnet) → other services (private subnet)
                                                                   → RDS / MSK / Redis (private subnet)
```

**Why not put services in the public subnet?**
- Services would be directly exposed to the internet — security risk
- ALB provides a single controlled entry point with health checks and SSL termination
- Private subnets mean RDS, MSK, and Redis are completely unreachable from outside the VPC

### NAT Gateway — Why It's Needed

Private subnet services can't reach the internet directly. But they need outbound access to:
- Pull Docker images from ECR
- Reach AWS APIs (CloudWatch, Secrets Manager)

NAT Gateway provides **outbound-only** internet access — nothing from the internet can initiate a connection inward.

### ALB — Why It's Needed Even with 1 Pod

The ALB isn't just for load balancing. It solves a fundamental networking problem:

1. **ECS Fargate tasks get private IPs only** — not reachable from the internet
2. **ALB sits in the public subnet** with a public DNS name — the only thing reachable from outside
3. **Bridges public-to-private** — forwards traffic to gateway-service in the private subnet
4. **Health checks** — stops routing to unhealthy tasks during deployments
5. **Future scaling** — when gateway-service scales to N pods, ALB distributes automatically

### Security Groups — Defense in Depth

```
Internet → [ALB SG: port 80 from anywhere]
              → [Gateway SG: port 8080 from ALB only]
                  → [ECS SG: ports 8080-8085 between services]
                      → [DB SG: port 5432 from ECS only]
                      → [Messaging SG: ports 6379/9092 from ECS only]
```

Each layer only accepts traffic from the layer above it. Even if one security group is misconfigured, the others still protect.

---

## Why Remote State (S3 + DynamoDB)?

| Concern | Solution |
|---------|----------|
| **Team collaboration** | S3 stores shared state — everyone sees the same infrastructure |
| **Concurrent access** | DynamoDB locking prevents two `terraform apply` from corrupting state |
| **State durability** | S3 versioning — can roll back to any previous state |
| **Encryption** | AES-256 server-side encryption on the state file |

For solo development, local state works fine. But remote state is production best practice and demonstrates the pattern.

---

## Why `for_each` Everywhere?

The Terraform code uses `for_each` extensively (RDS × 5, ECR × 6, CloudWatch alarms × 6) because:

- **DRY** — one resource block defines all 5 databases with different configs
- **Easy to add/remove services** — add a key to the map, `terraform apply`
- **Independent lifecycle** — can target individual resources (`terraform apply -target=aws_db_instance.services["payment"]`)

---

## Cost Optimization Decisions

| Decision | Why |
|----------|-----|
| **Single AZ deployment** | Multi-AZ doubles cost for RDS/MSK. Single AZ is fine for demo. |
| **Smallest instance types** | `t3.micro` / `t3.small` — burstable, cheapest available |
| **7-day log retention** | CloudWatch logs auto-delete after 7 days — no accumulating storage cost |
| **No Container Insights** | Disabled — adds ~$0.01/container/hour. Not needed for demo. |
| **No encryption in transit (MSK)** | PLAINTEXT for demo simplicity. Production would use TLS. |
| **`skip_final_snapshot`** | No RDS snapshots on destroy — faster cleanup, no leftover costs |

**Estimated cost: ~$0.29/hr (~$0.58 for a 2-hour demo)**

Always run `terraform destroy` after testing.

---

## Deployment

### Prerequisites

- AWS CLI configured with appropriate credentials
- Terraform >= 1.0
- Docker (for building service images)

### Deploy

```bash
cd terraform/

# 1. Initialize and create remote state infrastructure
terraform init
terraform apply -target=module.tf-state

# 2. Migrate state to S3 (uncomment backend "s3" block first)
terraform init -migrate-state

# 3. Deploy everything
terraform apply

# 4. Access application
# Output: alb_dns_name → http://<ALB-DNS>
```

### Destroy

```bash
terraform destroy
```

---

## File Structure

```
terraform/
├── modules/
│   ├── keys/                  # RSA key pair for JWT (uploaded to Secrets Manager)
│   └── tf-state/              # S3 + DynamoDB for remote state management
├── 1-provider.tf              # AWS provider, backend config, tf-state module
├── 2-vpc.tf                   # VPC (10.0.0.0/16)
├── 3-subnets.tf               # 2 public + 2 private subnets (ALB requires 2 AZs)
├── 4-igw-nat.tf               # Internet Gateway + NAT Gateway
├── 5-routes.tf                # Route tables linking subnets to gateways
├── 6-sg.tf                    # Security groups (ALB, Gateway, ECS, DB, Messaging)
├── 7-secrets.tf               # Secrets Manager (JWT keys + DB credentials)
├── 8-rds.tf                   # RDS PostgreSQL × 5 (database-per-service)
├── 9-msk.tf                   # MSK Kafka (KRaft mode, single broker)
├── 10-elasticache.tf          # ElastiCache Redis (single node)
├── 11-ecr.tf                  # ECR repositories × 6 (one per service)
├── 12-iam.tf                  # IAM roles (execution + task) with secrets access
├── 13-alb.tf                  # ALB + target group + listener (gateway only)
├── 14-ecs-cluster.tf          # ECS cluster + Service Connect namespace + log groups
├── 15-ecs-services.tf         # Task definitions + ECS services × 6
├── 16-cloudwatch.tf           # CloudWatch alarms (5xx, running tasks, RDS CPU)
└── 17-outputs.tf              # ALB DNS, cluster name, VPC ID
```

Files are numbered for readability. Terraform resolves dependencies automatically regardless of file order.

---

## Local → AWS Service Mapping

| Local (Docker Compose) | AWS Managed Service | Why Managed |
|------------------------|--------------------|----|
| Docker containers | **ECS Fargate** | Serverless, no server management |
| Docker internal DNS | **ECS Service Connect** | Zero code changes, same hostname resolution |
| Kafka + Zookeeper | **Amazon MSK (KRaft)** | No ZooKeeper, auto-patching, monitoring |
| Redis | **ElastiCache** | Auto-failover, patching, backups |
| PostgreSQL × 5 | **RDS PostgreSQL × 5** | Backups, patching, point-in-time recovery |
| `.env` files | **Secrets Manager** | Encrypted, audited, no credentials in code |
| `docker compose logs` | **CloudWatch Logs** | Centralized, searchable, retention policies |
| — | **CloudWatch Alarms** | Automated alerting (5xx, service down, high CPU) |

---

## Region

**`ap-southeast-3` (Jakarta)** — chosen for lowest latency from Indonesia.

---

## Future: EKS Migration

This ECS Fargate deployment is Phase 2.5. After completing the Kubernetes manifests phase (Phase 4), the infrastructure will migrate to **Amazon EKS** with:

- EKS managed node groups replacing Fargate tasks
- Kubernetes-native service discovery (CoreDNS) replacing ECS Service Connect
- Horizontal Pod Autoscaler + Cluster Autoscaler
- AWS Load Balancer Controller for Ingress → ALB mapping
- PgBouncer as sidecar containers (already designed for this)

The Terraform code will be refactored to provision EKS instead of ECS, while keeping the same VPC, RDS, MSK, ElastiCache, and Secrets Manager infrastructure.
