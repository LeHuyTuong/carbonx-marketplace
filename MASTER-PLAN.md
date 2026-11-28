# Master Plan — CarbonX Distributed Microservices Platform

Mỗi tầng có Quyết định (chọn gì), Lý do (vì sao), Luật (AI phải theo, viết dạng "luôn ..." hoặc "không bao giờ ...").
Tầng sau không được mâu thuẫn tầng trước. Muốn đổi một quyết định thì sửa file này trước, rồi mới sửa code.
Task nào đi ngược file này thì dừng lại và hỏi.
File này thay thế mọi lựa chọn monolith trong `docs/architecture/` (08, 16 ADR-001, 28, 30). Mục 11–14 và các dòng gắn `(bổ sung 28/11)` là quyết định mới thêm ngày 28/11/2026 để lấp chỗ thiếu của plan; đó là quyết định, chưa phải mô tả code đã có.

## 1. Nghiệp vụ
### Quyết định
- Hệ thống sàn giao dịch tín chỉ carbon xe điện gồm 4 vai trò (`EV_OWNER`, `COMPANY`, `CVA`, `ADMIN`) vận hành chuỗi MRV 2 cấp (CVA → Admin), phát hành lô tín chỉ bất biến theo phương pháp luận `VM0038`, tách phần nắm giữ `Credit Holdings` (bán 80 giữ 20 để dùng), mua bán trên sàn (phí sàn 5%), triệt tiêu tín chỉ và chia sẻ doanh thu về ví chủ xe điện kèm truy vết nguồn gốc `Payout Items`.
- Luồng mua tín chỉ bằng ví vận hành theo mô hình thanh toán tức thì 1 bước cho người dùng cuối, được điều phối ngầm qua **Order Saga** giữa `trading-service`, `registry-service` và `ledger-service` kèm giao dịch bù trừ (Compensating Transactions).
- Mục tiêu sản phẩm là kiến trúc Microservices hoàn chỉnh theo 6 trụ cột chuẩn Solution Architect, chạy thực tế trên Docker Compose và sẵn sàng triển khai Kubernetes (GKE / Cloud Run).
### Lý do
- Tách bạch ranh giới nghiệp vụ và điều phối bằng Saga đảm bảo tính nhất quán cuối cùng (Eventual Consistency) khi mỗi microservice sở hữu cơ sở dữ liệu độc lập.
### Luật
- Luôn giữ nguyên 4 vai trò và quy trình thẩm định 2 cấp (CVA trước, Admin sau) khi phát hành tín chỉ.
- Luôn ghi nhận tối thiểu một dòng `WalletTransaction` bất biến chứa số dư trước và sau cho mọi thay đổi số dư ví.
- Không bao giờ cho phép sửa ngược thông số của một `Credit Batch` sau khi đã phát hành.

## 2. Công nghệ
### Quyết định
- Core & Hạ tầng Spring Cloud (`2023.0.3` trên Java 17 + Spring Boot `3.3.3`): Maven Multi-Module **12 modules** gồm `common-kernel`, `config-server` (`:8888` + `Spring Cloud Bus` qua Kafka + `@RefreshScope`), `discovery-server` (`:8761` Netflix Eureka), `api-gateway` (`:8080` Spring Cloud Gateway WebFlux + `Spring Cloud LoadBalancer` + Swagger UI tập trung `springdoc-openapi-starter-webflux-ui`), **2 tầng BFF không trạng thái (`web-bff` `:8091` cho Web SPA, `mobile-bff` `:8092` cho Mobile App)**, cùng 6 domain microservices (`identity-service` `:8081`, `mrv-service` `:8082`, `registry-service` `:8083`, `trading-service` `:8084`, `ledger-service` `:8085`, `notification-service` `:8086`).
- Giao tiếp, Chịu lỗi & AI: Đồng bộ qua **Spring Cloud OpenFeign** bọc **Resilience4j** (Circuit Breaker, Retry, Rate Limiter, Bulkhead — thay thế hoàn toàn Hystrix/Ribbon); Bất đồng bộ qua **Apache Kafka (KRaft)**; Cache dùng **Redis**; Tích hợp AI bằng **Spring AI `ChatClient`** (`spring-ai-openai-spring-boot-starter`) cho CVA Suggest Score và AI Chat Widget; Push Notification di động qua **FCM / APNs**.
- Đóng gói & Điều phối Cloud: Multi-stage `Dockerfile` với base image bảo mật **Google Distroless (`gcr.io/distroless/java17-debian12:nonroot`)**, kèm bộ manifest **Kubernetes (`k8s/configmap.yaml`, `secret.yaml`, `deployment.yaml`, `service.yaml`)** sẵn sàng cho GKE / Cloud Run.
### Lý do
- Áp dụng đầy đủ 6 trụ cột công nghệ chuẩn Spring Cloud, BFF đa thiết bị, Kafka, Redis, Spring AI, Distroless và Kubernetes giải quyết trọn vẹn bài toán định tuyến, phát hiện dịch vụ, tối ưu băng thông Mobile/Web, cấu hình nóng, chịu lỗi và vận hành cloud-native.
### Luật
- Luôn đóng gói mọi microservice và BFF bằng multi-stage Dockerfile trên nền `gcr.io/distroless/java17-debian12:nonroot`.
- Không bao giờ dùng thư viện đã ngừng phát triển như Netflix Hystrix hoặc Ribbon; bắt buộc dùng `Resilience4j` và `Spring Cloud LoadBalancer`.

