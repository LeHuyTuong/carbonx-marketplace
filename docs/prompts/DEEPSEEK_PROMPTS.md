# Bộ Prompt Triển Khai CarbonX DDD Modular Monolith → Microservices (Dành cho DeepSeek)

> **Cách dùng**: Giao cho DeepSeek chạy **tuần tự từ Prompt 1 → Prompt 7** ngay trong thư mục `backend/carbonx-backend`.
> Sau MỖI prompt, bắt buộc chạy `mvn test -B` để xác nhận `ModuleBoundaryArchTest` (ArchUnit) và các test mới đều **PASS 100%** trước khi sang prompt tiếp theo.

---

## Prompt 0 — System Context (Dán kèm đầu mỗi phiên làm việc với DeepSeek)

```text
Bạn là Senior Java/Spring Boot Architect triển khai dự án CarbonX Marketplace tại `backend/carbonx-backend` (Java 17, Spring Boot 3.3, MySQL 8, Flyway, ArchUnit, Testcontainers).

NGUỒN SỰ THẬT BẮT BUỘC PHẢI ĐỌC TRƯỚC KHI CODE:
1. `MASTER-PLAN.md` ở thư mục gốc repo (10 tầng kiến trúc & luật bắt buộc).
2. `docs/diagrams/services/01..05` (ERD từng Bounded Context bằng tiếng Anh).
3. `backend/carbonx-backend/src/main/resources/db/migration/V1..V5*.sql` (lược đồ bảng chuẩn — KHÔNG thêm FK chéo module).
4. `backend/Market_carbon/` (chỉ đọc để tham khảo công thức nghiệp vụ cũ như VM0038, 8 luật gian lận, VNPay HMAC — KHÔNG copy cấu trúc layered cũ).

LUẬT CỨNG (ArchUnit sẽ làm FAIL BUILD nếu vi phạm):
- 6 module độc lập: `identity`, `mrv`, `registry`, `trading`, `ledger`, `notification` + kernel `shared`.
- Mỗi module chỉ có 4 layer: `api`, `application`, `domain`, `infrastructure`.
- Một module CHỈ ĐƯỢC import package `..api..` của module khác và `..shared..`. CẤM import `..domain..`, `..application..`, `..infrastructure..` của module khác.
- Khung interface DDD đã dựng sẵn 100% (`application/port/in/*UseCase`, `application/port/out/*Port`, `api/*Port`, `api/*Event`, `shared/outbox/OutboxPort` & `DomainEventPublisher`) — KHÔNG sửa chữ ký interface đã chốt, chỉ implement đúng contract.
- Mọi thay đổi tài chính/tồn kho phải có khoá bi quan (`@Lock(PESSIMISTIC_WRITE)`), `Idempotency-Key`, và ghi Domain Event vào `OutboxPort.append(...)` trong cùng transaction.
- Mọi endpoint phải có `@PreAuthorize` rõ ràng; KHÔNG trả trực tiếp JPA Entity ra REST controller.
```

---

## Prompt 1 — Module `identity` (Auth, JWT, KYC, Vehicle Registry)

```text
Hãy triển khai hoàn chỉnh Bounded Context `com.carbonx.identity` khớp với `V1__init_identity.sql` và `docs/diagrams/services/01-identity-erd.png`.

Yêu cầu chi tiết:
1. `domain`: Entities `User`, `Company`, `KycProfile`, `Vehicle` (rich domain methods: `approveKyc()`, `rejectKyc()`, `deactivate()`), enums `Role` (`EV_OWNER`, `COMPANY`, `CVA`, `ADMIN`), `KycStatus`.
2. `infrastructure`: JPA Repositories (`UserRepository`, `CompanyRepository`, `KycProfileRepository`, `VehicleRepository`), `SecurityConfig` (JWT stateless, `@EnableMethodSecurity`, tắt `httpBasic`, Swagger bật theo `app.swagger.enabled`, fail-closed `app.security.jwt-secret`, CORS theo `app.cors.allowed-origins`), `JwtTokenProvider`, `JwtAuthFilter`.
3. `application`: Use cases đăng ký, đăng nhập, KYC chủ xe/doanh nghiệp, CRUD xe điện (`VehicleService`), và implement `IdentityQueryPort` (đã có sẵn interface tại `com.carbonx.identity.api.IdentityQueryPort`).
4. `api`: REST Controllers giữ đúng path tương thích Frontend (`/api/v1/auth/**`, `/api/v1/kyc/**`, `/api/v1/vehicles/**`) với `@PreAuthorize` chặt chẽ (tham khảo `docs/security/SECURITY_AUDIT.md`).
5. `test`: Viết integration test kiểm tra `EV_OWNER` gọi `GET /api/v1/vehicles/list` phải trả `403 Forbidden`, và `mvn test -B` pass cả `ModuleBoundaryArchTest`.
```

---

## Prompt 2 — Module `mrv` (Projects, Applications, CSV Upload, 8 Fraud Rules, AI Scoring)

```text
Hãy triển khai hoàn chỉnh Bounded Context `com.carbonx.mrv` khớp với `V2__init_mrv.sql` và `docs/diagrams/services/02-mrv-erd.png`.

Yêu cầu chi tiết:
1. `domain`: Entities `Project`, `ProjectApplication`, `EmissionReport`, `FraudAnalysis` kèm state machine chặt:
   - `ProjectApplication`: `SUBMITTED` -> `CVA_APPROVED` / `NEEDS_REVISION` / `CVA_REJECTED` -> `ADMIN_APPROVED` / `ADMIN_REJECTED`.
   - `EmissionReport`: `SUBMITTED` -> `CVA_APPROVED` / `CVA_REJECTED` -> `ADMIN_APPROVED` / `ADMIN_REJECTED` -> `CREDIT_ISSUED`.
   - CẤM join sang bảng `companies`/`vehicles`; chỉ lưu `companyId`, `companyNameSnap`, `verifierId`.
2. `application`:
   - Gọi `IdentityQueryPort` (chỉ qua package `com.carbonx.identity.api`) để lấy tên công ty và kiểm tra biển số xe hợp lệ trong CSV.
   - Chuyển 8 luật kiểm tra gian lận (`DQ1`..`DQ8`, tham khảo `backend/Market_carbon/.../analysis/rules/`) sang `com.carbonx.mrv.domain.rules`.
   - Khi Admin duyệt `EmissionReport` (`ADMIN_APPROVED`), ghi `EmissionReportApprovedEvent` (đã có sẵn ở `com.carbonx.mrv.api`) vào `OutboxWriter` trong cùng `@Transactional`.
3. `api`: REST Controllers `/api/v1/projects/**`, `/api/v1/project-applications/**`, `/api/v1/emission-reports/**` có `@PreAuthorize` cho từng vai `COMPANY`, `CVA`, `ADMIN`.
4. `test`: Unit test state machine (chặn nhảy bước `SUBMITTED` -> `ADMIN_APPROVED`) + `mvn test -B` pass `ModuleBoundaryArchTest`.
```

---

## Prompt 3 — Module `registry` (Credit Batches, Credit Holdings 80/20 Split, Serial Movements, Retirement)

```text
Hãy triển khai hoàn chỉnh Bounded Context `com.carbonx.registry` khớp với `V3__init_registry.sql` và `docs/diagrams/services/03-registry-erd.png`.

MỤC TIÊU CỐT LÕI (giải quyết bài toán lô 100 tín chỉ -> bán 80 giữ lại 20 để dùng):
1. `domain`:
   - `CreditBatch` (BẤT BIẾN sau khi tạo): tính theo công thức VM0038 `(totalEnergy * emissionFactor / 1000) * (1 - buffer - uncertainty - leakage)`, sinh `batchCode` và dải serial `serialStart..serialEnd`.
   - `CreditHolding` (sở hữu theo công ty trên 1 lô): có `availableQty`, `lockedQty`, `retiredQty`, `serialStart`, `serialEnd`, `@Version` + phương thức domain `lock(qty)`, `unlock(qty)`, `deductLocked(qty)`, `retire(qty)`.
   - `CreditMovement`: sổ cái di chuyển bất biến (`ISSUE`, `LOCK`, `UNLOCK`, `TRANSFER_OUT`, `TRANSFER_IN`, `RETIRE`) ghi rõ `quantity` và `serialStart..serialEnd`.
   - `RetirementCertificate`: sinh từ `CreditHolding` khi doanh nghiệp triệt tiêu phần còn lại (ví dụ 20 tín chỉ giữ lại).
2. `application`:
   - Listener nhận `EmissionReportApprovedEvent` (từ `OutboxEvent` với `eventType = "mrv.emission-report.approved"`) để phát hành 1 `CreditBatch` + 1 `CreditHolding` ban đầu cho `issuerCompanyId` + 1 `CreditMovement(ISSUE)` (idempotent theo `sourceReportId`).
   - Implement `RegistryCommandPort` (đã có sẵn tại `com.carbonx.registry.api.RegistryCommandPort`):
     - `lockCreditsForListing`: khoá `FOR UPDATE` trên `CreditHolding`, chuyển `availableQty -> lockedQty`, ghi `CreditMovement(LOCK)`.
     - `transferSoldCredits`: trừ `lockedQty` của người bán, tạo hoặc cộng `CreditHolding` mới cho người mua trỏ cùng `batchId` kèm dải serial con chính xác, ghi `TRANSFER_OUT` + `TRANSFER_IN`.
3. `api`: `/api/v1/credits/batches/**`, `/api/v1/credits/holdings/**`, `/api/v1/credits/retirements/**`.
4. `test`: Test kịch bản: Phát hành lô 100 -> Lock 80 -> Bán 80 cho Company B -> Assert Company A còn đúng `availableQty = 20`, Company B có `availableQty = 80` cùng `batchId`, sau đó Company A triệt tiêu 20 còn lại thành công. Chạy `mvn test -B` pass 100%.
```

---

## Prompt 4 — Module `trading` (Market Listings & Luồng Mua Tức Thì 1 Bước)

```text
Hãy triển khai hoàn chỉnh Bounded Context `com.carbonx.trading` khớp với `V4__init_trading.sql` và `docs/diagrams/services/04-trading-erd.png`.

Yêu cầu nghiệp vụ đã chốt trong MASTER-PLAN.md:
- Luồng mua tín chỉ bằng ví là **THANH TOÁN TỨC THÌ 1 BƯỚC** (bỏ trạng thái `PENDING` thừa).
- Khi gọi `POST /api/v1/orders` (bắt buộc header `Idempotency-Key` hoặc tự sinh theo request):
  1. Kiểm tra `idempotencyKey` trong `trade_orders` — nếu đã tồn tại thì trả lại đúng đơn đã khớp (idempotent).
  2. Khoá `MarketListing` bằng `@Lock(PESSIMISTIC_WRITE)` (`FOR UPDATE`), kiểm tra `availableQty >= quantity` và `buyerCompanyId != sellerCompanyId`.
  3. Tính `grossAmount = unitPrice * quantity`, `platformFee = grossAmount * 0.05`, `netSellerAmount = grossAmount - platformFee` bằng `com.carbonx.shared.domain.Money`.
  4. Gọi `LedgerSettlementPort.settleTradeOrder(...)` để trừ ví người mua & cộng ví người bán.
  5. Gọi `RegistryCommandPort.transferSoldCredits(...)` để chuyển `CreditHolding` cho người mua.
  6. Trừ `listing.availableQty`, lưu `TradeOrder(FINISHED)` và ghi `OrderSettledEvent` vào `OutboxWriter` trong cùng transaction.
- Khi niêm yết (`POST /api/v1/marketplace/listings`): gọi `RegistryCommandPort.lockCreditsForListing(...)`. Khi huỷ niêm yết: gọi `RegistryCommandPort.releaseLockedCredits(...)`.
- Test đồng thời bằng Testcontainers MySQL 8: bắn 8 thread song song vào cùng 1 `Idempotency-Key` hoặc cùng 1 `Listing` còn 1 tín chỉ -> đảm bảo chỉ khớp đúng 1 lần, không bán âm.
```

---

## Prompt 5 — Module `ledger` (Immutable Wallet Ledger, Withdrawals & Payout Provenance)

```text
Hãy triển khai hoàn chỉnh Bounded Context `com.carbonx.ledger` khớp với `V5__init_ledger_and_outbox.sql` và `docs/diagrams/services/05-ledger-erd.png`.

MỤC TIÊU CỐT LÕI (giải quyết bài toán chủ xe biết tiền chia từ lô tín chỉ nào, ID nào):
1. `domain`: Entities `Wallet`, `WalletTransaction` (append-only, KHÔNG có setter sửa số tiền), `BankAccount`, `Withdrawal`, `ProfitDistribution`, `PayoutItem`.
2. `application`:
   - Implement `LedgerSettlementPort.settleTradeOrder(...)`: khoá 2 `Wallet` theo **thứ tự `id` tăng dần** (`FOR UPDATE`) để tránh deadlock; kiểm tra `balanceUsd >= grossAmount`; ghi 2 dòng `WalletTransaction` (`ORDER_DEBIT`, `ORDER_CREDIT`) kèm `balanceBefore` / `balanceAfter` và `idempotencyKey`.
   - `ProfitSharingService`: nhận danh sách đóng góp `(evOwnerId, kwh)` + `sourceReportId` + `creditBatchId` + `batchCode` + `serialRange`, tính `allocatedCredits` và `amountUsd`, chạy thread pool `profitPool` với `claimToken` để cộng vào ví từng chủ xe, lưu `walletTxId` vào `PayoutItem` và ghi `ProfitDistributedEvent` vào `OutboxWriter`.
   - Endpoint truy vết cho Chủ xe: `GET /api/v1/wallet/transactions/{txId}/provenance` (hoặc đính kèm trong lịch sử ví) trả về chính xác `creditBatchId`, `batchCode`, `serialRange`, `sourceReportId`, `period`, `kwhContributed`, `allocatedCredits`.
3. `test`: Testcontainers MySQL 8 kiểm chứng bất biến `sum(wallet_transactions) == wallet.balance_usd` dưới tải đồng thời + kiểm chứng truy vết ngược từ `WalletTransaction` về đúng `creditBatchId`.
```

---

## Prompt 6 — Module `notification` (SSE Realtime + Outbox Consumer) & Đồng bộ Frontend

```text
1. Trong `com.carbonx.notification`:
   - Nâng cấp `DomainEventNotificationListener` lắng nghe `mrv.emission-report.approved`, `trading.order.settled`, `ledger.profit.distributed` từ `OutboxEvent`, lưu vào bảng `notifications` và đẩy realtime qua `SseEmitter` (`GET /api/v1/notifications`, bọc `try/catch` an toàn khi client đóng tab).
2. Trong `frontend/src/pages/company/CompanyOrdersPage.tsx` và `frontend/src/pages/MarketplacePage.tsx`:
   - Cập nhật luồng mua khớp 1 bước: bấm "Mua ngay" gửi `Idempotency-Key` (UUID), hiển thị đơn `Hoàn tất` ngay lập tức, bỏ 2 nút `Hoàn tất` / `Huỷ đơn` thừa trên đơn ví.
   - Trong `frontend/src/features/wallet/components/TransactionList.tsx`: hiển thị thêm badge nguồn gốc lô tín chỉ (`batchCode`, `allocatedCredits`, `kWh`) cho các giao dịch `PROFIT_PAYOUT`.
3. Chạy `mvn test -B` và `cd frontend && npm run build && npm test`.
```

---

## Prompt 7 — Pha 2: Tách Microservices với Apache Kafka (KRaft) & Spring Cloud Gateway

```text
Chỉ chạy sau khi Prompt 1 -> Prompt 6 đều xanh 100%.

1. Thêm service `kafka` (Apache Kafka KRaft mode, không cần Zookeeper) và `api-gateway` (Spring Cloud Gateway xác thực JWT và gắn header `X-User-Id`, `X-User-Role`, `X-Request-Trace`) vào `docker-compose.yml`.
2. Tạo adapter `KafkaOutboxRelay implements DomainEventPublisher` (kích hoạt bằng `@Profile("kafka")`) bắn bản ghi từ bảng `outbox_events` lên các Kafka topic (`mrv.events`, `trading.events`, `ledger.events`).
3. Bóc `notification` thành `backend/notification-service` độc lập (Kafka Consumer + SSE) và bóc `mrv` thành `backend/mrv-service` độc lập (CSV + 8 luật gian lận + AI scoring + Kafka Producer), định tuyến toàn bộ qua `api-gateway:8080`.
4. Kiểm chứng toàn bộ hệ thống bằng `docker compose up --build` + xem distributed trace xuyên suốt `api-gateway -> core -> kafka -> notification-service` trên Jaeger (`http://localhost:16686`) và metrics trên Grafana (`http://localhost:3001`).
```
