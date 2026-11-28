# Master Plan — CarbonX Distributed Microservices Platform

Mỗi tầng có Quyết định (chọn gì), Lý do (vì sao), Luật (AI phải theo, viết dạng "luôn ..." hoặc "không bao giờ ...").
Tầng sau không được mâu thuẫn tầng trước. Muốn đổi một quyết định thì sửa file này trước, rồi mới sửa code.
Task nào đi ngược file này thì dừng lại và hỏi.

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
- Áp tới mức nào: Tầng **BFF** tách riêng `web-bff` (`/api/web/**` gom bảng điều khiển đa service giàu dữ liệu cho Admin/CVA/Company kèm Read Projection báo cáo) và `mobile-bff` (`/api/mobile/**` nén payload JSON gọn nhẹ cho mạng 4G/5G, gộp 1 request màn hình chính Chủ xe điện, cấp Presigned URL chụp ảnh KYC/xe lên S3 và đăng ký FCM/APNs token); giao dịch liên service (Mua tín chỉ giữa `trading`, `registry`, `ledger`) dùng **Order Saga Orchestrator** tách khỏi logic nghiệp vụ chi tiết (lưu trạng thái 6 pha tại `order_saga_instances`, phát sự kiện qua `outbox_events` → Kafka, mọi bước bù trừ đảm bảo *Idempotent – Reversible – Reliable*); luồng trong nội bộ 1 service (`Wallet`, `CreditHolding`) dùng khoá bi quan `FOR UPDATE` + 1 DB transaction cục bộ; dữ liệu tham chiếu tĩnh đọc qua Redis cache (`StaticReferenceCachePort`).
- Khi sập: Service phụ chậm/lỗi thì Resilience4j ngắt mạch (`OPEN` Fail Fast) trả fallback và BFF trả `partialData = true`; bước sau trong Order Saga thất bại thì chuyển `FAILED → COMPENSATING`, chạy chuỗi bù trừ ngược chiều rồi chuyển `COMPENSATED` + phát `OrderCompensatedEvent` qua Outbox; LLM lỗi thì Circuit Breaker `aiScoring` mở mạch trả điểm fallback từ 8 luật `DQ1..DQ8`; bản ghi Outbox lỗi quá 5 lần chuyển `DEAD_LETTER` cảnh báo đội vận hành.
### Lý do
- Kết hợp BFF, Saga Orchestration (non-blocking, thay thế 2PC), Outbox, CQRS Read Projections và Resilience4j vừa triệt tiêu cross-DB join và over-fetching trên Mobile, vừa bảo toàn nhất quán cuối cùng (Eventual Consistency) và chống lỗi sập dây chuyền (Cascading Failure).
### Luật
- Luôn tách riêng interface `*CommandUseCase` và `*QueryUseCase` trong gói `application.port.in` của mỗi microservice và mỗi BFF.
- Không bao giờ cấp cơ sở dữ liệu riêng hay đặt luật nghiệp vụ lõi (tính tiền, phát hành tín chỉ, duyệt MRV) bên trong `web-bff` hoặc `mobile-bff`.
- Luôn quản lý vòng đời Saga qua 6 trạng thái (`PENDING`, `EXECUTING`, `COMPLETED`, `FAILED`, `COMPENSATING`, `COMPENSATED`), định nghĩa giao dịch bù trừ (*Idempotent, Reversible, Reliable*), không đưa logic tính toán chi tiết vào Orchestrator, và ghi nhận sự kiện kết quả qua `OutboxPort`.
- Luôn bật `spring.cloud.openfeign.circuitbreaker.enabled=true` và bọc `Resilience4j` kèm fallback cho mọi `@FeignClient`, lời gọi Spring AI và cổng thanh toán bên ngoài; tuyệt đối dùng Constructor Injection (cấm `@Autowired` field injection).
- Không bao giờ để hai microservice đọc/ghi chung một CSDL vật lý hay thực hiện SQL `JOIN` chéo giữa các Bounded Context.

