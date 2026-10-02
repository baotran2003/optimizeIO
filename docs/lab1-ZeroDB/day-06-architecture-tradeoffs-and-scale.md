# 📅 NGÀY 6: MỞ RỘNG KIẾN TRÚC — TỪ LOCAL IN-MEMORY ĐẾN QUY MÔ DUOLINGO

> **Mục tiêu:** Hiểu "bức tranh toàn cảnh" của hệ thống Duolingo thực tế: Tại sao họ không dùng Redis? Tại sao phải dùng S3 làm trung gian? Đâu là những điểm yếu (Trade-offs) và rủi ro của kiến trúc In-Memory? Từ đó biết cách **đặt câu hỏi thiết kế hệ thống** như một kỹ sư thực thụ.

> **Ngày này tập trung vào tư duy kiến trúc (Architectural Thinking). Bài tập chủ yếu là đọc, phân tích và viết — không viết nhiều code mới.**

---

## 0. "Phá mã" thuật ngữ: Từ Cloud AWS sang Code Spring Boot của bạn

Trước khi đọc sâu vào hệ thống, hãy dùng bảng "từ điển" này để không bị ngợp bởi các từ ngữ Cloud:

| Khái niệm trên AWS (Duolingo) | Bản chất thực tế là gì? | Code tương đương trong project của bạn |
| :--- | :--- | :--- |
| **Worker** | Một chương trình Java đang chạy làm đúng 1 nhiệm vụ | Class Service của bạn (ví dụ: `InMemoryPreloadService`) |
| **Interim Worker** | "Worker trung gian" làm cầu nối: Đọc từ kho lưu trữ $\rightarrow$ nạp vào RAM | Chính là method `preload()` trong `InMemoryPreloadService.java` |
| **ECS Cluster** | Máy chủ AWS bật sẵn 20 bản sao Spring Boot chạy song song | Bạn bấm nút **Run** 20 lần cho 20 app trên 20 máy |
| **Load Chunk** | Chia nhỏ 4 triệu user thành 20 phần (mỗi phần 200k) để nạp | Thay vì 1 máy nạp hết, 20 máy mỗi máy nạp 1 phần nhỏ vào `List` |
| **AWS S3** | Nơi lưu file phẳng chứa 4 triệu user (rẻ, tải nhanh) | PostgreSQL của bạn (bảng `user_devices`) |
| **Local In-Memory RAM** | Thanh RAM vật lý cắm trực tiếp trên máy của Worker | `List<DeviceCacheItem>` lưu trên RAM của bạn |

> **Quy tắc cốt lõi:** Bất cứ khi nào bạn thấy từ **"Interim Worker nạp Chunk từ S3 vào RAM"**, hãy nghĩ ngay đến: **"`InMemoryPreloadService` đọc dữ liệu vào `List` trên RAM"**!

---

## 1. Toàn cảnh Pipeline của Duolingo (Từ A đến Z)

Hãy nhìn lại toàn bộ hành trình từ "Có danh sách 4 triệu user" đến "User nhận được notification trên điện thoại":

```
[Trước vài THÁNG]
DynamoDB ──────────────────────────────────────────────────────────────────
(User DB)   SELECT user_id, device_token WHERE active=true
               │
               ▼ (Bất đồng bộ - Async)
            Notification Campaign Service
               │
               ▼
            AWS S3 ←── Lưu file phẳng: [user_id, device_token, os_type]
            (Cheap Storage)


[Trước vài GIỜ]
            AWS S3
               │
               ▼ Scale lên 20 Interim Workers (ECS Cluster)
            [Worker 1] ──► Load Chunk 1 (200k users) vào RAM
            [Worker 2] ──► Load Chunk 2 (200k users) vào RAM
            ...
            [Worker 20] ──► Load Chunk 20 (200k users) vào RAM
               │
               ▼
            Warm-up xong: 4 triệu user_id → device_token nằm trên RAM


[Thời điểm GIỜ G - Bấm nút đỏ]
            Marketing bấm nút
               │
               ▼
            API Server nhận request
               │
               ▼ Gửi 50+ message vào FIFO Queue
            AWS SQS FIFO Queue (Chống duplicate)
               │
               ▼
            Interim Workers đọc message, phân phối 10,000+ message tiếp
               │
               ▼ Đọc từ RAM (Zero DB Query!)
            SQS Queue thứ 2 (batch 500 iOS / 250 Android per message)
               │
               ▼
            5,000+ Notification Workers
               │          │
               ▼          ▼
            APNS        FCM
          (Apple)    (Android)
               │          │
               ▼          ▼
            iPhone     Android Device
```

