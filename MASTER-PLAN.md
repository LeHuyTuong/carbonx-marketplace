# Master Plan — CarbonX Marketplace (DDD Modular Monolith → Microservices)

Mỗi tầng có Quyết định (chọn gì), Lý do (vì sao), Luật (AI phải theo, viết dạng "luôn ..." hoặc "không bao giờ ...").
Tầng sau không được mâu thuẫn tầng trước. Muốn đổi một quyết định thì sửa file này trước, rồi mới sửa code.
Task nào đi ngược file này thì dừng lại và hỏi.

## 1. Nghiệp vụ
### Quyết định
- Hệ thống sàn giao dịch tín chỉ carbon xe điện gồm 4 vai trò (`EV_OWNER`, `COMPANY`, `CVA`, `ADMIN`) vận hành chuỗi MRV 2 cấp (CVA → Admin), phát hành lô tín chỉ bất biến theo phương pháp luận `VM0038`, mua bán trên sàn (phí sàn 5%), triệt tiêu tín chỉ và chia sẻ doanh thu về ví chủ xe điện.
- Luồng mua tín chỉ bằng số dư ví được chuẩn hoá thành thanh toán tức thì 1 bước (`Order` chuyển thẳng `FINISHED`, trừ ví người mua, cộng ví người bán và chuyển quyền sở hữu tín chỉ trong cùng một luồng chốt đơn, loại bỏ trạng thái `PENDING` thừa).
- Mục tiêu sản phẩm là Portfolio Showcase kỹ thuật sâu, chạy thực tế bằng Docker Compose và tái sử dụng toàn bộ giao diện React 19 hiện tại.
### Lý do
- Chốt chặt luồng nghiệp vụ và loại bỏ bước thừa giúp ranh giới domain ổn định trước khi tách module DDD và tránh sửa chéo nhiều service về sau.
### Luật
- Luôn giữ nguyên 4 vai trò và quy trình thẩm định 2 cấp (CVA trước, Admin sau) khi phát hành tín chỉ.
- Luôn ghi nhận tối thiểu một dòng `WalletTransaction` bất biến chứa số dư trước và sau cho mọi thay đổi số dư ví.
- Không bao giờ cho phép sửa ngược thông số của một `Lô tín chỉ` sau khi đã phát hành.

## 2. Công nghệ
### Quyết định
- Backend dùng Java 17 + Spring Boot 3.3 + MySQL 8 + Flyway + Spring Data JPA; Frontend dùng React 19 + TypeScript + Vite 6 + Tailwind CSS v4 + TanStack Query + Zustand + Nginx.
- Thực hiện trực tiếp trên repo độc lập `carbonx-standalone` (`LeHuyTuong/carbonx-marketplace`), đổi `artifactId` backend sang `carbonx-backend`.
- Lộ trình: Pha 1 dựng DDD Modular Monolith trong 1 ứng dụng Spring Boot; Pha 2 bổ sung Spring Cloud Gateway và Apache Kafka (KRaft mode) để tách `mrv-service` và `notification-service`.
### Lý do
- Giữ vững stack ổn định đã có giúp tập trung toàn bộ công sức vào kiến trúc DDD, Flyway, Outbox và tách Microservices theo từng bước an toàn.
### Luật
- Luôn đóng gói toàn bộ dịch vụ chạy được bằng `docker compose up` trên máy local.
- Không bao giờ thêm thư viện bên thứ ba nếu chưa kiểm tra lỗ hổng và phiên bản tương thích Java 17 / React 19.

