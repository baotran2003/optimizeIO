# 🔴 LAB 2 — NGÀY 3: TẦNG DATABASE — UNIQUE CONSTRAINTS VS DATABASE LOCKS

---

## 📌 1. Mục tiêu học tập Ngày 3

* **Hiểu giới hạn của Tầng 1 (In-Memory CAS):** Tại sao khóa RAM ở Ngày 2 chỉ hoạt động trên **1 Node duy nhất (Single Instance)**. Khi hệ thống scale-out thành nhiều server (Cluster / Multi-nodes), tầng RAM hoàn toàn bất lực trước request đến 2 máy chủ khác nhau.
* **Database làm "Trọng tài tối cao" (Single Source of Truth):** Đưa cơ chế bảo vệ xuống tầng Database bền vững (PostgreSQL).
* **Phân biệt & Thực hành 3 cơ chế kiểm soát đồng thời tại Database:**
  1. **Database Unique Constraint (`UNIQUE INDEX`):** Đẩy việc kiểm tra xuống B-Tree index của DB engine. Bắt `DataIntegrityViolationException` / `DuplicateKeyException` hoặc dùng `INSERT ... ON CONFLICT DO NOTHING`.
  2. **Pessimistic Locking (Khóa bi quan):** `SELECT ... FOR UPDATE` — cơ chế giữ Row-Level Lock và cái giá phải trả về nghẽn Connection Pool (đã học ở Lab 1).
  3. **Optimistic Locking (Khóa lạc quan):** Cơ chế `@Version` trong JPA — hoạt động theo nguyên lý tương tự CAS ở tầng DB (chỉ commit nếu version chưa đổi).
* **Kết quả kỳ vọng:** Dù có 50 hay 100 luồng chạy đồng thời đâm thẳng xuống Database, **đúng 1 bản ghi duy nhất được tạo**, các luồng còn lại nhận lỗi trùng lặp (`409 Conflict`) mà không làm hỏng dữ liệu.

---

## 🔬 2. Kiến thức cốt lõi: 3 Vũ khí tầng Database

### 2.1. Vũ khí 1: Unique Constraint (`UNIQUE(campaign_code)`) — Lựa chọn số 1 cho Idempotency
* **Bản chất:** Tạo chỉ mục duy nhất (Unique B-Tree Index) trên cột `campaign_code`.
* Khi 2 luồng cùng thực hiện lệnh `INSERT`:
  * Luồng 1 ghi thành công vào B-Tree.
  * Luồng 2 vi phạm ràng buộc duy nhất $\rightarrow$ PostgreSQL ném lỗi mã `23505 (unique_violation)`.
* **Ưu điểm:**
  * Cực kỳ nhẹ, không cần duy trì transaction lock dài hạn.
  * Là chốt chặn an toàn cuối cùng (**Last Line of Defense**) không thể bị vượt qua.
* **Nhược điểm:** Phải bắt exception hoặc dùng cú pháp SQL mở rộng (`ON CONFLICT`).

