 # 🔴 LAB 2: NÚT BẤM MÀU ĐỎ HÌNH VUÔNG — IDEMPOTENCY & CONCURRENCY CONTROL

> **Mục tiêu lớn:** Nắm vững bản chất của hiện tượng **Race Condition** khi có nhiều request đồng thời, hiểu sâu khái niệm **Idempotency (Tính khả biến)** trong hệ thống phân tán, và thực hành xây dựng 3 tầng phòng ngự chống kích hoạt trùng lặp: từ **In-Memory CAS (nanogiây)**, **Database Constraints/Locks (mili-giây)** đến **Distributed Lock / SQS FIFO Deduplication**.

---

## 📖 1. Bối cảnh thực tế: Bài toán chiếc nút đỏ Duolingo

Trong sự kiện Super Bowl, thời điểm phát sóng quảng cáo 5 giây của Duolingo là ngẫu nhiên (phụ thuộc vào thời gian bóng chết, chấn thương). Đội marketing có ít nhất 2 người cùng cầm máy tính theo dõi truyền hình trực tiếp và sẵn sàng bấm nút kích hoạt.

### Hai rủi ro nghiêm trọng:
1. **Human Concurrency (Hai người cùng bấm):** Cả 2 người cùng thấy quảng cáo xuất hiện trên TV $\rightarrow$ cùng đập tay vào nút bấm trong cùng một mili-giây.
2. **Network Retry (Mạng chập chờn):** Trình duyệt gửi request đi, mạng lag 1 chút $\rightarrow$ trình duyệt hoặc gateway tự động gửi lại (Retry) request thứ 2.

### Hậu quả nếu hệ thống không có tính Idempotency:
* Hệ thống nạp 2 lần 4 triệu user $\rightarrow$ Đẩy **8 triệu tin nhắn** vào hàng đợi.
* Mỗi người dùng nhận **2 thông báo giống hệt nhau** trên điện thoại $\rightarrow$ Trải nghiệm cực kỳ tệ, bị người dùng chửi bới, report spam.
* Chi phí hạ tầng gửi push notification (APNS/FCM) và máy chủ tăng gấp đôi vô nghĩa.

---

## 📅 2. Kế hoạch học tập 5 Ngày (5-Day Master Plan)

```
 [Ngày 1] ────────► [Ngày 2] ────────► [Ngày 3] ────────► [Ngày 4] ────────► [Ngày 5]
Bản chất Race      In-Memory CAS      Database Locks     Distributed Lock    Stress Test
Condition & Bẫy   (AtomicBoolean /     (Unique Index /   & SQS FIFO Dedu-   "Chiếc nút đỏ"
Check-Then-Act     ConcurrentMap)      SELECT FOR UPDATE)   plication ID     & Phỏng vấn
```

---

### 📅 NGÀY 1: Bản chất Race Condition & Cạm bẫy kinh điển "Check-Then-Act"
* **Mục tiêu:** Hiểu tại sao 90% dev CRUD viết code kiểm tra trùng lặp nhưng hệ thống vẫn bị gửi 2 lần khi có 2 người bấm nút cùng lúc.
* **Kiến thức cốt lõi:**
  * Race Condition (Tranh chấp tài nguyên) trong Web Server đa luồng (Tomcat thread pool).
  * Cạm bẫy **"Check-Then-Act" (Kiểm tra rồi mới hành động)**:
    ```java
    // ❌ CẠM BẪY CHẾT NGƯỜI CỦA DEV CRUD:
    if (!campaignRepo.existsByCode("SUPERBOWL_2024")) {
        // ⚠️ Khoảng trống vài mili-giây ở đây đủ cho 20 luồng khác cùng lọt qua if!
        campaignRepo.save(new Execution("SUPERBOWL_2024", ...));
        sendPushNotifications();
    }
    ```
* **Thực hành:** Viết `NaiveTriggerService`, dùng nhiều luồng bắn vào cùng 1 mili-giây để tận mắt chứng kiến chiến dịch bị kích hoạt 2-3 lần.
* **Phỏng vấn:** *"Check-Then-Act là gì? Tại sao `existsBy()` không thể bảo vệ hệ thống trước Race Condition?"*

