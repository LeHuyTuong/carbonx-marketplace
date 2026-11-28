# Bộ Prompt Triển Khai CarbonX 12-Module Microservices + BFF (Dành cho DeepSeek)

> **Kiến trúc đã dựng sẵn**: Maven Multi-Module Reactor **12 modules** (`13 reactor items`) tại `backend/`:
> - Hạ tầng Spring Cloud (`2023.0.3`): `common-kernel`, `config-server` (`:8888` + Spring Cloud Bus Kafka), `discovery-server` (`:8761` Netflix Eureka), `api-gateway` (`:8080` WebFlux + LoadBalancer + Resilience4j + Swagger UI tập trung).
> - **2 tầng Backend-for-Frontend không trạng thái (Stateless BFF)**:
>   - `web-bff` (`:8091`, `/api/web/**`): gom dữ liệu tổng quan đa service song song cho Web SPA (Admin, CVA, Company, EV Owner) kèm graceful degradation (`partialData=true`).
>   - `mobile-bff` (`:8092`, `/api/mobile/**`): tối ưu băng thông 4G/5G cho ứng dụng Mobile Chủ xe điện (1 request Home duy nhất, cursor pagination, cấp Presigned URL chụp ảnh KYC/cà-vẹt xe lên thẳng S3, đăng ký FCM/APNs push token).
> - 6 Microservices độc lập (**Database-per-Service** + Flyway + DDD + Logical CQRS + OpenFeign + Resilience4j + Kafka Outbox + Distroless Dockerfile):
>   1. `identity-service` (`:8081`, DB `carbonx_identity`, OAuth2 Google + JWT)
>   2. `mrv-service` (`:8082`, DB `carbonx_mrv`, Spring AI `ChatClient` + 8 Fraud Rules)
>   3. `registry-service` (`:8083`, DB `carbonx_registry` + Redis Cache + 80/20 `CreditHoldings`)
>   4. `trading-service` (`:8084`, DB `carbonx_trading` + Order Saga Orchestrator + Compensating Transactions)
>   5. `ledger-service` (`:8085`, DB `carbonx_ledger` + Immutable `WalletTransactions` + `PayoutItems` Provenance)
>   6. `notification-service` (`:8086`, DB `carbonx_notification` + Kafka Consumer + SSE Realtime + Email + FCM/APNs Push)
> - Đóng gói & K8s: `Dockerfile` Distroless (`gcr.io/distroless/java17-debian12:nonroot`), `docker-compose.yml`, `k8s/{configmap,secret,deployment,service}.yaml`.

---

## Prompt 0 — System Context (Dán kèm đầu mỗi phiên làm việc với DeepSeek)

```text
Bạn là Principal Distributed Systems Architect triển khai các Microservices của CarbonX tại `backend/`.

NGUỒN SỰ THẬT BẮT BUỘC ĐỌC TRƯỚC KHI CODE:
1. `MASTER-PLAN.md` (10 tầng kiến trúc & luật bắt buộc).
2. `docs/diagrams/architecture.png` & `docs/diagrams/services/01..05` (Architecture & 5 Service ERDs).
3. `backend/<service>/src/main/resources/db/migration/V1__*.sql` (lược đồ Database-per-Service chuẩn).
4. `backend/common-kernel/` (`Money`, `SerialRange`, `IntegrationEvents`, `OutboxPort`, `CleanCodeArchRules`).
5. `backend/Market_carbon/` (chỉ đọc tham khảo công thức nghiệp vụ cũ: VM0038, 8 luật gian lận DQ1..DQ8, VNPay HMAC — KHÔNG copy kiến trúc monolith cũ).

LUẬT CLEAN CODE & KIẾN TRÚC BẮT BUỘC (`ServiceArchitectureTest` chạy tự động trong mỗi service, vi phạm sẽ FAIL BUILD):
- Giữ nguyên chữ ký các interface `*CommandUseCase`, `*QueryUseCase`, `*Port` đã định nghĩa trong `application/port/in` và `application/port/out`.
- `@RestController` chỉ gọi `*CommandUseCase` / `*QueryUseCase`, CẤM gọi thẳng `..infrastructure..` / Repository và CẤM trả trực tiếp class `@Entity`.
- Tầng `..domain..` thuần khiết, CẤM import `..api..` hoặc `..infrastructure..`.
- CẤM `@Autowired` field injection (chỉ dùng Constructor Injection `@RequiredArgsConstructor`), CẤM `System.out`/`printStackTrace`.
- Mọi `@FeignClient`, lời gọi `Spring AI ChatClient`, và cổng thanh toán phải cấu hình `spring.cloud.openfeign.circuitbreaker.enabled=true` và bọc `Resilience4j` (`@CircuitBreaker`, `@Retry`, `@RateLimiter`, `@Bulkhead`) kèm class `fallback` / `fallbackMethod` rõ ràng; tuyệt đối không dùng `@Autowired` field injection.
- Sau mỗi prompt, bắt buộc chạy `cd backend && mvn test -B` xanh 12/12 modules trước khi báo hoàn tất.
```

