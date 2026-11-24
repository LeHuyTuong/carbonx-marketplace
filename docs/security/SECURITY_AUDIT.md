# Security Audit Summary — CarbonX Marketplace

Báo cáo rà soát bảo mật theo **OWASP Top 10** trước khi xuất bản repo độc lập.

## Các kiểm soát đã được vá & áp dụng

| Mã | Mức độ | Hạng mục | Biện pháp đã áp dụng |
|---|---|---|---|
| **SEC-01** | HIGH | Broken Access Control (`VehicleController`) | Áp dụng `@PreAuthorize` tách biệt quyền `EV_OWNER`, `COMPANY`, `ADMIN` trên toàn bộ endpoint `/api/v1/vehicles/**`. |
| **SEC-02** | HIGH | IDOR & Public File Download (`ReportController`) | Khoá `/api/v1/reports/download/{id}` và `/download-json/{id}` cho `ADMIN`/`CVA`; gỡ `/api/v1/reports/files/download` khỏi `PUBLIC_ENDPOINT` và yêu cầu xác thực. |
| **SEC-03** | HIGH | Demo Credentials in Prod Bundle (`LoginPage.tsx`) | Khoá khối Quick Login mẫu sau cờ `import.meta.env.DEV \|\| import.meta.env.VITE_ENABLE_DEMO_LOGIN === 'true'`, loại bỏ hoàn toàn khỏi bundle production. |
| **SEC-04** | MEDIUM | Swagger / HTTP Basic (`AppConfig.java`) | Gỡ `.httpBasic()`; tắt `/swagger-ui/**` và `/v3/api-docs/**` ở profile `prod` (`app.swagger.enabled=false`). |
| **SEC-05** | MEDIUM | Security Headers (`frontend/nginx.conf`) | Tắt `server_tokens`, bổ sung `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`. |
| **SEC-06** | MEDIUM | OAuth2 Token & Error Handling | Chuyển JWT OAuth2 callback sang URL fragment `#token=` (không lưu vào server log/Referer) và trả lỗi JSON tĩnh. |

## Kiểm soát nền tảng có sẵn
- **Fail-closed secrets**: `application-prod.properties` và `docker-compose.yml` bắt buộc `JWT_SECRET` (từ chối khởi động nếu thiếu).
- **Concurrency & Financial Invariants**: Khoá bi quan `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) khi chốt đơn (`OrderServiceImpl`) và chia sẻ doanh thu (`ProfitSharingServiceImpl`), kèm sổ cái bất biến `WalletTransaction`.
- **Supply chain**: `npm audit --omit=dev --audit-level=high` báo `0 vulnerabilities`.