---

### 📅 NGÀY 2: Khóa In-Memory Siêu Tốc & Nguyên lý CPU CAS (Compare-And-Swap)
* **Mục tiêu:** Xây dựng tầng phòng ngự đầu tiên ngay tại RAM/CPU, chặn đứng request thứ 2 trong thời gian **vài nanogiây** mà không cần đụng vào Database hay Network.
* **Kiến thức cốt lõi:**
  * Tại sao không nên dùng `synchronized` method (nó làm khóa toàn bộ luồng, biến hệ thống thành tuần tự, tụt throughput thê thảm)?
  * Nguyên lý lệnh nguyên tử ở cấp độ phần cứng CPU: **CAS (Compare-And-Swap / lệnh Assembly `CMPXCHG`)**.
  * Vũ khí trong Java: `AtomicBoolean.compareAndSet(false, true)` và `ConcurrentHashMap.putIfAbsent()`.
  * Chỉ duy nhất 1 luồng đổi được cờ từ `false` sang `true` (Winner), các luồng còn lại bị từ chối ngay lập tức (`REJECTED`).
* **Thực hành:** Xây dựng `InMemoryLockService`, đo lường thời gian từ chối request trùng lặp (thường chỉ mất `< 0.01 ms`).
* **Phỏng vấn:** *"Sự khác biệt giữa `synchronized` (Blocking Lock) và `AtomicBoolean.compareAndSet` (Non-blocking Lock / CAS)?"*

---

### 📅 NGÀY 3: Tầng Database — Unique Constraints vs Database Locks
* **Mục tiêu:** Hiểu vì sao khóa In-Memory ở Ngày 2 là chưa đủ nếu hệ thống chạy nhiều server (Cluster/Auto-scaling), và cách Database đứng ra làm "trọng tài tối cao".
* **Kiến thức cốt lõi:**
  * **Database Unique Constraint:** Đưa ràng buộc `UNIQUE(campaign_code)` xuống cấp độ Engine của PostgreSQL. Cơ chế `INSERT ... ON CONFLICT DO NOTHING` hoặc bắt `DataIntegrityViolationException`.
  * **Pessimistic Locking (Khóa bi quan):** `SELECT ... FOR UPDATE` — khi nào nên dùng và cái giá phải trả về nghẽn Connection Pool (đã học ở Lab 1 Ngày 5).
  * **Optimistic Locking (Khóa lạc quan):** Cơ chế `@Version` trong JPA/Hibernate — hoạt động thế nào trong bài toán đồng thời.
* **Thực hành:** Viết `DatabaseLockService`, thử nghiệm 50 luồng chèn đồng thời vào bảng có ràng buộc `UNIQUE`, kiểm chứng đúng 1 luồng ghi nhận thành công và 49 luồng nhận `409 Conflict`.
* **Phỏng vấn:** *"Khi nào dùng Optimistic Lock (`@Version`) và khi nào dùng Unique Constraint để chống trùng lặp?"*

---

### 📅 NGÀY 4: Khóa Phân Tán (Distributed Lock) & SQS FIFO Deduplication
* **Mục tiêu:** Nắm vững giải pháp cấp độ Enterprise cho hệ thống Microservices & cách Duolingo giải quyết thực tế trên AWS.
* **Kiến thức cốt lõi:**
  * **Chuẩn thiết kế RESTful Idempotency:** Header `Idempotency-Key` (Client tự sinh UUID gửi kèm request).
  * **Khóa phân tán với Redis:** Lệnh `SET key value NX PX milliseconds` (SET if Not eXists với TTL). Cơ chế tự động giải phóng khóa chống Deadlock.
  * **Giải pháp thực tế của Duolingo: AWS SQS FIFO Message Deduplication ID:**
    * Khi bấm nút, request đẩy message vào SQS FIFO với `MessageDeduplicationId = "campaign_superbowl_2024"`.
    * Dù 2 người cùng bấm, SQS nhận 2 message cùng ID $\rightarrow$ SQS **tự động loại bỏ message thứ hai** trong cửa sổ 5 phút!