---

## Prompt 1 — `identity-service` (`:8081`, DB `carbonx_identity`, OAuth2 Google + JWT)

```text
Triển khai hoàn chỉnh `backend/identity-service` khớp `V1__init_identity.sql`:
1. Implement `IdentityCommandUseCase` và `IdentityQueryUseCase` (tách riêng CommandService `@Transactional` và QueryService `@Transactional(readOnly = true)`).
2. Tích hợp `spring-boot-starter-oauth2-client` (Google OIDC callback chuyển sang URL fragment `#token=`) + phát hành/xác thực JWT stateless chứa `userId`, `email`, `role` (`ROLE_EV_OWNER`, `ROLE_COMPANY`, `ROLE_CVA`, `ROLE_ADMIN`).
3. Cung cấp internal REST endpoints cho `mrv-service` gọi qua OpenFeign (`GET /api/v1/internal/companies/{id}`, `POST /api/v1/internal/vehicles/verify-plates`) và ghi `VehicleRegisteredEvent` vào `OutboxPort` -> Kafka.
4. Viết test kiểm chứng RBAC (`403 Forbidden` khi `EV_OWNER` truy cập `/api/v1/vehicles/list`) và chạy `cd backend && mvn test -pl identity-service -am -B` xanh 100%.
```

---

## Prompt 2 — `mrv-service` (`:8082`, DB `carbonx_mrv`, **Spring AI `ChatClient`** + **Resilience4j** + OpenFeign)

```text
Triển khai hoàn chỉnh `backend/mrv-service` khớp `V1__init_mrv.sql`:
1. Implement `MrvCommandUseCase` và `MrvQueryUseCase`, state machine duyệt 2 cấp (CVA -> Admin) cho `ProjectApplication` và `EmissionReport`, cùng 8 luật kiểm tra gian lận `DQ1`..`DQ8`.
2. Implement `IdentityServiceClientPort` bằng `@FeignClient(name = "identity-service")` bọc `@CircuitBreaker` + `@Retry`.
3. Implement `SpringAiScoringPort` bằng `org.springframework.ai.chat.client.ChatClient` (`spring-ai-openai-spring-boot-starter`):
   - `evaluateEmissionReport` (`POST /api/v1/reports/{id}/ai-score` cho CVA): gắn `@CircuitBreaker(name = "aiScoring", fallbackMethod = "fallbackRuleScore")` và `@Bulkhead(name = "aiScoring")` — khi LLM lỗi/timeout tự động trả điểm fallback tính từ 8 luật `DQ1..DQ8`.
   - `chatWithAssistant` (`POST /api/v1/ai/chat` cho `AiChatWidget`): gắn `@CircuitBreaker(name = "aiChat")` + `@RateLimiter(name = "aiChat")`.