## 3. Design pattern
### Quyết định
- Pattern đã chọn: **BFF (Backend for Frontend)** + **Database-per-Service** (không cross-DB `JOIN`) + **Pragmatic DDD** + **Logical CQRS** (`*CommandUseCase`, `*QueryUseCase` & Read Projections từ Kafka) + **Saga Orchestration** (6 trạng thái chuẩn `PENDING → EXECUTING → COMPLETED | FAILED → COMPENSATING → COMPENSATED`, chọn thay thế 2PC/XA để không khóa CSDL phân tán) + **Transactional Outbox** + **Resilience4j** (Circuit Breaker `CLOSED/OPEN/HALF-OPEN`, Retry, Rate Limiter, Bulkhead + OpenFeign fallback) + Idempotency Key.
- Áp tới mức nào: Tầng **BFF** tách riêng `web-bff` (`/api/web/**` gom bảng điều khiển đa service giàu dữ liệu cho Admin/CVA/Company, đọc Read Projection báo cáo do các domain service giữ) và `mobile-bff` (`/api/mobile/**` nén payload JSON gọn nhẹ cho mạng 4G/5G, gộp 1 request màn hình chính Chủ xe điện, chuyển tiếp Presigned URL S3 do service sở hữu tệp ký và chuyển tiếp đăng ký FCM/APNs token sang `notification-service`); giao dịch liên service (Mua tín chỉ giữa `trading`, `registry`, `ledger`) dùng **Order Saga Orchestrator** tách khỏi logic nghiệp vụ chi tiết (lưu trạng thái 6 pha tại `order_saga_instances`, phát sự kiện qua `outbox_events` → Kafka, mọi bước bù trừ đảm bảo *Idempotent – Reversible – Reliable*); luồng trong nội bộ 1 service (`Wallet`, `CreditHolding`) dùng khoá bi quan `FOR UPDATE` + 1 DB transaction cục bộ; dữ liệu tham chiếu tĩnh đọc qua Redis cache (`StaticReferenceCachePort`).
- Khi sập: Service phụ chậm/lỗi thì Resilience4j ngắt mạch (`OPEN` Fail Fast) trả fallback và BFF trả `partialData = true`; bước sau trong Order Saga thất bại thì chuyển `FAILED → COMPENSATING`, chạy chuỗi bù trừ ngược chiều rồi chuyển `COMPENSATED` + phát `OrderCompensatedEvent` qua Outbox; LLM lỗi thì Circuit Breaker `aiScoring` mở mạch trả điểm fallback từ 8 luật `DQ1..DQ8`; bản ghi Outbox lỗi quá 5 lần chuyển `DEAD_LETTER` cảnh báo đội vận hành.
### Lý do
- Kết hợp BFF, Saga Orchestration (non-blocking, thay thế 2PC), Outbox, CQRS Read Projections và Resilience4j vừa triệt tiêu cross-DB join và over-fetching trên Mobile, vừa bảo toàn nhất quán cuối cùng (Eventual Consistency) và chống lỗi sập dây chuyền (Cascading Failure).
### Luật
- Luôn tách riêng interface `*CommandUseCase` và `*QueryUseCase` trong gói `application.port.in` của mỗi microservice và mỗi BFF.
- Không bao giờ cấp cơ sở dữ liệu riêng hay đặt luật nghiệp vụ lõi (tính tiền, phát hành tín chỉ, duyệt MRV) bên trong `web-bff` hoặc `mobile-bff`.
- Luôn quản lý vòng đời Saga qua 6 trạng thái (`PENDING`, `EXECUTING`, `COMPLETED`, `FAILED`, `COMPENSATING`, `COMPENSATED`), định nghĩa giao dịch bù trừ (*Idempotent, Reversible, Reliable*), không đưa logic tính toán chi tiết vào Orchestrator, và ghi nhận sự kiện kết quả qua `OutboxPort`.
- Luôn bật `spring.cloud.openfeign.circuitbreaker.enabled=true` và bọc `Resilience4j` kèm fallback cho mọi `@FeignClient`, lời gọi Spring AI và cổng thanh toán bên ngoài; tuyệt đối dùng Constructor Injection (cấm `@Autowired` field injection).
- Không bao giờ để hai microservice đọc/ghi chung một database (schema) hay thực hiện SQL `JOIN` chéo giữa các Bounded Context. Được dùng chung một MySQL server ở local/staging (như `docker-compose.yml` hiện tại) với điều kiện mỗi service một database `carbonx_*` và một DB user chỉ có quyền trên database của mình; production tách instance khi tải hoặc yêu cầu cô lập đòi hỏi (bổ sung 28/11).
- Luôn đặt Read Projection trong domain service tiêu thụ sự kiện; BFF chỉ gọi và gộp, không giữ bản chiếu (bổ sung 28/11).

