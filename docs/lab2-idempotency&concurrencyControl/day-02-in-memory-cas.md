# 🔴 LAB 2 — NGÀY 2: KHÓA IN-MEMORY SIÊU TỐC & NGUYÊN LÝ CPU CAS (COMPARE-AND-SWAP)

---

## 📌 1. Mục tiêu học tập Ngày 2

* **Hiểu bản chất cơ chế CAS (Compare-And-Swap):** Lệnh nguyên tử ở cấp độ phần cứng CPU giúp thay đổi trạng thái mà **không cần dùng khóa chặn (Non-blocking / Lock-free)**.
* **Biết vì sao KHÔNG nên dùng `synchronized`:** Hiểu cái giá phải trả về hiệu năng khi chuyển thread sang trạng thái `BLOCKED` và chi phí Context Switching của hệ điều hành.
* **Xây dựng tầng phòng ngự đầu tiên (Tầng RAM):** Dùng `AtomicBoolean.compareAndSet()` và `ConcurrentHashMap.putIfAbsent()` để biến 2 bước rời rạc **Check-Then-Act** thành **1 thao tác duy nhất (Atomic Operation)**.
* **Kết quả kỳ vọng:** Khi 60 request bắn đồng thời vào:
  * Đúng **1 Winner duy nhất** kích hoạt thành công (`successCount = 1`).
  * **59 request còn lại** bị từ chối ngay lập tức (`rejectedCount = 59`).
  * Thời gian ra quyết định từ chối: **siêu tốc (< 0.01 ms / vài chục nanogiây)**.

---

## 🔬 2. Kiến thức cốt lõi: Tại sao CAS lại "ăn đứt" Synchronized?

### 2.1. Cạm bẫy của `synchronized` (Blocking Lock)
Khi bạn gắn `synchronized` vào method hoặc block code:
1. Luồng đầu tiên lấy được Monitor Lock.
2. 59 luồng còn lại bị hệ điều hành (OS) đình chỉ, chuyển sang trạng thái **`BLOCKED`**.
3. Khi luồng đầu tiên chạy xong, OS phải đánh thức (wake-up) luồng tiếp theo $\rightarrow$ Tốn chi phí **Thread Context Switch (1-5 micro-giây cho mỗi lần chuyển)**.
4. Nó biến hệ thống xử lý song song thành **tuần tự (Serial Execution)** $\rightarrow$ Throughput (thông lượng) tụt dốc thê thảm.

### 2.2. Vũ khí CAS (Compare-And-Swap / Lock-Free)
* Ở cấp độ phần cứng, CPU (x86/ARM) có lệnh Assembly chuyên biệt: **`CMPXCHG` (Compare and Exchange)**.
* Nguyên lý: *"Chỉ thay đổi giá trị từ A thành B nếu giá trị hiện tại trong RAM đúng bằng A. Tất cả diễn ra trong đúng 1 chu kỳ xung nhịp phần cứng!"*

```
          [RAM đang lưu giá trị: false]
                        │
       ┌────────────────┴────────────────┐
       ▼                                 ▼
[Luồng 1 gọi CAS(false -> true)]   [Luồng 2 gọi CAS(false -> true)]
       │                                 │
So sánh: RAM đang là false?        So sánh: RAM đã bị đổi thành true!
  -> ĐÚNG! Đổi sang true.            -> SAI! Không được đổi.
  -> Trả về: true (WINNER 🏆)        -> Trả về: false (REJECTED ❌)
```

👉 **Không có luồng nào phải ngủ (`BLOCKED`)!** Luồng thua cuộc lập tức nhận về kết quả `false` và thoát ra ngay trong thời gian tính bằng nanogiây.

---

## 🛠️ 3. Kế hoạch triển khai Code Ngày 2

### Bước 1: Tạo Service xử lý khóa In-Memory
* **File:** `src/main/java/com/example/optimizeIO/lab2/service/InMemoryLockService.java`
* **Vũ khí sử dụng:**
  * `AtomicBoolean` cho kịch bản 1 chiến dịch đơn lẻ (`compareAndSet(false, true)`).
  * `ConcurrentHashMap<String, Boolean>` cho kịch bản hệ thống phục vụ nhiều chiến dịch khác nhau (`putIfAbsent(campaignCode, true)`).
* **Metrics đo lường:**
  * `successCount`: Phải tuyệt đối bằng 1.
  * `rejectedCount`: Phải bằng `N - 1`.
  * `rejectionLatencyNs`: Thời gian trung bình để từ chối các request trùng lặp (tính bằng nanosecond).

### Bước 2: Thêm Endpoint kiểm chứng vào Controller
* **Endpoint:** `GET /api/lab2/day2/in-memory-cas?users=60`
* So sánh trực tiếp kết quả với Ngày 1 để thấy sự khác biệt một trời một vực:
  * Ngày 1 (Check-Then-Act): `successCount = 4` (LỖI).
  * Ngày 2 (CAS Atomic): `successCount = 1`, `rejectedCount = 59` (CHUẨN XÁC).

---

## 📋 4. Checklist hoàn thành Ngày 2