4. Khi Admin duyệt báo cáo, ghi `EmissionReportApprovedEvent` vào `OutboxPort` và relay lên Kafka topic `mrv.events`.
5. Chạy `cd backend && mvn test -pl mrv-service -am -B` xanh 100%.
```

---

## Prompt 3 — `registry-service` (`:8083`, DB `carbonx_registry` + Redis Cache + 80/20 `CreditHoldings`)

```text
Triển khai hoàn chỉnh `backend/registry-service` khớp `V1__init_registry.sql`:
1. Kafka Consumer lắng nghe `EmissionReportApprovedEvent` (idempotent theo `sourceReportId`) để phát hành bất biến `CreditBatch` theo công thức VM0038 + tạo `CreditHolding` gốc + `CreditMovement(ISSUE)`.
2. Implement `RegistryCommandUseCase` và `RegistryQueryUseCase` (kết hợp Redis cache cho tra cứu nhanh `BatchProvenanceView`):
   - Giải quyết bài toán **Lô 100 -> Lock 80 -> Bán 80 cho Buyer Holding (`000001..000080`), giữ lại 20 ở Seller Holding (`000081..000100`) để Triệt tiêu**.
   - Cung cấp `compensateReleaseLockedCredits` cho bước bù trừ của Order Saga.
3. Test kịch bản 100 -> 80/20 + xuất PDF chứng chỉ triệt tiêu trên Testcontainers MySQL 8; chạy `cd backend && mvn test -pl registry-service -am -B` xanh 100%.
```

---

## Prompt 4 — `trading-service` (`:8084`, DB `carbonx_trading` + **6-State Saga Orchestrator + Outbox**)

```text
Triển khai hoàn chỉnh `backend/trading-service` khớp `V1__init_trading.sql` và `V2__saga_orchestration_state.sql` (BẮT BUỘC không dùng 2PC/XA, chỉ dùng Constructor Injection, CẤM @Autowired):
1. Implement `TradingCommandUseCase`, `TradingQueryUseCase`, `OrderSagaOrchestratorPort`, `OrderCompensationPort`, và `OrderSagaRemotePorts` (qua `@FeignClient(name = "ledger-service", fallback = ...)` & `@FeignClient(name = "registry-service", fallback = ...)` đã bật `spring.cloud.openfeign.circuitbreaker.enabled=true`).
2. Vòng đời 6 trạng thái chuẩn (`OrderSagaState`: `PENDING -> EXECUTING -> COMPLETED` hoặc `FAILED -> COMPENSATING -> COMPENSATED`) lưu tại `order_saga_instances` + `order_saga_transition_logs` (chỉ điều phối trạng thái/timeout/retry, KHÔNG nhét công thức tính thuế/phí chi tiết vào Orchestrator):
   - Bước 1 (`RESERVE_LISTING`): `PENDING -> EXECUTING`, kiểm tra `idempotencyKey`, khoá `MarketListing` (`FOR UPDATE`), tạo `ListingReservation(RESERVED)` + `TradeOrder(PENDING)`.
   - Bước 2 (`SETTLE_PAYMENT`): gọi `ledger-service` (`settleOrderPayment`). Nếu lỗi -> `FAILED -> COMPENSATING` -> `compensateCancelOrderAndReleaseListing` (`CANCELLED`) -> `COMPENSATED`.
   - Bước 3 (`TRANSFER_CREDITS`): gọi `registry-service` (`transferLockedCredits`). Nếu lỗi -> `FAILED -> COMPENSATING`: gọi ngược chiều `compensateRefundWalletSettlement` (Idempotent, Reversible, Reliable) -> `compensateCancelOrderAndReleaseListing` -> `COMPENSATED` + ghi `OrderCompensatedEvent` vào `outbox_events`!
   - Bước 4 (`FINALIZE_ORDER`): lưu `ListingReservation(COMMITTED)`, `TradeOrder(FINISHED)`, chuyển Saga sang `COMPLETED` và ghi `OrderSettledEvent` vào `outbox_events` trong cùng 1 local DB commit -> Kafka.