## 4. UI
### Quyết định
- Phục vụ 2 kênh giao diện độc lập qua tầng BFF riêng: **Web SPA** (`frontend/`: React 19 + Vite 6 + Tailwind CSS v4 cho đầy đủ 4 vai trò Admin, CVA, Company, EV Owner — Sidebar phân nhóm, Topbar Breadcrumb, `FileDropzone`, `ConfirmDialog`, KPI kèm đơn vị, biểu đồ Recharts) và **Mobile App** (`mobile/`: **React Native + Expo + TypeScript** chuyên biệt cho Chủ xe điện theo cấu trúc Clean Architecture / Logical CQRS khớp 1-1 với `mobile-bff` — theo dõi số dư ví & nguồn gốc lô tín chỉ chia tiền, chụp ảnh giấy tờ KYC/cà-vẹt xe qua S3 Presigned URL, xác thực sinh trắc học khi rút tiền, nhận Push Notification FCM/APNs).
- Cập nhật màn hình `/marketplace` và `/app/company/orders` khớp luồng mua tức thì 1 bước (bỏ nút `Hoàn tất` / `Huỷ đơn` trên đơn thanh toán ví).
- Tài liệu và giao diện người dùng giữ nhất quán tiếng Việt.
### Lý do
- Nhu cầu trải nghiệm giữa màn hình quản trị/kiểm toán lớn trên Web (bảng dữ liệu rộng, xuất CSV/PDF) và ứng dụng di động Chủ xe ngoài đường (mạng 4G chập chờn, thao tác 1 chạm, camera, push notification) khác biệt hoàn toàn; dùng cùng hệ sinh thái React + TypeScript giữa `frontend/` và `mobile/` giúp đồng nhất hợp đồng kiểu dữ liệu với `web-bff` và `mobile-bff`.
### Luật
- Luôn dùng `ConfirmDialog` và `FileDropzone` dùng chung cho thao tác xác nhận phá huỷ và tải tệp trên Web.
- Luôn lưu JWT/Refresh Token di động trong Secure Storage (iOS Keychain / Android Keystore qua `SecureTokenVaultPort`), tuyệt đối không lưu plaintext ở AsyncStorage; và giữ ranh giới `domain/` + `application/` của `mobile/` thuần TypeScript, không import ngược `infrastructure/` hay UI framework.
- Không bao giờ bắt người dùng nhập tay mã ID nội bộ của cơ sở dữ liệu trên giao diện Web hay Mobile.

## 5. Dữ liệu và quy ước
### Quyết định
- Mô hình thực thể khái niệm hợp nhất bằng tiếng Anh được định nghĩa tại `docs/diagrams/conceptual-erd.drawio` với thực thể lõi `Credit Batches` (21 thực thể, 20 quan hệ, 0 chu trình) và tách thành 5 sơ đồ độc lập theo service tại `docs/diagrams/services/01..05`.
- Giải quyết triệt để 2 bài toán cốt lõi của sàn carbon: (1) `Credit Batches` tách thành `Credit Holdings` (tách phần sở hữu khi bán 80 / giữ 20 để triệt tiêu mà không sửa lô gốc) kèm sổ cái serial `Credit Movements`; (2) `Profit Distributions` sinh `Payout Items` lưu rõ `creditBatchId`, `batchCode`, `serialRange` và `sourceReportId` để chủ xe điện truy vết chính xác từng đồng tiền nhận vào `Wallets`.
- Quản lý lược đồ cơ sở dữ liệu hoàn toàn bằng **Flyway** (`src/main/resources/db/migration/V1..Vn`, bao gồm `V2__saga_orchestration_state.sql` cho máy trạng thái 6 pha của Order Saga trong `trading-service`), đặt `spring.jpa.hibernate.ddl-auto=validate` ở môi trường production; tên thực thể, bảng và code thống nhất tiếng Anh.
- Mỗi Bounded Context sở hữu nhóm bảng riêng (**Database-per-Service**), không có khoá ngoại vật lý hay SQL `JOIN` chéo; dữ liệu báo cáo/hiển thị liên service sử dụng snapshot trong `market_listings`/`payout_items`, bảng chiếu CQRS đồng bộ từ Kafka Data Events, hoặc bộ nhớ đệm tham chiếu tĩnh trên Redis (`StaticReferenceCachePort`).
### Lý do
- Tách quyền sở hữu bảng và bỏ khoá ngoại chéo giúp việc bóc tách cơ sở dữ liệu theo từng microservice ở Pha 2 diễn ra không cần viết lại truy vấn.
### Luật
- Luôn tạo file migration Flyway mới theo thứ tự tăng dần cho mọi thay đổi schema cơ sở dữ liệu.
- Không bao giờ chỉnh sửa lại file migration Flyway đã được áp dụng lên nhánh `main`.
- Không bao giờ tạo khoá ngoại vật lý hoặc câu lệnh `JOIN` SQL xuyên qua ranh giới hai Bounded Context khác nhau.

