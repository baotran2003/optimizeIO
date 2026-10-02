# 📅 NGÀY 5: THẮT NÚT CỔ CHAI TẠI DATABASE CONNECTION POOL (HikariCP)

> **Mục tiêu:** Hiểu tại sao khi có Spike Traffic (đột biến truy cập), Database chết không phải vì CPU hay RAM mà vì **nghẽn Connection Pool**. Thực hành tự tay chứng kiến `ConnectionTimeoutException`, sau đó tự cấu hình HikariCP để xử lý tải cao.

> **Ngày này thực hành CODE nhiều hơn đọc. Mỗi bài tập, hãy tự viết trước, chỉ xem gợi ý khi thực sự bí.**

---

## 1. Bối cảnh: Tại sao Connection Pool lại quan trọng?

Hãy nhớ lại bài toán Duolingo: Khi 4 triệu người đồng loạt nhận thông báo và mở app, hàng triệu request đổ vào backend cùng lúc.

Nếu backend không Pre-warm In-Memory và vẫn query Database bình thường thì điều gì xảy ra? Không phải CPU chết, không phải RAM cạn. Mà là **Database Connection bị cạn kiệt hoàn toàn.**

---

## 2. HikariCP là gì và tại sao Spring Boot dùng nó?

**Connection Pool** là một "hồ bơi" chứa sẵn các kết nối đến Database. Thay vì mỗi request phải:
```
Mở TCP socket ──► Xác thực PostgreSQL ──► Sử dụng ──► Đóng kết nối
(Tốn ~20ms - 50ms chỉ riêng bước thiết lập kết nối!)
```

Connection Pool làm sẵn N kết nối khi khởi động, request chỉ cần:
```
Mượn connection từ Pool ──► Sử dụng ──► Trả lại Pool
(Chỉ tốn < 1ms để mượn!)
```

**HikariCP** là thư viện Connection Pool nhanh nhất trong thế giới Java (đây là lý do Spring Boot chọn nó làm mặc định từ Spring Boot 2.0). Tên "Hikari" tiếng Nhật nghĩa là "Ánh sáng".

---

## 3. Cấu hình mặc định của HikariCP (Cái bẫy ẩn)

Spring Boot khi không cấu hình gì thêm sẽ dùng các thông số mặc định của HikariCP:

```yaml
# Giá trị mặc định - Không cần viết vào application.properties vì đây là mặc định
spring.datasource.hikari.maximum-pool-size=10       # Tối đa 10 connections
spring.datasource.hikari.minimum-idle=10            # Duy trì sẵn 10 connections
spring.datasource.hikari.connection-timeout=30000   # Chờ tối đa 30 giây nếu Pool đầy
spring.datasource.hikari.idle-timeout=600000        # Connection nhàn rỗi sau 10 phút thì đóng
spring.datasource.hikari.max-lifetime=1800000       # Connection sống tối đa 30 phút
```

👉 **Con số chết chóc: `maximum-pool-size=10`**

Điều này có nghĩa là: **Chỉ có đúng 10 request được phép truy cập Database cùng một lúc!**

---

## 4. Hiện tượng Connection Exhaustion (Cạn kiệt kết nối)

Hãy hình dung Pool như một cửa hàng thuê xe đạp chỉ có **10 chiếc**.

```
[Bình thường - 5 khách cùng lúc]:
Khách 1 ──► Mượn xe #1 (200ms) ──► Trả lại ✓
Khách 2 ──► Mượn xe #2 (200ms) ──► Trả lại ✓
...5 xe đang dùng, còn 5 xe rảnh rỗi ✓

[Spike Traffic - 15 khách cùng lúc, mỗi người đi 2 giây]:
Khách 1-10 ──► Mượn xe #1-10 (giữ xe 2 giây)
Khách 11   ──► ❌ Xếp hàng chờ...
Khách 12   ──► ❌ Xếp hàng chờ...
Khách 13   ──► ❌ Xếp hàng chờ...
...Nếu chờ quá 30 giây ──► ConnectionTimeoutException ──► Sập!
```

**Hậu quả Cascading Failure (Sụp đổ dây chuyền):**
1. Connection Pool cạn kiệt.
2. Các request mới phải xếp hàng chờ connection.
3. HTTP thread của Tomcat cũng bị block theo (Tomcat mặc định chỉ có 200 threads).
4. Các API KHÔNG liên quan đến Database (ví dụ: trả về `"Hello World"`) cũng bị lag vì thread đang bị block hết!
5. Toàn bộ ứng dụng đứng hình dù Database vẫn hoàn toàn khỏe mạnh.