## 3. Design pattern
### Quyết định
- Pattern đã chọn: Pragmatic DDD Modular Monolith kết hợp Transactional Outbox (`outbox_events`), Port/Adapter (`DomainEventPublisher`), Khoá bi quan (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`) và Idempotency Key.
- Áp tới mức nào: Các luồng tài chính và thay đổi tồn kho (`Order`, `Wallet`, `Withdrawal`, `ProfitSharing`) dùng khoá bi quan + Idempotency Key + ghi Domain Event vào bảng `outbox_events`; các luồng đọc/cập nhật hồ sơ đơn giản (KYC, xe điện, danh mục dự án) dùng một database transaction cục bộ là đủ.
- Khi sập: Trạng thái sự kiện lưu bền vững trong bảng `outbox_events` (`PENDING`, `SENT`, `FAILED`) và trạng thái chi trả lưu trên từng dòng `ProfitDistributionDetail` với `claim_token`; khi khởi động lại, `OutboxRelayScheduler` quét lại các bản ghi chưa gửi; nếu một sự kiện lỗi liên tiếp 5 lần thì chuyển sang trạng thái `DEAD_LETTER`, dừng thử lại tự động và phát cảnh báo cho đội vận hành.
### Lý do
- Pragmatic DDD kết hợp Transactional Outbox cho phép chạy in-process nhẹ nhàng ở Pha 1 và chuyển sang Kafka Producer ở Pha 2 mà không phải sửa logic nghiệp vụ lõi.
### Luật
- Luôn ghi Domain Event vào bảng `outbox_events` trong cùng transaction với thay đổi dữ liệu của Aggregate Root.
- Không bao giờ để một luồng chuyển tiền hoặc chốt đơn chạy mà thiếu khoá bi quan hoặc cơ chế chống xử lý trùng (idempotency).

## 4. UI
### Quyết định
- Giữ nguyên bộ giao diện React 19 + Tailwind CSS v4 đã chuẩn hoá theo 4 vai trò (Sidebar phân nhóm, Topbar Breadcrumb, `FileDropzone`, `ConfirmDialog`, KPI kèm đơn vị, biểu đồ Recharts).
- Cập nhật màn hình `/marketplace` và `/app/company/orders` khớp luồng mua tức thì 1 bước (bỏ nút `Hoàn tất` / `Huỷ đơn` trên đơn thanh toán ví).
- Tài liệu và giao diện người dùng giữ nhất quán tiếng Việt.
### Lý do
- Giao diện hiện tại đã đạt 0 lỗi trên `ui-audit` và phản ánh đúng 4 vai trò nghiệp vụ, chỉ cần đồng bộ lại màn hình đơn mua theo flow mới.
### Luật
- Luôn dùng `ConfirmDialog` và `FileDropzone` dùng chung cho thao tác xác nhận phá huỷ và tải tệp.
- Không bao giờ bắt người dùng nhập tay mã ID nội bộ của cơ sở dữ liệu trên giao diện.

## 5. Dữ liệu và quy ước
### Quyết định
- Mô hình thực thể khái niệm hợp nhất được định nghĩa tại `docs/diagrams/conceptual-erd.drawio` với thực thể lõi là `Lô tín chỉ` (19 thực thể nghiệp vụ, 18 quan hệ, 0 chu trình).
- Quản lý lược đồ cơ sở dữ liệu hoàn toàn bằng **Flyway** (`src/main/resources/db/migration/V1..Vn`), đặt `spring.jpa.hibernate.ddl-auto=validate` ở môi trường production.
- Mỗi Bounded Context sở hữu nhóm bảng riêng và tham chiếu sang module khác bằng định danh (`UUID`/`Long` ID), không tạo khoá ngoại vật lý chéo giữa các module.
### Lý do
- Tách quyền sở hữu bảng và bỏ khoá ngoại chéo giúp việc bóc tách cơ sở dữ liệu theo từng microservice ở Pha 2 diễn ra không cần viết lại truy vấn.
### Luật
- Luôn tạo file migration Flyway mới theo thứ tự tăng dần cho mọi thay đổi schema cơ sở dữ liệu.
- Không bao giờ chỉnh sửa lại file migration Flyway đã được áp dụng lên nhánh `main`.
- Không bao giờ tạo khoá ngoại vật lý hoặc câu lệnh `JOIN` SQL xuyên qua ranh giới hai Bounded Context khác nhau.

## 6. Kiểm thử
### Quyết định
- Áp dụng 3 lớp kiểm thử tự động: `ArchUnit` (kiểm tra ranh giới 6 module DDD), `JUnit 5 + Testcontainers MySQL 8` (kiểm thử bất biến tiền, khoá `FOR UPDATE`, Transactional Outbox và phân quyền `403`), và `Vitest + React Testing Library` cho Frontend.
- Mọi bản vá bảo mật hoặc luồng tiền đều phải đi kèm test hồi quy (regression test).
### Lý do
- Kết hợp ArchUnit và Testcontainers trên MySQL thật bắt được cả vi phạm kiến trúc lẫn lỗi tranh chấp đồng thời mà Mockito đơn thuần bỏ sót.
### Luật
- Luôn giữ `ModuleBoundaryArchTest` xanh trong mọi commit chạm vào mã nguồn backend.
- Luôn kiểm chứng bằng MySQL thật (Testcontainers) cho các thao tác chốt đơn đồng thời và chia sẻ doanh thu.
- Không bao giờ bỏ qua hoặc tắt một bài test đang fail để hợp thức hoá bản build.

## 7. Bảo mật
### Quyết định
- Áp dụng RBAC chặt ở tầng server bằng `@PreAuthorize` trên mọi controller, xác thực JWT stateless (tắt `httpBasic`, ẩn Swagger ở production, truyền token OAuth2 qua URL fragment `#token=`).
- Fail-closed toàn bộ secret (`JWT_SECRET`, khoá cổng thanh toán, mật khẩu DB) ở `application-prod.properties` và `docker-compose.yml`; ẩn hoàn toàn khối đăng nhập mẫu khỏi bundle production; bật security headers trên Nginx (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `server_tokens off`).
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
- Chia backend thành 6 Bounded Contexts độc lập: `identity`, `mrv`, `registry`, `trading`, `ledger`, `notification`, cùng 1 kernel nhỏ `shared` (chỉ chứa `BaseEntity`, Value Objects bất biến, `DomainEvent` và hạ tầng Outbox).
- Mỗi module cấu trúc thống nhất 4 gói con: `api`, `application`, `domain`, `infrastructure`.
- Giao tiếp đồng bộ liên module chỉ đi qua Public Port ở gói `api` hoặc `application.port`; giao tiếp bất đồng bộ đi qua `DomainEvent` trong Outbox.
### Lý do
- Ranh giới rõ ràng theo Bounded Context cho phép nhiều agent hoặc lập trình viên làm việc song song trên các module khác nhau mà không giẫm chân nhau.
### Luật
- Không bao giờ cho phép một module import trực tiếp lớp trong gói `infrastructure` hoặc `repository` của module khác.
- Không bao giờ đưa logic nghiệp vụ đặc thù của một module vào gói `shared`.

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