## 4. UI
### Quyết định
- Phục vụ 2 kênh giao diện độc lập qua tầng BFF riêng: **Web SPA** (`frontend/`: React 19 + Vite 6 + Tailwind CSS v4 cho đầy đủ 4 vai trò Admin, CVA, Company, EV Owner — Sidebar phân nhóm, Topbar Breadcrumb, `FileDropzone`, `ConfirmDialog`, KPI kèm đơn vị, biểu đồ Recharts) và **Mobile App** (`mobile/`: **React Native + Expo + TypeScript** chuyên biệt cho Chủ xe điện theo cấu trúc Clean Architecture / Logical CQRS khớp 1-1 với `mobile-bff` — theo dõi số dư ví & nguồn gốc lô tín chỉ chia tiền, chụp ảnh giấy tờ KYC/cà-vẹt xe qua S3 Presigned URL, xác thực sinh trắc học khi rút tiền, nhận Push Notification FCM/APNs).
- Cập nhật màn hình `/marketplace` và `/app/company/orders` khớp luồng mua tức thì 1 bước (bỏ nút `Hoàn tất` / `Huỷ đơn` trên đơn thanh toán ví).
- Tài liệu và giao diện người dùng giữ nhất quán tiếng Việt.
- Realtime trên Web (bổ sung 28/11): `notification-service` mở `GET /api/v1/notifications/stream` (SSE, thay `NotificationController` SSE của `Market_carbon`), qua `api-gateway` với route tắt response timeout cho đường này và heartbeat 25 giây. Web lấy vé ngắn hạn (60 giây, dùng 1 lần) qua `POST /api/v1/notifications/stream-ticket` rồi mở stream bằng vé đó. Khi chạy nhiều instance, sự kiện được fan-out qua Redis Pub/Sub để tới đúng instance đang giữ kết nối. Mobile không dùng SSE mà nhận Push FCM/APNs.
### Lý do
- Nhu cầu trải nghiệm giữa màn hình quản trị/kiểm toán lớn trên Web (bảng dữ liệu rộng, xuất CSV/PDF) và ứng dụng di động Chủ xe ngoài đường (mạng 4G chập chờn, thao tác 1 chạm, camera, push notification) khác biệt hoàn toàn; dùng cùng hệ sinh thái React + TypeScript giữa `frontend/` và `mobile/` giúp đồng nhất hợp đồng kiểu dữ liệu với `web-bff` và `mobile-bff`.
### Luật
- Luôn dùng `ConfirmDialog` và `FileDropzone` dùng chung cho thao tác xác nhận phá huỷ và tải tệp trên Web.
- Luôn lưu JWT/Refresh Token di động trong Secure Storage (iOS Keychain / Android Keystore qua `SecureTokenVaultPort`), tuyệt đối không lưu plaintext ở AsyncStorage; và giữ ranh giới `domain/` + `application/` của `mobile/` thuần TypeScript, không import ngược `infrastructure/` hay UI framework.
- Không bao giờ bắt người dùng nhập tay mã ID nội bộ của cơ sở dữ liệu trên giao diện Web hay Mobile.
- Không bao giờ đặt access JWT vào query string của URL SSE; chỉ dùng vé stream ngắn hạn (bổ sung 28/11).

## 5. Dữ liệu và quy ước
### Quyết định
- Mô hình thực thể khái niệm hợp nhất bằng tiếng Anh được định nghĩa tại `docs/diagrams/conceptual-erd.drawio` với thực thể lõi `Credit Batches` (21 thực thể, 20 quan hệ, 0 chu trình) và tách thành 5 sơ đồ độc lập theo service tại `docs/diagrams/services/01..05`.
- Giải quyết triệt để 2 bài toán cốt lõi của sàn carbon: (1) `Credit Batches` tách thành `Credit Holdings` (tách phần sở hữu khi bán 80 / giữ 20 để triệt tiêu mà không sửa lô gốc) kèm sổ cái serial `Credit Movements`; (2) `Profit Distributions` sinh `Payout Items` lưu rõ `creditBatchId`, `batchCode`, `serialRange` và `sourceReportId` để chủ xe điện truy vết chính xác từng đồng tiền nhận vào `Wallets`.
- Quản lý lược đồ cơ sở dữ liệu hoàn toàn bằng **Flyway** (`src/main/resources/db/migration/V1..Vn`, bao gồm `V2__saga_orchestration_state.sql` cho máy trạng thái 6 pha của Order Saga trong `trading-service`), đặt `spring.jpa.hibernate.ddl-auto=validate` ở môi trường production; tên thực thể, bảng và code thống nhất tiếng Anh.
- Mỗi Bounded Context sở hữu nhóm bảng riêng (**Database-per-Service**), không có khoá ngoại vật lý hay SQL `JOIN` chéo; dữ liệu báo cáo/hiển thị liên service sử dụng snapshot trong `market_listings`/`payout_items`, bảng chiếu CQRS đồng bộ từ Kafka Data Events, hoặc bộ nhớ đệm tham chiếu tĩnh trên Redis (`StaticReferenceCachePort`).
### Lý do
- Tách quyền sở hữu bảng và bỏ khoá ngoại chéo giúp việc chuyển dữ liệu từ `Market_carbon` sang từng database `carbonx_*` (lộ trình ở mục 11) diễn ra không cần viết lại truy vấn.
### Luật
- Luôn tạo file migration Flyway mới theo thứ tự tăng dần cho mọi thay đổi schema cơ sở dữ liệu.
- Không bao giờ chỉnh sửa lại file migration Flyway đã được áp dụng lên nhánh `main`.
- Không bao giờ tạo khoá ngoại vật lý hoặc câu lệnh `JOIN` SQL xuyên qua ranh giới hai Bounded Context khác nhau.