```sql
-- DDL chốt chặn
CREATE TABLE campaign_executions (
    id BIGSERIAL PRIMARY KEY,
    campaign_code VARCHAR(64) NOT NULL UNIQUE,
    triggered_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### 2.2. Vũ khí 2: Pessimistic Locking (`SELECT ... FOR UPDATE`)
* **Bản chất:** Khóa dòng (Row-level lock) ngay khi đọc, ép các transaction khác phải chờ (`BLOCKED`) cho đến khi transaction hiện tại `COMMIT` hoặc `ROLLBACK`.
* **Cảnh báo:** Nếu query giữ lock lâu hoặc có nhiều luồng cùng tranh chấp, toàn bộ Connection trong Pool (HikariCP) sẽ bị kẹt cứng (Connection Pool Starvation).

### 2.3. Vũ khí 3: Optimistic Locking (`@Version`)
* **Bản chất:** Không khóa dòng nào ở DB, chỉ thêm một cột `version INT`.
* Khi update: `UPDATE table SET ..., version = version + 1 WHERE id = ? AND version = ?`.
* Nếu có luồng khác đã update trước $\rightarrow$ số dòng update = 0 $\rightarrow$ JPA ném `OptimisticLockException`.
* **Phù hợp:** Trường hợp **Read-Heavy** (đọc nhiều, ghi ít, tỉ lệ xung đột thấp). Không tối ưu cho trường hợp 60 luồng cùng đập vào 1 lúc (vì 59 luồng sẽ văng lỗi ngay lập tức).

---

## 🛠️ 3. Kế hoạch triển khai Code Ngày 3

### Bước 1: Tạo Entity & Bảng Database
* Tạo bảng `campaign_executions` với ràng buộc `UNIQUE(campaign_code)`.
* Tạo Entity JPA `CampaignExecution.java` và Repository `CampaignExecutionRepository.java`.

### Bước 2: Tạo Service xử lý Database Lock
* **File:** `src/main/java/com/example/optimizeIO/lab2/service/day3/DatabaseLockService.java`
* Viết hàm `triggerWithUniqueConstraint(int concurrentUsers)`:
  * 50 luồng đồng thời `INSERT` bản ghi với cùng mã `SUPERBOWL_2024`.
  * Luồng thành công $\rightarrow$ Winner (HTTP 200).
  * Các luồng dính `DataIntegrityViolationException` $\rightarrow$ Rejected / 409 Conflict.
  * Đo lường thời gian DB xử lý xung đột (thường mất 2ms - 5ms, so sánh với nanogiây của CAS Ngày 2).

### Bước 3: Tạo Endpoint kiểm chứng vào Controller
* **Endpoint:** `GET /api/lab2/day3/db-unique-constraint?users=50`
* Kiểm tra:
  * `successCount == 1`.
  * `rejectedCount == 49`.
  * Số lượng bản ghi thực tế trong bảng DB: `SELECT count(*) FROM campaign_executions WHERE campaign_code = 'SUPERBOWL_2024'` $\rightarrow$ **Đúng 1 dòng**.

---

## 📋 4. Checklist hoàn thành Ngày 3

- [x] Tạo file kế hoạch `docs/lab2-idempotency&concurrencyControl/day-03-database-locks.md`.
- [x] Tạo bảng DB / Entity `CampaignExecution` có ràng buộc `UNIQUE`.
- [x] Tự tay code `DatabaseLockService.java` (Xử lý bắt ngoại lệ `DataIntegrityViolationException`).
- [x] Tạo endpoint `GET /api/lab2/day3/db-unique-constraint` trong Controller.
- [x] Chạy stress test 50 user đồng thời, đối soát DB chỉ có duy nhất 1 bản ghi.
- [x] Trả lời câu hỏi phỏng vấn Senior: *"Tại sao In-Memory CAS nhanh hơn nhưng trong kiến trúc Microservices/Cluster bắt buộc phải có Unique Constraint ở Database?"*.

---

## 🎯 5. Trả lời phỏng vấn Senior: "Tại sao In-Memory CAS nhanh hơn nhưng trong Microservices/Cluster bắt buộc phải có Unique Constraint ở Database?"

### 1. Giới hạn vật lý của In-Memory CAS trong hệ thống phân tán
* **In-Memory CAS (Ngày 2):** Hoạt động ở cấp độ phần cứng CPU và bộ nhớ RAM của **1 tiến trình JVM duy nhất**.
* Khi hệ thống scale-out thành nhiều máy chủ (Cluster / Multi-instances / Auto-scaling):
  * **Server A** (chạy trên Node 1) có vùng RAM riêng.
  * **Server B** (chạy trên Node 2) có vùng RAM riêng.
  * Request 1 đến Server A $\rightarrow$ CAS trên Server A đổi `false -> true` thành công.
  * Request 2 đồng thời đến Server B $\rightarrow$ Server B chưa từng thấy Request 1, RAM của Server B vẫn là `false` $\rightarrow$ CAS trên Server B **tiếp tục thành công!**
* 👉 **Hệ quả:** Nếu chỉ dùng In-Memory CAS, chiến dịch vẫn bị kích hoạt 2 lần trên 2 server khác nhau. Do đó, **Database chính là Single Source of Truth (Trọng tài tối cao duy nhất)** mà tất cả các server đều phải tuân theo.

---

### 2. Giải mã Concept: Optimistic Locking (`@Version`)
* **Ý nghĩa tên gọi "Lạc quan":** Không khóa cứng dòng dữ liệu nào lúc đọc (`SELECT`). Tin rằng xung đột hiếm khi xảy ra. Chỉ đến lúc ghi (`UPDATE/COMMIT`) mới kiểm tra xem dữ liệu có bị ai sửa trộm hay không.
* **Cơ chế hoạt động:**
  1. Thêm cột `version INT DEFAULT 0` trong DB và gắn `@Version` trên Entity JPA.
  2. Khi Luồng A và Luồng B cùng đọc record `{id: 1, version: 1}`.
  3. Luồng A commit trước: Hibernate thực thi SQL:
     ```sql
     UPDATE campaign_executions 
     SET status = 'TRIGGERED', version = 2 
     WHERE id = 1 AND version = 1;
     ```
     $\rightarrow$ Cập nhật thành công 1 dòng (`rows_updated = 1`), version trong DB nhảy lên 2.
  4. Luồng B đến sau, cũng chạy câu lệnh `WHERE id = 1 AND version = 1`:
     $\rightarrow$ Nhưng lúc này `version` trong DB đã là 2 $\rightarrow$ Không khớp dòng nào (`rows_updated = 0`).
  5. Hibernate thấy `rows_updated == 0` $\rightarrow$ Ném ngay ngoại lệ **`OptimisticLockException`**.
* 👉 **Bản chất của `@Version` chính là lệnh CAS (Compare-And-Swap) ở tầng Database**, thực hiện qua mệnh đề `WHERE version = ?`.

---

### 3. Bảng so sánh 3 cơ chế đồng thời tại Database

| Tiêu chí | Unique Constraint (`UNIQUE INDEX`) | Pessimistic Lock (`SELECT FOR UPDATE`) | Optimistic Lock (`@Version`) |
| :--- | :--- | :--- | :--- |
| **Bản chất** | Kiểm tra trùng trên B-Tree Index lúc `INSERT`. | Khóa dòng vật lý tại DB. Luồng khác bị `BLOCKED`. | Không khóa lúc đọc. Kiểm tra `version` lúc `UPDATE`. |
| **Phù hợp nhất với** | **Tạo mới tài nguyên (INSERT)**<br>(vd: Kích hoạt campaign, tạo user, mã đơn hàng). | **Thao tác UPDATE nhạy cảm, ít luồng**<br>(vd: Trừ tiền tài khoản, giao dịch ngân hàng). | **Đọc nhiều - Ghi ít (Read-Heavy)**<br>(vd: Sửa bài viết, cập nhật profile). |
| **Khi 50 luồng cùng đập vào** | 1 luồng INSERT thành công, 49 luồng văng lỗi `DuplicateKey` ngay lập tức (Fail-fast). | 1 luồng giữ lock, **49 luồng xếp hàng chờ**, làm cạn kiệt Connection Pool (HikariCP)! | 1 luồng UPDATE thành công, 49 luồng cùng văng `OptimisticLockException`. |
| **Đánh giá cho Lab 2** | 🏆 **LỰA CHỌN TỐI ƯU NHẤT** (Bảo vệ thao tác tạo mới chiến dịch). | ❌ Không nên dùng (Nguy cơ nghẽn Connection Pool cao). | ⚠️ Không dùng được cho thao tác `INSERT` lần đầu (vì chưa có record để có `version`). |

---

### 💡 Câu chốt phỏng vấn (Senior Interview Punchline):
> *"In-Memory CAS cực nhanh trong nanosecond nhưng chỉ giới hạn trong 1 JVM. Khi chạy Cluster nhiều node, Database bắt buộc phải là Single Source of Truth.*
>
> *Đối với bài toán chiếc nút đỏ (kích hoạt chiến dịch mới), **Unique Constraint** là giải pháp tối ưu nhất vì nó chốt chặn thao tác `INSERT` ngay tại B-Tree Index và fail-fast. Ta không dùng **Pessimistic Lock** vì nó khóa luồng gây cạn kiệt Connection Pool, và cũng không dùng **Optimistic Lock (`@Version`)** vì nó chỉ hoạt động khi `UPDATE` dữ liệu đã tồn tại, không ngăn được 2 luồng cùng `INSERT` mới."*