---

## 2. Câu hỏi đầu tiên: Tại sao không dùng Redis?

Đây là câu hỏi đầu tiên bất kỳ kỹ sư nào cũng sẽ hỏi: **"Redis cũng lưu trữ trên RAM, tại sao không dùng Redis thay vì phải lưu trên Local RAM của Worker?"**

### Redis là gì và nó hoạt động thế nào?

Redis (Remote Dictionary Server) là một cơ sở dữ liệu **In-Memory** nằm trên một server **riêng biệt**. Khi ứng dụng cần đọc dữ liệu từ Redis, nó phải:

```
Application Server ──[TCP Socket]──► Redis Server ──► Trả kết quả về ──[TCP Socket]──► App
(Tốn: 1 Network Round-Trip ~ 0.5ms - 2ms)
```

### Bài toán số học: Redis vs Local RAM

Nếu Duolingo dùng Redis để lưu 4 triệu user_id → device_token:

* Để gửi 800,000 noti/giây, cần đọc 800,000 bản ghi/giây từ Redis.
* Mỗi lần đọc Redis tốn ~0.5ms (mạng nội bộ AWS).
* Throughput tối đa của 1 Redis connection: $\frac{1}{0.5ms} = 2,000 \text{ ops/giây}$
* Để đạt 800,000 ops/giây cần: $\frac{800,000}{2,000} = 400 \text{ connections đến Redis}$
* Một cụm Redis Enterprise tốt nhất chỉ chịu được ~1 triệu ops/giây với **hàng chục server** → Cực kỳ đắt đỏ và phức tạp.

**Local RAM của Worker:**
* Đọc từ ArrayList trên RAM: ~10-20 nanoseconds (không qua mạng).
* Throughput lý thuyết: hàng chục triệu ops/giây trên 1 core CPU.
* Chi phí: **$0 thêm** (RAM đã có sẵn trên server EC2).

👉 **Kết luận:** Local RAM nhanh hơn Redis từ **10,000 đến 100,000 lần** cho bài toán này vì không cần đi qua mạng.

### Vậy Redis dùng để làm gì?

Redis phù hợp khi:
* Cần **chia sẻ trạng thái** giữa nhiều server (Session, Rate Limiting counter, Distributed Lock).
* Cần **tìm kiếm/lọc** linh hoạt theo nhiều tiêu chí.
* Dữ liệu **thay đổi thường xuyên** và cần đồng bộ real-time giữa các node.

Local In-Memory phù hợp khi:
* Dữ liệu **đọc cực kỳ nhiều, ít/không thay đổi** trong khoảng thời gian ngắn.
* Cần **throughput tối đa** (không thể chấp nhận bất kỳ network latency nào).
* Mỗi worker **tự chủ** và không cần đồng bộ dữ liệu với worker khác.

---

## 3. Tại sao phải dùng S3 làm trung gian?

Một câu hỏi tiếp theo: **"Tại sao không Interim Worker đọc thẳng từ DynamoDB vào RAM? Cần gì phải qua S3?"**

### Vấn đề khi đọc thẳng từ DynamoDB:

DynamoDB là Database production đang phục vụ hàng triệu user thật. Nếu 20 Interim Workers đồng thời đọc 4 triệu bản ghi từ DynamoDB:
* **Tốn RCU (Read Capacity Units):** DynamoDB tính phí theo số lần đọc. Đọc 4 triệu bản ghi 20 lần = 80 triệu lần đọc → Chi phí khổng lồ.
* **Cạnh tranh tài nguyên:** Làm chậm các query thông thường của user đang học Duolingo vì cùng chia sẻ bandwidth của DynamoDB.
* **Không thể tua lại:** Nếu cần đọc lại (worker crash), phải query DynamoDB lần nữa.

### Giải pháp: S3 làm Data Lake trung gian

* S3 lưu trữ cực rẻ (~$0.023/GB/tháng).
* Đọc từ S3 nhanh và song song (Parallel multi-part download).
* Snapshot dữ liệu được tạo **1 lần** vài tháng trước → 20 worker chỉ cần đọc từ file S3 tĩnh, không tốn chi phí DynamoDB thêm.
* Nếu Worker crash, chỉ cần đọc lại file S3 mà không cần query DynamoDB.

---

## 4. Trade-offs (Đánh đổi) — Những gì có thể sai với kiến trúc này

Một kỹ sư giỏi không chỉ biết **ưu điểm** mà phải hiểu rõ **điểm yếu** và cách xử lý chúng.

### Trade-off 1: Stale Data (Dữ liệu lỗi thời)

**Vấn đề:** File S3 được tạo trước sự kiện vài tháng. Nếu user đổi điện thoại hoặc xóa app trong thời gian đó thì sao?

```
Tháng 10: Snapshot S3 ghi nhận: User 123 → device_token "abc"
Tháng 2 (Super Bowl): User 123 đã đổi iPhone mới → device_token là "xyz"

Kết quả: Notification gửi đến device_token "abc" (điện thoại cũ) → Thất bại
```

**Cách Duolingo xử lý:**
* Chấp nhận một tỷ lệ thất bại nhỏ (~1-5%) do stale data — đây là sự đánh đổi có chủ ý.
* Mục tiêu là **gửi nhanh nhất có thể** cho số đông, chứ không phải 100% thành công.
* APNS và FCM sẽ trả về error code khi token không hợp lệ → Hệ thống ghi nhận và dọn dẹp.

### Trade-off 2: Single Point of Failure — Worker Crash

**Vấn đề:** Nếu 1 trong 20 Interim Workers bị crash sau khi đã nạp dữ liệu vào RAM thì sao?

**Cách xử lý:**
* ECS (Elastic Container Service) tự động phát hiện container chết và khởi động lại.
* Worker mới chỉ cần đọc lại file S3 (đã có sẵn) → Pre-warm lại chunk của mình.
* Đây là lý do tại sao Pre-warm diễn ra **vài tiếng trước** giờ G, không phải 5 phút trước.

### Trade-off 3: Memory Pressure trên Worker

**Vấn đề:** 4 triệu bản ghi trong RAM tốn bao nhiêu? Nếu Worker EC2 không đủ RAM?

**Tính toán sơ bộ:**
* 1 bản ghi: `user_id` (8 bytes) + `device_token` (~100 bytes) + `os_type` (7 bytes) = ~115 bytes.
* 4 triệu bản ghi / 20 workers = **200,000 bản ghi / worker**.
* 200,000 × 115 bytes ≈ **23MB RAM / worker** → Hoàn toàn trong tầm kiểm soát!

**Bài học:** Luôn tính toán sơ bộ bộ nhớ trước khi lo lắng.

### Trade-off 4: Thời gian Pre-warm không đủ

**Vấn đề:** Nếu sự kiện xảy ra sớm hơn dự kiến (quảng cáo bắt đầu khi chưa Pre-warm xong)?

**Cách Duolingo xử lý:**
* Pre-warm bắt đầu đủ sớm (vài tiếng trước).
* Hệ thống monitoring theo dõi % pre-warm hoàn thành → Chỉ enable nút bấm khi đã đạt 100%.

---

## 5. Đọc lại code đã viết với con mắt kiến trúc sư

Bây giờ hãy mở lại code chúng ta đã viết trong Lab 1 và đặt câu hỏi kiến trúc cho từng phần:

### Đọc `InMemoryPreloadService.java`