## 6. Kiểm thử
### Quyết định
- Áp dụng 3 lớp kiểm thử tự động: `ArchUnit` (6 luật kiểm tra ranh giới DDD, CQRS và Clean Code: cấm gọi chéo module, cấm Controller gọi thẳng Repository, cấm trả JPA Entity ra API, cấm `@Autowired` field injection, cấm `System.out`/`printStackTrace`), `JUnit 5 + Testcontainers MySQL 8` (kiểm thử bất biến tiền, khoá `FOR UPDATE`, Outbox, Circuit Breaker fallback và phân quyền `403`), và `Vitest` cho Frontend.
- Mọi bản vá bảo mật hoặc luồng tiền đều phải đi kèm test hồi quy (regression test).
- Thêm 2 lớp liên service (bổ sung 28/11): **Spring Cloud Contract** cho mọi hợp đồng `@FeignClient` (service cung cấp sinh stub, service gọi chạy test với stub) và cho payload sự kiện Kafka ở mục 12; **Saga E2E** bằng Testcontainers (MySQL + Kafka) chạy `trading` + `registry` + `ledger`, gồm 1 ca thành công và 1 ca lỗi cho mỗi bước `RESERVE_LISTING`, `SETTLE_PAYMENT`, `TRANSFER_CREDITS`, `FINALIZE_ORDER`, khẳng định trạng thái cuối `COMPENSATED` và số dư ví, số tín chỉ trở về như trước.
### Lý do
- Kết hợp ArchUnit và Testcontainers trên MySQL thật bắt được cả vi phạm kiến trúc lẫn lỗi tranh chấp đồng thời mà Mockito đơn thuần bỏ sót.
- Test trong từng service không bắt được việc hai service lệch hợp đồng; contract test và Saga E2E lấp đúng khoảng đó.
### Luật
- Luôn giữ `ServiceArchitectureTest` (áp `CleanCodeArchRules` của `common-kernel`) xanh ở mọi module bị chạm trong commit.
- Luôn cập nhật contract và chạy verify ở cả hai phía khi đổi request/response Feign hoặc payload sự kiện (bổ sung 28/11).
- Luôn kiểm chứng bằng MySQL thật (Testcontainers) cho các thao tác chốt đơn đồng thời và chia sẻ doanh thu.
- Không bao giờ bỏ qua hoặc tắt một bài test đang fail để hợp thức hoá bản build.

## 7. Bảo mật
### Quyết định
- Kết hợp **Spring Security + OAuth 2.0 / OpenID Connect (Google OAuth2 Client)** và **JWT stateless** truyền danh tính & quyền (`ROLE_EV_OWNER`, `ROLE_COMPANY`, `ROLE_CVA`, `ROLE_ADMIN`) xuyên qua `api-gateway` xuống 6 microservices, kiểm tra `@PreAuthorize` ngay tại từng service.
- Fail-closed toàn bộ secret qua biến môi trường / Kubernetes `Secret` (hỗ trợ mở rộng sang HashiCorp Vault / Secret Manager) và thiết kế sẵn cấu hình mTLS cho kênh nội bộ service-to-service; ẩn khối đăng nhập mẫu khỏi bundle production; bật security headers trên Nginx.
- Luồng token (bổ sung 28/11):
  - `identity-service` là nơi duy nhất cấp token: xử lý đăng nhập Google OAuth2 và mật khẩu, cấp access JWT ngắn hạn cùng refresh token xoay vòng. Code hiện ký HS256 bằng secret chung (`JwtTokenIssuer`); đích là chuyển sang **RS256** và công bố khoá công khai tại `/.well-known/jwks.json` để không service nào phải giữ secret ký.
  - `api-gateway` kiểm chữ ký, hạn và issuer `carbonx-identity` rồi chuyển nguyên header `Authorization` xuống BFF và service. Mỗi service vẫn tự kiểm lại bằng JWKS (Spring `oauth2-resource-server`) trước `@PreAuthorize`.
  - BFF chuyển tiếp JWT của người dùng qua Feign `RequestInterceptor`.
  - Lời gọi hệ thống không có người dùng (bước Saga, consumer Kafka, job định kỳ) dùng service token `client_credentials` do `identity-service` cấp, mang `ROLE_SERVICE` và `aud` là service đích.
  - Endpoint nội bộ đặt dưới `/internal/**` (như `InternalIdentityController`); gateway chặn mọi đường `/internal/**` từ bên ngoài.
