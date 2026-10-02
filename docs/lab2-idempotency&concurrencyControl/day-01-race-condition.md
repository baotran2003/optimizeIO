# 🔴 LAB 2 — NGÀY 1: TÁI HIỆN LỖI RACE CONDITION VỚI CẠM BẪY CHECK-THEN-ACT

---

## 📌 1. Bối cảnh & Yêu cầu bài toán

Trong sự kiện Super Bowl của Duolingo, khi quảng cáo 5 giây xuất hiện ngẫu nhiên trên truyền hình trực tiếp, có nhiều người trong đội vận hành (hoặc request bị retry do mạng chập chờn) cùng bấm vào **"Chiếc Nút Đỏ"** để kích hoạt gửi Push Notification đến hàng triệu người dùng.

* **Yêu cầu:** Tính khả biến (**Idempotency**) — Dù có 10 hay 100 người/request kích hoạt cùng lúc, chiến dịch chỉ được phép khởi chạy **đúng 1 lần duy nhất** (`successCount = 1`).
* **Mục tiêu Ngày 1:** Viết code mô phỏng theo tư duy CRUD thông thường (`NaiveTriggerService`), chứng kiến lỗi **Race Condition** xảy ra khiến chiến dịch bị kích hoạt trùng lặp nhiều lần (`successCount > 1`).

---

## ⚠️ 2. Cạm bẫy kinh điển "Check-Then-Act" (Kiểm tra rồi mới Hành động)

Hầu hết lập trình viên thường viết code kiểm tra như sau:

```java
// Bước 1: CHECK (Kiểm tra xem chiến dịch đã tồn tại / đã chạy chưa)
boolean alreadyExists = campaignDatabase.contains(campaignCode);

if (!alreadyExists) {
    // ⚠️ KHOẢNG TRỐNG CHẾT NGƯỜI (Gap of vulnerability)
    // Khoảng trễ xử lý (I/O, context switch, validate...) dù chỉ 0.1ms 
    // cũng đủ để hàng chục luồng khác cùng đọc thấy alreadyExists == false!
    
    // Bước 2: ACT (Lưu lại trạng thái và bắn thông báo)
    executeCampaign(campaignCode, userId);
}
```

### Tại sao đoạn code này gây lỗi nghiêm trọng?
* Hai thao tác **Check** (`contains`) và **Act** (`execute/add`) là **tách rời**, không phải thao tác nguyên tử (**Non-Atomic**).
* Khi 60 luồng cùng ùa vào trong cùng 1 mili-giây, tất cả đều check thấy chưa có ai chạy, và tất cả đều lọt vào khối `if`.
* Dù thay `HashSet` bằng `campaignRepo.existsByCode(...)` trong Database thì lỗi **vẫn xảy ra y hệt** (thậm chí nặng hơn vì Network round-trip giữa app và DB tốn từ 2ms đến 10ms, làm cửa sổ tranh chấp mở rộng hơn gấp hàng chục lần).

---

## ⚙️ 3. Phân tích luồng thực thi trong `NaiveTriggerService.java`

Code sử dụng kỹ thuật đồng bộ bằng `CountDownLatch` để tạo ra sự tranh chấp tài nguyên hoàn hảo:

```
[Main Thread]
   │
   ├── Khởi tạo ExecutorService với pool 60 Threads
   ├── Khởi tạo CountDownLatch startGun = new CountDownLatch(1)
   │
   ├── Tạo và submit 60 Tasks (Thread-1 -> Thread-60)
   │     Mỗi thread khi khởi động đều dừng lại ở: startGun.await();
   │     (60 luồng như 60 vận động viên đứng tại vạch xuất phát)
   │
   └── startGun.countDown(); // 💥 "BẮN SÚNG LỆNH" - 60 luồng đồng loạt lao đi!
```

### Sơ đồ dòng thời gian tranh chấp tại thời điểm T = 0ms:

```
                     startGun.countDown()
                              │
     ┌────────────────┬───────┴────────┬────────────────┐
     ▼                ▼                ▼                ▼
 Thread-51        Thread-54        Thread-55        Thread-56   ... (60 threads)
     │                │                │                │
contains()?      contains()?      contains()?      contains()?
     │                │                │                │
   false            false            false            false  (Chưa ai kịp lưu)
     │                │                │                │
  Lọt qua if       Lọt qua if       Lọt qua if       Lọt qua if
     │                │                │                │
[Sleep 0.1ms]    [Sleep 0.1ms]    [Sleep 0.1ms]    [Sleep 0.1ms]
     │                │                │                │
  add() ✅         add() ✅         add() ✅         add() ✅  (HashSet bị race)
     │                │                │                │
successCount++   successCount++   successCount++   successCount++
     └────────────────┴────────────────┴────────────────┘
                              │
                   successCount = 4 (BUG XẢY RA!) 💀
```

---

## 🧪 4. Kết quả thực nghiệm thực tế

Khi gọi API mô phỏng với **60 concurrent users**:

```json
{
  "campaignCode": "SUPERBOWL_2024",
  "concurrentUsers": 60,
  "successCount": 4,
  "rejectedCount": 0,
  "bugDetected": true,
  "verdict": "❌ RACE CONDITION! Chiến dịch bị kích hoạt 4 lần!",
  "executionLog": [
    "User-55 fired campaign SUPERBOWL_2024",
    "User-54 fired campaign SUPERBOWL_2024",
    "User-56 fired campaign SUPERBOWL_2024",
    "User-51 fired campaign SUPERBOWL_2024"
  ]
}
```

### Phân tích kết quả:
1. **`successCount = 4`:** Có tới 4 luồng cùng ghi nhận chiến dịch thành công. Thay vì bắn 1 tin, hệ thống gửi **4 thông báo giống hệt nhau** đến toàn bộ người dùng.
2. **`rejectedCount = 0`:** Cả 60 luồng đều vượt qua vòng `if (!alreadyExists)`, không có luồng nào rơi vào nhánh `else` ban đầu.
3. **Hiện tượng lạ ở `HashSet`:** Bản thân `HashSet` **không Thread-Safe**. Khi nhiều luồng cùng gọi `.add()` đồng thời, cấu trúc bảng băm (hash bucket) bị biến dạng bộ nhớ, dẫn đến việc nhiều luồng cùng nhận được kết quả `true` khi add cùng 1 key!

---

## 💡 5. Bài học rút ra & Hướng đi cho Ngày 2

| Vấn đề | Hiện tượng ở Ngày 1 | Giải pháp ở các ngày tiếp theo |
| :--- | :--- | :--- |
| **Check-Then-Act** | 2 thao tác Check và Act rời rạc, tạo khoảng trống cho race condition. | Biến thành **1 thao tác nguyên tử (Atomic)** duy nhất. |
| **Khóa CPU / RAM** | `HashSet` không an toàn, code không có lock. | **Ngày 2:** Dùng lệnh CPU CAS (`AtomicBoolean.compareAndSet` hoặc `ConcurrentHashMap.putIfAbsent`) chặn đứng trong nanogiây. |
| **Khóa Database** | Nếu gọi qua DB vẫn bị race do isolation level. | **Ngày 3:** Đặt `UNIQUE Constraint` hoặc `SELECT ... FOR UPDATE` ở tầng Database. |
| **Hệ thống phân tán** | Khi có nhiều máy chủ / ECS instances. | **Ngày 4:** Dùng Redis `SETNX` hoặc SQS FIFO Deduplication ID. |