- [x] Tạo file kế hoạch `docs/lab2-idempotency&concurrencyControl/day-02-in-memory-cas.md`.
- [x] Tự tay code `InMemoryLockService.java` (Điền các TODO logic CAS).
- [x] Tạo endpoint `GET /api/lab2/day2/in-memory-cas` trong Controller.
- [x] Chạy stress test với 60 user đồng thời, đối soát `successCount == 1`.
- [x] Tự trả lời được câu hỏi phỏng vấn: *"Tại sao CAS không cần lock mà vẫn đảm bảo tính Thread-Safe?"*.

---

## 🎯 5. Trả lời phỏng vấn Senior: "Tại sao CAS không cần lock mà vẫn đảm bảo tính Thread-Safe?"

Code minh họa:
```java
boolean isWinner = campaignLock.compareAndSet(false, true);
```

### 1. Bản chất Hardware: Tập lệnh nguyên tử đơn nhất (Atomic Instruction)
Ở tầng mã máy thông thường, thao tác cập nhật biến luôn bị phân rã làm 3 giai đoạn: `Read -> Compare -> Write` (tạo ra khoảng trống cho Race Condition).

CAS giải quyết tận gốc bằng cách gom toàn bộ chu trình trên thành **1 lệnh mã máy duy nhất**:
* **Kiến trúc x86 / x64:** Lệnh `LOCK CMPXCHG` (Compare and Exchange có tiền tố LOCK bus/cache).
* **Kiến trúc ARM:** Cặp lệnh liên kết tải / lưu có điều kiện `LDREX / STREX` (Load-Linked / Store-Conditional).

Khi lệnh này được kích hoạt, CPU phần cứng đảm bảo **không có bất kỳ lõi (Core) nào khác** có thể can thiệp, đọc hay ghi đè vào cùng một ô nhớ trong suốt chu kỳ thực thi.

---

### 2. Cách CPU đồng bộ bộ nhớ: Cache Coherence & Giao thức MESI
Trong CPU đa lõi (Multi-core), mỗi Core có L1/L2 Cache riêng. Để các Core không nhìn thấy dữ liệu rác/lệch pha, CPU sử dụng giao thức **MESI** với 4 trạng thái cache line:
* **M (Modified):** Dữ liệu đã thay đổi tại Core này, là mới nhất và chưa flush về RAM.
* **E (Exclusive):** Core đang giữ độc quyền, đồng bộ với RAM.
* **S (Shared):** Nhiều Core cùng giữ bản sao chỉ đọc (Read-only).
* **I (Invalid):** Dữ liệu trong Cache line này đã bị vô hiệu hóa / lỗi thời.

**Cơ chế thực thi khi Thread A (Core 1) gọi `compareAndSet(false, true)`:**
1. **Gửi tín hiệu RFO (Request For Ownership):** Core 1 broadcast yêu cầu độc quyền trên Cache Bus.
2. **Invalidate Cache của các Core khác:** Toàn bộ các Core khác (Core 2, 3...) đang chứa cache line chứa biến `campaignLock` lập tức chuyển trạng thái sang **`I (Invalid)`**.
3. **Thực thi nguyên tử:** Core 1 nắm quyền độc quyền **`M (Modified)`**, kiểm tra giá trị cũ và ghi đè giá trị mới `true` chỉ trong **1 xung nhịp CPU**.
4. **Các Core khác nhận thất bại ngay lập tức:** Khi Thread B (chạy trên Core 2) cố gắng đọc/so sánh, nó thấy cache line là `Invalid` $\rightarrow$ buộc phải nạp lại giá trị mới (`true`) $\rightarrow$ CAS thất bại trả về `false` trong vòng **nanogiây**.

---

### 3. Bảng so sánh tổng kết: `synchronized` vs `CAS (compareAndSet)`

| Tiêu chí | `synchronized` (Blocking Lock) | CAS `compareAndSet` (Non-blocking / Lock-Free) |
| :--- | :--- | :--- |
| **Cơ chế hoạt động** | Dùng Monitor Lock của OS. Luồng thua bị đình chỉ (`BLOCKED`). | Dùng lệnh phần cứng `CMPXCHG`. Luồng thua nhận `false` ngay. |
| **Độ trễ xử lý (Latency)** | **1 - 5 micro-giây** (Tốn công Context Switch, sleep & wake up). | **1 - 3 nano-giây** (Cực nhanh, chỉ vài chu kỳ CPU). |
| **Throughput (Thông lượng)** | Thấp khi tải cao vì biến hệ thống thành tuần tự. | Cực cao, các luồng từ chối có thể giải phóng tài nguyên lập tức. |
| **Nguy cơ Deadlock** | Có (nếu lồng nhiều lock không đúng thứ tự). | **Không bao giờ xảy ra Deadlock** (Lock-Free). |

> 💡 **Câu chốt phỏng vấn:** *"CAS an toàn vì ở cấp phần cứng, tiền tố `LOCK` kết hợp giao thức `MESI` trao quyền sở hữu độc quyền ô nhớ cho đúng 1 Core tại 1 chu kỳ máy. Toàn bộ thao tác kiểm tra và ghi đè diễn ra atomic mà không cần hệ điều hành can thiệp đình chỉ thread như `synchronized`."*