---

## 5. Tại sao không đơn giản tăng `maximum-pool-size` lên 10,000?

Đây là câu hỏi nhiều người sẽ hỏi ngay. Câu trả lời nằm ở phía **PostgreSQL**:

Mỗi connection tới PostgreSQL là **1 process riêng biệt** trên hệ điều hành (PostgreSQL dùng kiến trúc Process-per-connection):
* Mỗi PostgreSQL connection process ngốn khoảng **5MB - 10MB RAM** trên server DB.
* PostgreSQL mặc định chỉ cho phép tối đa **100 connections** (`max_connections=100`).
* Nếu có 1,000 connections đồng thời, PostgreSQL phải quản lý 1,000 process, gây **Context Switching** khổng lồ trên CPU của server DB → Thậm chí còn chậm hơn 10 connections!

👉 **Quy tắc thực tế:** Kích thước Pool tối ưu thường là:
$$\text{Pool Size} = (\text{Số lõi CPU của DB Server}) \times 2 + \text{Số ổ đĩa đang dùng}$$
Ví dụ: DB server 4 lõi, 1 ổ SSD → Pool tối ưu ≈ **9 connections**.

---

## 6. Lộ trình thực hành Ngày 5 (Tự viết code)

Dưới đây là **4 bài tập theo thứ tự**, từ dễ đến khó. Mỗi bài có:
- Mô tả rõ **bạn cần viết gì**.
- **Gợi ý** để bắt đầu.
- **Kết quả kỳ vọng** để tự kiểm tra.

---

### Bài tập 5.1 — Tạo một endpoint "chậm" (Slow Endpoint)

**Mục tiêu:** Tạo ra một API cố tình giữ connection trong 2 giây để mô phỏng một query nặng.

**Bạn cần tự viết:**
Tạo file mới: `src/main/java/com/example/optimizeIO/lab1/controller/Day5Controller.java`

Endpoint cần có:
- Mapping: `GET /api/lab1/day5/slow-query`
- Dùng `JdbcTemplate` để gọi `SELECT pg_sleep(2)` (lệnh PostgreSQL làm cho query ngủ 2 giây).
- Trả về thời gian thực thi.

**Gợi ý:**
```java
// Gọi lệnh PostgreSQL pg_sleep(N) để giả lập query nặng tốn N giây
jdbcTemplate.execute("SELECT pg_sleep(2)");
```

**Kết quả kỳ vọng:** Gọi API này và thấy response mất đúng ~2,000ms.

---

### Bài tập 5.2 — Viết kịch bản bắn đồng thời (Concurrent Stress Test)

**Mục tiêu:** Dùng Java `ExecutorService` để giả lập 15 request bắn đồng thời vào endpoint chậm của Bài 5.1. Quan sát khi 15 request cùng chiếm Pool 10 connections.

**Bạn cần tự viết:**
Tạo file mới: `src/main/java/com/example/optimizeIO/lab1/service/Day5ConnectionPoolService.java`

Service cần có hàm:
- `runConcurrentStressTest(int concurrentRequests)`: Tạo `concurrentRequests` threads, mỗi thread gọi `jdbcTemplate.execute("SELECT pg_sleep(2)")` đồng thời. Đo và in ra thời gian mỗi thread mất, thread nào bị `timeout`.

**Gợi ý:**
```java
ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
List<Future<Map<String, Object>>> futures = new ArrayList<>();

for (int i = 1; i <= concurrentRequests; i++) {
    final int threadId = i;
    futures.add(executor.submit(() -> {
        // TODO: Gọi jdbcTemplate.execute() và đo thời gian
        // TODO: Bắt SQLException hoặc CannotGetJdbcConnectionException
    }));
}
// TODO: Thu gom kết quả từ tất cả Future
```

**Kết quả kỳ vọng:**
- Thread 1-10: Chạy bình thường, mỗi thread mất ~2,000ms.
- Thread 11-15: Phải **xếp hàng chờ** connection được trả về, tổng thời gian mất ~4,000ms (chờ 2s + chạy 2s).

---

### Bài tập 5.3 — Cấu hình HikariCP và quan sát sự thay đổi