## 6. Kiểm thử
### Quyết định
- Áp dụng 3 lớp kiểm thử tự động: `ArchUnit` (6 luật kiểm tra ranh giới DDD, CQRS và Clean Code: cấm gọi chéo module, cấm Controller gọi thẳng Repository, cấm trả JPA Entity ra API, cấm `@Autowired` field injection, cấm `System.out`/`printStackTrace`), `JUnit 5 + Testcontainers MySQL 8` (kiểm thử bất biến tiền, khoá `FOR UPDATE`, Outbox, Circuit Breaker fallback và phân quyền `403`), và `Vitest` cho Frontend.
- Mọi bản vá bảo mật hoặc luồng tiền đều phải đi kèm test hồi quy (regression test).
### Lý do
- Kết hợp ArchUnit và Testcontainers trên MySQL thật bắt được cả vi phạm kiến trúc lẫn lỗi tranh chấp đồng thời mà Mockito đơn thuần bỏ sót.
### Luật
- Luôn giữ `ModuleBoundaryArchTest` xanh trong mọi commit chạm vào mã nguồn backend.
- Luôn kiểm chứng bằng MySQL thật (Testcontainers) cho các thao tác chốt đơn đồng thời và chia sẻ doanh thu.
- Không bao giờ bỏ qua hoặc tắt một bài test đang fail để hợp thức hoá bản build.

## 7. Bảo mật
### Quyết định
- Kết hợp **Spring Security + OAuth 2.0 / OpenID Connect (Google OAuth2 Client)** và **JWT stateless** truyền danh tính & quyền (`ROLE_EV_OWNER`, `ROLE_COMPANY`, `ROLE_CVA`, `ROLE_ADMIN`) xuyên qua `api-gateway` xuống 6 microservices, kiểm tra `@PreAuthorize` ngay tại từng service.
- Fail-closed toàn bộ secret qua biến môi trường / Kubernetes `Secret` (hỗ trợ mở rộng sang HashiCorp Vault / Secret Manager) và thiết kế sẵn cấu hình mTLS cho kênh nội bộ service-to-service; ẩn khối đăng nhập mẫu khỏi bundle production; bật security headers trên Nginx.
### Lý do
- Sàn giao dịch tài chính và tín chỉ carbon có rủi ro cao về leo thang đặc quyền, IDOR và rò rỉ chứng từ nếu chỉ dựa vào ẩn nút ở Frontend.
### Luật
- Không bao giờ đặt giá trị secret mặc định hoặc tài khoản demo cứng trong đường dẫn build production.
- Không bao giờ trả trực tiếp JPA Entity chứa thông tin nhạy cảm ra API response mà thiếu DTO tường minh.
- Luôn kiểm tra cả quyền theo vai trò (`@PreAuthorize`) lẫn quyền sở hữu tài nguyên (ownership) ở server-side.

## 8. Quan sát hệ thống
### Quyết định
- Triển khai đầy đủ stack observability trong `docker-compose.yml`: **Prometheus** (thu thập metrics qua Spring Actuator `/actuator/prometheus`), **Grafana** (dashboard theo dõi độ trễ API, tỷ lệ lỗi 4xx/5xx, hàng đợi `outbox_events` và `profitPool`), và **OpenTelemetry + Jaeger** (distributed tracing xuyên suốt REST request, Outbox relay và Kafka ở Pha 2).
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
### Lý do
- Tách tầng BFF theo thiết bị (Web vs Mobile) khỏi 6 domain microservices giúp đội Mobile/Frontend chủ động thay đổi cấu trúc màn hình mà không đụng vào hợp đồng domain hay CSDL của các service lõi.
### Luật
- Không bao giờ cho phép một microservice hoặc BFF phụ thuộc Maven trực tiếp vào microservice khác (chỉ được dùng `common-kernel` và hợp đồng REST/Kafka).
- Không bao giờ đưa logic nghiệp vụ đặc thù của một service vào `common-kernel` hay vào tầng BFF.

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