3. Viết test Saga happy path (`COMPLETED`), test Saga bù trừ ngược (`COMPENSATED` khi `registry-service` lỗi kèm re-play idempotent), test Circuit Breaker `OPEN -> Fallback` và test đồng thời 8 luồng; chạy `cd backend && mvn test -pl trading-service -am -B` xanh 100%.
```

---

## Prompt 5 — `ledger-service` (`:8085`, DB `carbonx_ledger` + Immutable Ledger + Payout Provenance)

```text
Triển khai hoàn chỉnh `backend/ledger-service` khớp `V1__init_ledger.sql`:
1. Implement `LedgerCommandUseCase` và `LedgerQueryUseCase`:
   - `settleOrderPayment` & `compensateOrderPayment`: khoá 2 ví theo thứ tự `id` tăng dần (`FOR UPDATE`) chống deadlock, ghi bất biến `WalletTransaction` kèm `balanceBefore`/`balanceAfter` và `idempotencyKey`.
   - `executeProfitDistribution`: gọi Feign lấy đóng góp kWh từ `mrv-service` và mã lô/serial từ `registry-service`, chạy thread pool `profitPool` kèm `claimToken` cộng ví từng chủ xe, lưu đầy đủ `creditBatchId`, `batchCodeSnap`, `serialRangeSnap`, `sourceReportId` vào `PayoutItem` và phát `ProfitDistributedEvent`.
   - `getTransactionProvenance`: trả về chính xác tiền chia từ lô tín chỉ nào, dải serial nào, báo cáo nào cho Chủ xe.
   - Tích hợp cổng thanh toán VNPay (HMAC-SHA512) / Stripe / PayPal bọc `@CircuitBreaker` + `@Retry`.
2. Test đối soát sổ cái & truy vết provenance bằng Testcontainers MySQL 8; chạy `cd backend && mvn test -pl ledger-service -am -B` xanh 100%.
```

---

## Prompt 6 — `notification-service` (`:8086`), JWT Filter trên `api-gateway` & Đồng bộ Frontend

```text
1. Trong `backend/notification-service`: implement `NotificationEventConsumerPort` (`@KafkaListener` idempotent theo `eventId`) lưu bảng `notifications`, đẩy realtime qua `SseEmitter` và gửi Email qua `JavaMailSender`.
2. Trong `backend/api-gateway`: thêm reactive `JwtAuthenticationGlobalFilter` kiểm tra chữ ký JWT tập trung tại Gateway và gắn header `X-User-Id`, `X-User-Role`, `X-Request-Trace` xuống 6 microservices.
3. Trong `frontend/`: đồng bộ màn `CompanyOrdersPage.tsx` (luồng mua 1 bước kèm `Idempotency-Key`) và `TransactionList.tsx` (hiển thị nguồn gốc lô tín chỉ `batchCode` / `serialRange` cho Chủ xe).
4. Chạy `cd backend && mvn clean test -B`, `cd frontend && npm run build && npm test`, và `docker compose up --build -d` kiểm tra toàn bộ hệ thống trên Eureka (`:8761`), Config Server (`:8888`), Gateway Swagger UI (`:8080/swagger-ui.html`), Jaeger (`:16686`) và Grafana (`:3001`).
```

---

## Prompt 7 — Tầng BFF Đa Thiết Bị: `web-bff` (`:8091`) & `mobile-bff` (`:8092`)

```text
Triển khai 2 module BFF không trạng thái (TUYỆT ĐỐI KHÔNG thêm JPA/@Entity/Database vào BFF — `ServiceArchitectureTest` sẽ chặn):
1. Trong `backend/web-bff` (`/api/web/**`):
   - Implement `WebBffQueryUseCase` & `WebBffCommandUseCase` kết hợp `WebDownstreamClientsPort` (`@FeignClient` tới `identity`, `mrv`, `registry`, `trading`, `ledger`).
   - Dùng `CompletableFuture` song song + `@CircuitBreaker` / `@Bulkhead` để nếu 1 service thống kê phụ chậm/lỗi thì vẫn trả dashboard với `partialData = true` thay vì lỗi 500 toàn trang.
2. Trong `backend/mobile-bff` (`/api/mobile/**`):
   - Implement `MobileBffQueryUseCase` (`getEvOwnerHomeBootstrap` gom 1 lần gọi cho màn hình chính Mobile trên mạng 4G; `getCursorPayoutProvenanceFeed` phân trang theo cursor nhẹ pin).
   - Implement `MobileBffCommandUseCase` cấp Presigned S3 PUT URL chụp ảnh CCCD/cà-vẹt xe từ camera, đăng ký FCM/APNs push token sang `notification-service` (`MobilePushNotificationPort`), và gửi yêu cầu rút tiền kèm `Idempotency-Key`.
3. Chạy `cd backend && mvn test -pl web-bff,mobile-bff -am -B` xanh 100%.
```