Mở file: `src/main/java/com/example/optimizeIO/lab1/service/InMemoryPreloadService.java`

Trả lời các câu hỏi sau trong đầu (hoặc viết ra):
1. Dữ liệu đang được load từ PostgreSQL — trong hệ thống thực tế của Duolingo, nguồn này là **S3**. Điểm tương đồng và khác biệt là gì?
2. `List<DeviceCacheItem>` đang là `Collections.unmodifiableList()` — Tại sao cần `unmodifiable`? Điều gì xảy ra nếu Worker đọc trong khi một luồng khác đang ghi đè?
3. Hàm `preload()` đang có annotation `synchronized` — Điều này giải quyết vấn đề gì? Và nó gây ra hạn chế gì về hiệu năng?
4. `volatile boolean isPreloaded` — Tại sao cần `volatile` thay vì `boolean` thông thường?

### Đọc `DataSeederService.java`

Mở file: `src/main/java/com/example/optimizeIO/lab1/service/DataSeederService.java`

Trả lời:
1. Nếu đây là hệ thống thực tế, `batchSize = 2000` có phải là con số tốt nhất không? Bạn sẽ thay đổi như thế nào?
2. Khi `TRUNCATE TABLE` trước khi seed — Trong thực tế, tại sao không nên TRUNCATE bảng Production như vậy?

---

## 6. Bài tập Ngày 6: Cải thiện hệ thống hiện tại

### Bài tập 6.1 — Thêm cơ chế "Health Check" cho Pre-warm

**Mục tiêu:** Thêm API trả về % hoàn thành của quá trình Pre-warm để đội engineering có thể monitor.

**Bạn cần viết:**
Cập nhật `InMemoryPreloadService.java` để track tiến độ, sau đó thêm endpoint:
```
GET /api/lab1/prewarm-status
```
Trả về:
```json
{
  "status": "IN_PROGRESS",
  "progressPercent": 67.5,
  "loadedRecords": 67500,
  "totalExpected": 100000,
  "elapsedMs": 1230
}
```

**Gợi ý:** Dùng `AtomicLong` để đếm số bản ghi đã load mà không cần `synchronized`.

---

### Bài tập 6.2 — Viết câu trả lời phỏng vấn hệ thống (System Design Answer)

**Đây là bài tập quan trọng nhất của ngày này.**

Giả sử trong buổi phỏng vấn, interviewer hỏi:
> *"Hãy thiết kế một hệ thống gửi Push Notification đến 10 triệu người dùng trong vòng 10 giây."*

Hãy viết câu trả lời của bạn vào cuối file này, bao gồm:
1. Phân tích yêu cầu (throughput cần đạt, ràng buộc)
2. Giải pháp bạn đề xuất và tại sao
3. Trade-offs bạn chấp nhận
4. Cách monitor và xử lý lỗi

**Lưu ý:** Không cần hoàn hảo. Đây là bài tập để bạn tự luyện diễn đạt tư duy kỹ thuật.

---

## 7. Câu hỏi tự kiểm tra sau Ngày 6

1. Sự khác biệt cốt lõi giữa Redis và Local In-Memory trong bài toán throughput cực cao?
2. S3 đóng vai trò gì trong pipeline của Duolingo và tại sao không đọc thẳng từ DynamoDB?
3. Kể 3 điểm yếu (Trade-offs) của kiến trúc In-Memory Preloading và cách Duolingo xử lý từng điểm?
4. Tại sao `volatile` keyword lại cần thiết trong `InMemoryPreloadService`?

---

## 8. Kết quả và ghi chú thực nghiệm của bạn

*(Tự điền sau khi hoàn thành bài tập)*

### Bài tập 6.1 — Kết quả
```
// Dán code bạn viết vào đây sau khi hoàn thành
```

### Bài tập 6.2 — Câu trả lời phỏng vấn System Design
```
// Viết câu trả lời của bạn vào đây
```

### Câu trả lời tự kiểm tra
```
1. ...
2. ...
3. ...
4. ...
```