**Mục tiêu:** Điều chỉnh `connection-timeout` xuống thấp để thấy `ConnectionTimeoutException` rõ ràng hơn, sau đó tăng Pool Size để xử lý tải.

**Bạn cần tự viết vào `application.properties`:**

Bước 1 — Làm Pool dễ "chết" hơn để quan sát:
```properties
# TODO: Thêm 3 dòng cấu hình HikariCP này vào application.properties
# Maximum connections trong pool
spring.datasource.hikari.maximum-pool-size=???

# Thời gian tối đa chờ mượn connection trước khi ném Exception (ms)
spring.datasource.hikari.connection-timeout=???

# Bật log chi tiết Pool để quan sát
spring.datasource.hikari.leak-detection-threshold=???
```

**Gợi ý:** Đặt `connection-timeout=5000` (5 giây) để dễ quan sát timeout hơn mức mặc định 30 giây.

Bước 2 — Tăng Pool Size và xem thay đổi:
Sau khi quan sát timeout, thử tăng `maximum-pool-size` lên 15 rồi chạy lại Bài 5.2. Quan sát: Thread 1-15 có còn bị timeout không?

**Kết quả kỳ vọng:**
- Pool size 10 + 15 requests → Thread 11-15 phải xếp hàng hoặc timeout.
- Pool size 15 + 15 requests → Tất cả 15 threads chạy đồng thời, mỗi thread mất ~2,000ms.

---

### Bài tập 5.4 — Thêm HikariCP Metrics Endpoint (Quan sát Pool "thở")

**Mục tiêu:** Tạo API trả về trạng thái thực tế của Connection Pool (Bao nhiêu connection đang dùng, bao nhiêu đang rảnh, bao nhiêu đang chờ).

**Bạn cần tự viết vào `Day5Controller.java`:**
```java
// HikariCP expose thông tin Pool qua interface HikariDataSource
// Inject DataSource vào Controller, cast về HikariDataSource
// Gọi các hàm: getHikariPoolMXBean().getActiveConnections(), .getIdleConnections(), .getThreadsAwaitingConnection()
```

**Gợi ý import:**
```java
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
```

**Kết quả kỳ vọng:** Trong khi đang chạy Bài 5.2 (15 requests đồng thời), gọi API Metrics này và thấy:
```json
{
  "activeConnections": 10,
  "idleConnections": 0,
  "threadsAwaitingConnection": 5
}
```

---

## 7. Câu hỏi tự kiểm tra sau khi hoàn thành 4 bài tập

1. Tại sao HikariCP lại được đặt tên là "Hikari" (Ánh sáng)?
2. Sự khác biệt giữa `connection-timeout` và `idle-timeout` và `max-lifetime` trong HikariCP?
3. Nếu bạn có DB server 8 lõi CPU, 2 ổ SSD NVMe, bạn sẽ đặt `maximum-pool-size` là bao nhiêu?
4. Tại sao khi Connection Pool bị cạn, cả những API không query DB cũng bị lag?

---

## 8. Mục tiêu thực chiến cần đạt được sau Ngày 5

Sau ngày này, bạn phải nói trôi chảy được câu sau khi đi phỏng vấn:

> *"Khi hệ thống gặp Spike Traffic, Connection Pool là điểm nghẽn đầu tiên. HikariCP mặc định của Spring Boot chỉ có 10 connections. Nếu mỗi request giữ connection trong 500ms và có 100 request đồng thời thì tổng throughput chỉ đạt 20 requests/giây. Để giải quyết, cần kết hợp: (1) Giảm thời gian giữ connection bằng cách tối ưu query, (2) Tăng Pool Size phù hợp với số lõi CPU của DB server, (3) Với bài toán như Duolingo, loại bỏ hoàn toàn DB query tại giờ cao điểm bằng In-Memory Preloading."*

---

## 9. Sau khi hoàn thành 4 bài tập, bổ sung vào docs

Khi bạn đã chạy xong và có số liệu thực tế, hãy tự viết thêm vào cuối file này:

```markdown
## Kết quả thực nghiệm trên máy của tôi

| Bài tập | Cấu hình | Kết quả đo được | Nhận xét |
|:--------|:---------|:----------------|:---------|
| 5.2 | Pool=10, 15 requests | Thread 11-15 chờ ???ms | ... |
| 5.3 | Pool=15, 15 requests | Tất cả chạy ???ms | ... |
```