### Lý do
- Sàn giao dịch tài chính và tín chỉ carbon có rủi ro cao về leo thang đặc quyền, IDOR và rò rỉ chứng từ nếu chỉ dựa vào ẩn nút ở Frontend.
### Luật
- Không bao giờ đặt giá trị secret mặc định hoặc tài khoản demo cứng trong đường dẫn build production.
- Không bao giờ trả trực tiếp JPA Entity chứa thông tin nhạy cảm ra API response mà thiếu DTO tường minh.
- Luôn kiểm tra cả quyền theo vai trò (`@PreAuthorize`) lẫn quyền sở hữu tài nguyên (ownership) ở server-side.
- Không bao giờ để service tin header danh tính tự chế (`X-User-Id`, `X-Role`…) thay cho JWT đã kiểm chữ ký (bổ sung 28/11).
- Không bao giờ mở route `/internal/**` qua `api-gateway` (bổ sung 28/11).

## 8. Quan sát hệ thống
### Quyết định
- Triển khai đầy đủ stack observability trong `docker-compose.yml`: **Prometheus** (thu thập metrics qua Spring Actuator `/actuator/prometheus`), **Grafana** (dashboard theo dõi độ trễ API, tỷ lệ lỗi 4xx/5xx, hàng đợi `outbox_events` và `profitPool`), và **OpenTelemetry + Jaeger** (distributed tracing xuyên suốt REST request, Feign, Outbox relay và Kafka; `traceId` đi trong header của bản tin Kafka).
- Toàn bộ log backend xuất chuẩn JSON qua SLF4J kèm `traceId` / `X-Request-Trace`.
### Lý do
- Có sẵn Prometheus, Grafana và OpenTelemetry/Jaeger giúp chứng minh khả năng vận hành thực tế của hệ thống phân tán và dễ dàng truy vết giao dịch lỗi trong buổi demo phỏng vấn.
### Luật
- Luôn đính kèm `traceId` vào log và header `X-Request-Trace` khi xử lý request và phát Outbox event.
- Không bao giờ ghi mật khẩu, JWT token hoặc API key vào log hay span attributes của OpenTelemetry.

## 9. Ranh giới domain và chia việc
### Quyết định
- Tách thành **2 tầng BFF không trạng thái (`web-bff` `:8091`, `mobile-bff` `:8092`)** đứng trước **6 Microservices nghiệp vụ độc lập (Database-per-Service)**: `identity-service` (`carbonx_identity`), `mrv-service` (`carbonx_mrv`), `registry-service` (`carbonx_registry` + Redis cache), `trading-service` (`carbonx_trading`), `ledger-service` (`carbonx_ledger`), `notification-service` (`carbonx_notification`), cùng thư viện hợp đồng nhẹ `common-kernel` (`Money`, `SerialRange`, `DomainEvent`, `OutboxPort`, `CleanCodeArchRules`).
- Mỗi microservice tuân thủ đúng 4 gói DDD: `api` (REST Controllers + Feign Contracts), `application` (`port.in` CQRS `*CommandUseCase` / `*QueryUseCase` + `port.out`), `domain` (Aggregates + Value Objects), `infrastructure` (JPA, Kafka, Redis, External Adapters); riêng `web-bff` và `mobile-bff` chỉ giữ `api`, `application` (`port.in` / `port.out`) và `infrastructure.client` (`@FeignClient`), không có DB riêng.
- Giao tiếp từ BFF xuống domain services và giao tiếp đồng bộ liên service đi qua `@FeignClient` + `Spring Cloud LoadBalancer` + `Resilience4j`; giao tiếp bất đồng bộ đi qua `OutboxPort` → **Apache Kafka**.
- Quyền sở hữu dữ liệu (bổ sung 28/11; bảng lấy từ Flyway hiện có, phần "cần thêm" là quyết định mới):