* **Thực hành:** Thiết kế logic mô phỏng cơ chế Idempotency Key và SQS FIFO Deduplication.
* **Phỏng vấn:** *"Nếu server bị crash ngay sau khi lấy Redis Lock nhưng chưa kịp nhả lock thì xử lý thế nào (Deadlock prevention)?"*

---

### 📅 NGÀY 5: Thực Chiến Stress Test "Chiếc Nút Đỏ" & Phỏng Vấn Senior
* **Mục tiêu:** Hoàn thiện giao diện "Nút đỏ hình vuông Duolingo", thực hiện Stress Test đồng thời và tổng kết bộ câu hỏi phỏng vấn Senior Concurrency.
* **Kiến thức cốt lõi:**
  * Giải mã hiện tượng **"Hitbox Trap" (Cạm bẫy vùng tương tác)**:
    * Nút hình tròn trên web thực chất có hitbox hình chữ nhật $\rightarrow$ bấm vào 4 góc màu trắng vẫn kích hoạt sự kiện click.
    * Duolingo đổi sang nút hình vuông để mắt thấy và hitbox trùng khít 100%, triệt tiêu lỗi thao tác người dùng.
  * Kỹ thuật dùng `CountDownLatch` giả lập 50 người cùng click chuột trong cùng 1 mili-giây.
* **Thực hành:**
  * Bấm nút đỏ trên giao diện Web: So sánh chế độ Naive (báo động đỏ: gửi trùng) vs Chế độ Idempotent (1 Winner, 49 Rejected).
  * Đóng gói kiến thức Lab 2 thành bullet points đưa vào CV / GitHub.
* **Phỏng vấn:** *"Thiết kế một API thanh toán/kích hoạt đảm bảo tính Idempotent 100% trong kiến trúc Microservices phân tán?"*

---

## 🎯 3. Ba Tầng Phòng Ngự Chống Trùng Lặp (Kiến Trúc Tổng Thể)

```
                            [REQUEST KÍCH HOẠT ĐẾN]
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 1: IN-MEMORY CAS (Tốc độ nanogiây - Chặn ngay tại CPU / RAM)           │
│ - Dùng ConcurrentHashMap.putIfAbsent(campaignCode, true)                     │
│ - Request thứ 2 cùng server bị chặn đứng sau < 0.01 ms!                     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Nếu vượt qua hoặc chạy multi-node)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 2: DISTRIBUTED LOCK / FIFO QUEUE (Tốc độ mili-giây - Cấp độ Cụm Server)│
│ - SQS FIFO Queue MessageDeduplicationId = campaignCode                       │
│ - Hoặc Redis SETNX: setIfAbsent(campaignCode, "LOCKED", 5, TimeUnit.MINUTES)│
│ - Đảm bảo chỉ 1 Worker duy nhất trong cụm ECS được xử lý                    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Hàng rào phòng thủ cuối cùng)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ TẦNG 3: DATABASE UNIQUE CONSTRAINT (Bền vững - Trọng tài tối cao)          │
│ - Bảng campaign_executions với cột UNIQUE(campaign_code)                    │
│ - Nếu có 2 luồng lọt qua mạng, Database ném UniqueViolationException        │
│ - Backend bắt ngoại lệ và trả về HTTP 409 Conflict                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Checklist hoàn thành Lab 2

- [ ] **Ngày 1:** Tự tay viết code tái hiện lỗi Race Condition của cạm bẫy Check-Then-Act.
- [ ] **Ngày 2:** Làm chủ nguyên lý CPU CAS, viết In-Memory Lock chặn request trùng trong nanogiây.
- [ ] **Ngày 3:** Làm chủ Unique Constraints, phân biệt Pessimistic vs Optimistic Locking.
- [ ] **Ngày 4:** Hiểu rõ Idempotency-Key pattern, Redis SETNX và cơ chế SQS FIFO của Duolingo.
- [ ] **Ngày 5:** Chạy Stress Test trên giao diện "Chiếc nút đỏ", trả lời trôi chảy các câu hỏi phỏng vấn.
