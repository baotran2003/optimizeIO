# 📅 NGÀY 3: BẢN CHẤT JDBC BATCHING & CÁI BẪY CỦA `saveAll()`

> **Mục tiêu:** Hiểu bản chất tầng mạng (Network Packets), cơ chế Batch Insert của Database, và lý do tại sao phương thức `saveAll()` kết hợp với `GenerationType.IDENTITY` lại trở thành "thảm họa hiệu năng" khiến chèn dữ liệu chậm hơn hàng chục lần.

---

## 1. Vấn đề thực tế: Bài toán sinh 100,000 bản ghi

Ở bước đầu tiên của Lab 1, khi bạn bấm nút `[Sinh dữ liệu mẫu]`, hệ thống chèn **100,000 dòng** vào PostgreSQL chỉ mất **~2 đến 3 giây**.

Nếu giao bài toán này cho một lập trình viên Spring Boot thông thường, 99% sẽ viết:
```java
List<UserDevice> list = ... // tạo 100,000 đối tượng
userDeviceRepository.saveAll(list); // ❌ CÁI BẪY CHẾT NGƯỜI
```
**Hậu quả:** Ứng dụng chạy mất từ **3 đến 5 phút**, CPU tăng vọt, quạt tản nhiệt quay ầm ĩ và có nguy cơ văng lỗi `OutOfMemoryError`!

Tại sao lại có sự chênh lệch khủng khiếp giữa 2 giây và 5 phút? Hãy cùng mổ xẻ mã nguồn bên dưới.

---

## 2. Cái bẫy số 1: Mã nguồn thật bên trong `saveAll()` của Spring Data JPA

Nhiều người lầm tưởng rằng gọi `saveAll()` là Spring Data JPA sẽ tự động chèn một lúc tất cả các bản ghi vào Database. 

Nhưng nếu mở mã nguồn class mặc định `SimpleJpaRepository.java` của Spring Data JPA ra xem, bạn sẽ thấy:

```java
// Mã nguồn bên trong org.springframework.data.jpa.repository.support.SimpleJpaRepository:
@Transactional
@Override
public <S extends T> List<S> saveAll(Iterable<S> entities) {
    Assert.notNull(entities, "Entities must not be null");

    List<S> result = new ArrayList<>();
    for (S entity : entities) {
        result.add(save(entity)); // 👈 BẢN CHẤT CHỈ LÀ VÒNG LẶP FOR GỌI save() TỪNG DÒNG!
    }
    return result;
}
```

👉 **Bản chất của `saveAll()` chỉ là một vòng lặp `for` gọi `save()` từng phần tử một!**

---

## 3. Cái bẫy số 2: Tại sao `GenerationType.IDENTITY` lại VÔ HIỆU HÓA Batching của Hibernate?

Nhìn vào Entity `UserDevice.java` của chúng ta:
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

Khi bạn chọn chiến lược `IDENTITY` (PostgreSQL dùng `BIGSERIAL`, MySQL dùng `AUTO_INCREMENT`), cơ chế hoạt động là: **Database là bên duy nhất quyết định và tự động tăng giá trị ID khi dòng đó được INSERT vào bảng.**

### Mâu thuẫn chết người với Hibernate:
1. Hibernate có một nguyên tắc thiết kế bất di bất dịch: **Mọi Entity khi được `save()` vào `Persistence Context` bắt buộc phải có ID ngay lập tức** (để dùng ID làm Key trong Map quản lý).
2. Nhưng nếu Hibernate gom 2,000 câu lệnh INSERT lại thành 1 lô (Batch) và chưa gửi đi, thì **lấy đâu ra ID** cho các Entity đó?
3. Để giải quyết mâu thuẫn này, Hibernate bắt buộc phải chọn giải pháp: **Vô hiệu hóa hoàn toàn cơ chế Batching (Batching Disabled)**.
4. Nó buộc phải thực thi câu lệnh `INSERT` ngay lập tức cho từng dòng một, chờ Database phản hồi ID vừa sinh ra, rồi mới chạy tiếp dòng tiếp theo!

👉 **Kết quả:** Dù bạn có cấu hình `hibernate.jdbc.batch_size=2000` trong properties, Hibernate vẫn âm thầm lờ đi và gửi từng dòng riêng lẻ qua mạng!

---

## 4. JDBC Batching giải quyết vấn đề như thế nào? (Bản chất tầng mạng)

Trong file `DataSeederService.java` và `BatchInsertExperimentService.java`, chúng ta dùng:
```java
jdbcTemplate.batchUpdate(sql, batchArgs); // batchArgs gom 2,000 bản ghi
```

### So sánh mô hình tầng mạng:

```
[1. Cách thường - JPA saveAll(): 2,000 Network Round-trips]:
Java App  ──(INSERT dòng 1)──►  PostgreSQL
Java App  ◄──(OK, ID = 1)────  PostgreSQL
Java App  ──(INSERT dòng 2)──►  PostgreSQL
Java App  ◄──(OK, ID = 2)────  PostgreSQL
... (Lặp lại 2,000 lần liên tục! Tốn 2,000 lần bắt tay qua mạng)


[2. JDBC batchUpdate(): Đúng 1 Network Round-trip]:
Java App  ──[ Gói tin TCP chứa toàn bộ 2,000 INSERTs ]──►  PostgreSQL
Java App  ◄───────────[ OK, Đã chèn 2,000 dòng ]────────  PostgreSQL
(Chỉ tốn đúng 1 lần gửi và 1 lần nhận qua mạng!)
```

* **Về phía Java:** Gom 2,000 bản ghi vào một mảng nhị phân thô, không tạo Managed Entity, không tạo Snapshot Dirty Checking.
* **Về phía Database:** Nhận 1 gói tin lớn, mở đúng 1 Transaction, ghi hàng loạt dữ liệu vào file nhật ký WAL (Write-Ahead Logging), tiết kiệm I/O ổ đĩa tối đa.

---

## 5. Bí kíp của PostgreSQL: Cờ `reWriteBatchedInserts=true`

Trong URL kết nối của project:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/test_dualingo?reWriteBatchedInserts=true
```

Mặc định, PostgreSQL JDBC Driver khi nhận batch sẽ gửi các câu lệnh tách nhau bởi dấu chấm phẩy:
```sql
INSERT INTO user_devices (...) VALUES (...); INSERT INTO user_devices (...) VALUES (...);
```

Khi bật cờ `reWriteBatchedInserts=true`, PostgreSQL Driver sẽ tự động viết lại (rewrite) thành **1 câu lệnh SQL đa giá trị (Multi-value insert)** duy nhất:
```sql
INSERT INTO user_devices (user_id, device_token, os_type, created_at) VALUES 
(1001, 'token_1', 'IOS', NOW()),
(1002, 'token_2', 'ANDROID', NOW()),
...
(3000, 'token_2000', 'IOS', NOW());
```
Việc này giúp PostgreSQL parse câu lệnh SQL đúng 1 lần và thực thi hàng loạt với tốc độ nhanh hơn thêm **3 đến 5 lần nữa**!

---

## 6. Hướng dẫn thực hành trên máy của bạn

1. Chạy lại ứng dụng Spring Boot trong IntelliJ IDEA.
2. Mở trình duyệt vào: 👉 **`http://localhost:8080`**
3. Kéo xuống phần **"NGÀY 3: Bản Chất Batching"**:
   * **Bấm nút đỏ:** `[Chèn 2k dòng bằng saveAll()]` $\rightarrow$ Quan sát thời gian mất khoảng bao nhiêu ms (thường từ **600ms - 2,000ms**).
   * **Bấm nút xanh:** `[Chèn 2k dòng bằng batchUpdate()]` $\rightarrow$ Quan sát thời gian (thường chỉ mất **15ms - 40ms**).
4. Quan sát số `ops/s` (số dòng chèn được trong 1 giây) để thấy sự chênh lệch **20 - 50 lần**.

---

## 7. Đúc kết phỏng vấn & Nguyên tắc thiết kế (Senior Q&A)

### Câu hỏi 1: Nếu dự án bắt buộc phải dùng JPA/Hibernate thì làm sao để bật được Batch Insert?
* **Trả lời:** Không được dùng `GenerationType.IDENTITY`. Bắt buộc phải chuyển sang:
  1. `GenerationType.SEQUENCE` kết hợp cấu hình `allocationSize` (ví dụ 50 hoặc 100). Khi đó Hibernate sẽ xin trước 1 dải 50-100 ID từ DB, tự gán ID trong RAM và gom batch gửi bình thường.
  2. Tự sinh ID tại tầng ứng dụng (dùng UUID, Snowflake ID, TSID) trước khi gọi `save()`.

### Câu hỏi 2: Khi nào nên chuyển hẳn sang `JdbcTemplate` thay vì cố gắng cấu hình Hibernate Batching?
* **Trả lời:** Trong các tác vụ **Data Seeding, Data Migration, Import file CSV/Excel lớn, hoặc Đồng bộ dữ liệu định kỳ**. `JdbcTemplate` loại bỏ 100% overhead của Hibernate, mang lại hiệu năng cao nhất và kiểm soát chính xác 100% câu lệnh SQL gửi xuống Database.