| Service | Database | Bảng đang có | Cần thêm / sở hữu thêm |
|---|---|---|---|
| `identity-service` | `carbonx_identity` | `users`, `companies`, `kyc_profiles`, `vehicles`, `outbox_events` | `refresh_tokens`; tệp KYC và cà-vẹt trên S3 prefix `identity/` |
| `mrv-service` | `carbonx_mrv` | `projects`, `project_applications`, `emission_reports`, `fraud_analyses`, `outbox_events` | `processed_events`; tệp minh chứng S3 prefix `mrv/`; CVA Suggest Score và AI Chat Widget (`/api/v1/ai/**`) |
| `registry-service` | `carbonx_registry` | `credit_batches`, `credit_holdings`, `credit_movements`, `retirement_certificates`, `outbox_events` | `processed_events`; PDF chứng nhận triệt tiêu S3 prefix `registry/` |
| `trading-service` | `carbonx_trading` | `market_listings`, `listing_reservations`, `trade_orders`, `order_saga_instances`, `order_saga_transition_logs`, `outbox_events` | `processed_events`; bản chiếu lô tín chỉ để hiển thị sàn |
| `ledger-service` | `carbonx_ledger` | `wallets`, `wallet_transactions`, `bank_accounts`, `withdrawals`, `profit_distributions`, `payout_items`, `outbox_events` | `payment_orders`, `processed_events`; công thức chia lợi nhuận (`/api/v1/companies/payout-formula`) |
| `notification-service` | `carbonx_notification` | `notifications` | `device_tokens` (FCM/APNs), `processed_events`; cần vẽ thêm ERD `06` |

- S3 dùng một bucket chia prefix theo service; chỉ service sở hữu prefix được ký Presigned URL (bổ sung 28/11).
### Lý do
- Tách tầng BFF theo thiết bị (Web vs Mobile) khỏi 6 domain microservices giúp đội Mobile/Frontend chủ động thay đổi cấu trúc màn hình mà không đụng vào hợp đồng domain hay CSDL của các service lõi.
### Luật
- Không bao giờ cho phép một microservice hoặc BFF phụ thuộc Maven trực tiếp vào microservice khác (chỉ được dùng `common-kernel` và hợp đồng REST/Kafka).
- Không bao giờ đưa logic nghiệp vụ đặc thù của một service vào `common-kernel` hay vào tầng BFF.
- Luôn cập nhật bảng quyền sở hữu dữ liệu ở trên trước khi tạo bảng mới hoặc chuyển bảng giữa các service (bổ sung 28/11).

## 10. Vận hành sự cố
### Quyết định
- Rollback: Quay lại phiên bản ứng dụng trước bằng Docker image tag ổn định gần nhất (`docker compose up -d` với tag cũ); riêng cơ sở dữ liệu áp dụng chiến lược **Flyway forward-fix** (viết migration vá tiến, không drop bảng hay hạ version phá dữ liệu), quyền thực hiện thuộc Tech Lead / Project Owner.
- Hotfix: Chỉ áp dụng nhánh `hotfix/*` tách từ `main` khi xảy ra lỗi mất tiền, sai lệch số dư ví, lỗ hổng bảo mật Critical/High hoặc gián đoạn hoàn toàn luồng giao dịch; bắt buộc vượt qua toàn bộ bộ test tự động trước khi merge lại `main`.
- Báo cho: Thông báo trực tiếp cho Tech Lead / Project Owner trong vòng 15 phút khi phát hiện sai lệch đối soát `WalletTransaction`, lỗi chữ ký thanh toán hoặc hàng đợi `outbox_events` có bản ghi `DEAD_LETTER` lớn hơn 0.
### Lý do
- Hệ thống tài chính yêu cầu quy trình xử lý sự cố ưu tiên bảo toàn sổ cái và khả năng kiểm toán thay vì can thiệp tay trực tiếp vào cơ sở dữ liệu.
### Luật
- Luôn kiểm tra đối soát tổng số dư ví với sổ cái `WalletTransaction` sau mỗi sự cố liên quan đến thanh toán hoặc chia sẻ doanh thu.
- Không bao giờ chạy lệnh `UPDATE` hoặc `DELETE` thủ công trực tiếp trên bảng `wallet_transactions` ở môi trường production.

## 11. Lộ trình rời monolith `Market_carbon` (bổ sung 28/11)
### Quyết định
- Dùng **Strangler Fig qua `api-gateway`**. Monolith được đặt sau gateway như một route dự phòng (`/api/**` → `Market_carbon`). Mỗi khi một service xong, chỉ cần thêm route cụ thể của service đó lên trước; route cụ thể thắng route dự phòng. Monolith chỉ bị tắt khi không còn đường nào trỏ về nó.
- Thứ tự các pha:
  - **Pha 0 – Nền**: đưa `Market_carbon` vào `docker-compose.yml` sau gateway. Cho Eureka, Config, Kafka, observability chạy. Web và Mobile chỉ gọi qua gateway.
  - **Pha 1 – `identity-service`**: đã có code. Cắt `/api/v1/auth/**`, `/kyc/**`, `/vehicles/**`, `/users/**`; bật JWT RS256 + JWKS. Monolith chuyển sang kiểm token do identity cấp.
  - **Pha 2 – `mrv-service`**: projects, applications, reports, AI scoring, chat (`/api/v1/ai/**`).
  - **Pha 3 – `registry` + `trading` + `ledger`**: cắt cùng lúc vì Order Saga và luồng tiền nối ba service. Gồm cả thanh toán (mục 13) và chia lợi nhuận.
  - **Pha 4 – `notification-service`**: SSE, push FCM/APNs, consumer cho toàn bộ sự kiện ở mục 12.
  - **Pha 5 – Tắt monolith**: xoá route dự phòng, lưu trữ (archive) database monolith ở chế độ chỉ đọc.
- Chuyển dữ liệu: mỗi pha có một job ETL một lần từ database monolith sang `carbonx_*` của service đó.
  - **Giữ nguyên ID (`Long`)**, để tham chiếu chéo giữa các service (như `companyId`, `vehicleId`) vẫn đúng mà không cần bảng ánh xạ.
  - Chạy thử trên bản sao production trước, rồi chạy thật trong một cửa sổ khoá ghi ngắn.
- Chạy song song: trong một pha, mỗi bảng chỉ có **một bên được ghi**. Trước khi cắt, monolith ghi. Sau khi cắt, service mới ghi; monolith chỉ đọc bản sao do consumer Kafka cập nhật hoặc gọi API của service mới.
- Điều kiện xong một pha:
  - số dòng và tổng tiền đối soát khớp 100%: tổng số dư ví bằng tổng `wallet_transactions`, và tổng số tín chỉ của holdings bằng tổng số tín chỉ của batches;
  - hành trình E2E của các vai liên quan chạy xanh qua gateway;
  - 7 ngày không phải rollback route.
### Lý do
- Code thật hôm nay vẫn là monolith (`Market_carbon`, ~379 file Java) cùng khung microservices gần rỗng. Không có lộ trình thì plan chỉ mô tả đích mà không nói cách đi tới, và rất dễ rơi vào hai bên cùng ghi dữ liệu.
### Luật
- Không bao giờ ghi kép (dual-write) cùng một bảng từ cả monolith lẫn service mới.
- Luôn giữ route dự phòng về monolith cho tới khi pha cuối đạt điều kiện xong; rollback một pha chỉ cần gỡ route cụ thể khỏi gateway.
- Không bao giờ bắt đầu pha sau khi pha trước chưa đạt đủ điều kiện xong.

## 12. Danh mục sự kiện Kafka (bổ sung 28/11)
### Quyết định
- Sự kiện tích hợp chuẩn nằm ở `common-kernel` `IntegrationEvents`. Producer lấy theo code; consumer là quyết định mới:

| `eventType` | Producer | Consumer | Mục đích |
|---|---|---|---|
| `identity.vehicle.registered` | `identity-service` | `mrv-service`, `notification-service` | Gắn xe vào dự án đo phát thải; báo chủ xe |
| `mrv.emission-report.approved` | `mrv-service` | `registry-service`, `notification-service` | Phát hành `Credit Batch` từ báo cáo đã duyệt 2 cấp |
| `registry.credit-batch.issued` | `registry-service` | `trading-service`, `ledger-service`, `notification-service` | Bản chiếu lô để niêm yết; điều kiện lập chia lợi nhuận |
| `trading.order.settled` | `trading-service` | `notification-service`, `registry-service` (bản chiếu thống kê) | Báo mua thành công; số liệu báo cáo |
| `trading.order.compensated` | `trading-service` | `notification-service` | Báo đơn thất bại và đã hoàn |
| `ledger.profit.distributed` | `ledger-service` | `notification-service` | Báo chủ xe nhận tiền kèm truy vết lô |

- Lệnh của Order Saga (`RESERVE_LISTING`, `SETTLE_PAYMENT`, `TRANSFER_CREDITS`, `FINALIZE_ORDER`) gọi **đồng bộ qua Feign kèm `idempotencyKey`** (`OrderSagaRemotePorts`), không đi Kafka. Chỉ kết quả cuối (`settled` / `compensated`) mới phát qua Outbox.
- Topic đặt tên `carbonx.<service>.<aggregate>`, ví dụ `carbonx.trading.trade-order`. Key là `aggregateId` để giữ thứ tự theo aggregate.
- Envelope gồm `eventId` (UUID), `eventType`, `version`, `occurredAt`, `traceId` và `payload`.
- Version:
  - Trong cùng một version chỉ được thêm field tuỳ chọn.
  - Thay đổi phá vỡ thì tạo `eventType` mới có hậu tố `.v2`, chạy song song cho tới khi mọi consumer chuyển xong.
- Giao nhận kiểu at-least-once. Consumer chống trùng bằng bảng `processed_events(event_id PK)`, ghi cùng transaction với tác dụng nghiệp vụ.
- Lỗi xử lý thì retry 3 lần có backoff rồi đẩy sang `<topic>.DLT` và cảnh báo như `DEAD_LETTER` của Outbox (mục 10).
- Topic `springCloudBus` dành riêng cho Config Bus.
### Lý do
- Plan đã chọn Kafka + Outbox nhưng chưa nói sự kiện nào đi từ đâu tới đâu. Không có danh mục thì không chia việc được, và không viết được contract test.
### Luật
- Luôn thêm dòng vào bảng trên trước khi tạo sự kiện mới hoặc thêm consumer mới.
- Luôn phát sự kiện qua `OutboxPort` trong cùng transaction với thay đổi nghiệp vụ; không bao giờ gọi `KafkaTemplate` trực tiếp từ use case.
- Không bao giờ xử lý một sự kiện mà không kiểm `processed_events` trước.
- Không bao giờ dùng Kafka cho bước Saga cần phản hồi ngay; bước Saga đi Feign kèm `idempotencyKey`.

## 13. Thanh toán (bổ sung 28/11)
### Quyết định
- `ledger-service` sở hữu toàn bộ thanh toán, chuyển từ `Market_carbon`: `PaymentGatewayRegistry` với **Stripe**, **PayPal**, **VNPay**. Route gateway là `/api/v1/payment/**`, `/wallet/**`, `/withdrawal/**`.
- Nạp tiền:
  1. Người dùng tạo `payment_order` trạng thái `PENDING`, có `idempotencyKey`, số tiền, loại tiền và nhà cung cấp.
  2. Người dùng được chuyển sang trang nhà cung cấp.
  3. Nhà cung cấp gọi webhook công khai `POST /api/v1/payment/webhook/{provider}` qua gateway; route này không cần JWT nhưng `ledger-service` phải kiểm chữ ký.
  4. `ledger-service` kiểm chữ ký, số tiền, loại tiền và trạng thái đơn, rồi cộng ví cùng một `WalletTransaction` trong **một transaction cục bộ**.
  5. Chống trùng bằng unique `(provider, providerTransactionId)`.
  6. Return URL trên trình duyệt chỉ để hiển thị, không cộng tiền.
- Nếu đơn vị ví khác đơn vị thanh toán thì lưu tỷ giá trên `payment_order` tại thời điểm nạp.
- Rút tiền:
  1. Chủ xe hoặc công ty chọn `bank_account` đã xác minh và tạo `withdrawal` (`PENDING`), có xác thực sinh trắc học trên mobile. Ví bị trừ ngay vào khoản giữ (hold) bằng `WalletTransaction`.
  2. Admin duyệt thì chuyển `COMPLETED` sau khi chi. Nếu từ chối thì chuyển `REJECTED` và hoàn bằng một `WalletTransaction` đảo chiều.
- Mỗi ngày chạy job đối soát `payment_orders` với báo cáo quyết toán của nhà cung cấp; lệch thì báo theo mục 10.
### Lý do
- Monolith đã có 3 cổng thanh toán và rút tiền nhưng plan chưa nói ai sở hữu, webhook đi đường nào và chống trùng ra sao. Đây là chỗ mất tiền dễ nhất khi tách service.
### Luật
- Không bao giờ cộng tiền vào ví từ return URL hay từ dữ liệu client gửi lên; chỉ cộng từ webhook đã kiểm chữ ký hoặc từ API xác minh phía server.
- Luôn đi kèm `idempotencyKey` hoặc `providerTransactionId` unique cho mọi thao tác làm đổi số dư.
- Không bao giờ ghi secret webhook hay khoá API thanh toán vào log, repo hoặc config-server ở dạng plaintext.

## 14. CI/CD và môi trường (bổ sung 28/11)
### Quyết định
- **GitHub Actions**: mỗi module một workflow, có path filter (`backend/<module>/**` và `backend/common-kernel/**`). Các bước:
  1. build;
  2. unit test + `ServiceArchitectureTest`;
  3. Testcontainers integration;
  4. contract verify;
  5. dựng image Distroless, tag bằng git SHA, đẩy lên GHCR.
- Saga E2E chạy khi chạm `trading`, `registry`, `ledger` hoặc `common-kernel`.
- Có 3 môi trường:
  - `local`: Docker Compose;
  - `staging`: K8s namespace `carbonx-staging`;
  - `prod`: K8s namespace `carbonx-prod`.
- Cấu hình theo profile trên `config-server`; secret nằm trong K8s `Secret`.
- Lên prod bằng **chính image tag đã qua staging**, không build lại.
- Flyway tự chạy khi service khởi động, mỗi service trên database của mình.
- Ở staging và prod, topic Kafka được tạo bằng manifest hoặc script; tắt `auto.create.topics`.
- `k8s/deployment.yaml` hiện mới có gateway, 2 BFF và `trading`. Phải bổ sung Deployment + Service + readiness/liveness probe (`/actuator/health/*`) cho mọi module chạy được.
### Lý do
- Plan có Dockerfile và manifest K8s nhưng chưa có đường đưa code từ commit tới môi trường. Với 12 module, nếu không có path filter thì mỗi commit sẽ build lại tất cả.
### Luật
- Không bao giờ deploy image chưa qua đủ các bước của pipeline cho module đó.
- Luôn dùng tag bất biến (git SHA) cho image; không bao giờ deploy tag `latest` lên staging hoặc prod.
- Luôn có manifest K8s và probe cho module trước khi coi module đó sẵn sàng triển khai.
