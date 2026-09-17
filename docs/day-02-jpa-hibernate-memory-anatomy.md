# 📅 NGÀY 2: GIẢI PHẪU BỘ NHỚ (MEMORY ANATOMY) - MỔ XẺ "TỘI ĐỒ" JPA/HIBERNATE

> **Mục tiêu:** Hiểu rõ tận gốc rễ cơ chế quản lý trạng thái của Hibernate (First-Level Cache, Dirty Checking Snapshot) và lý do tại sao JPA Entity lại biến thành "kẻ sát nhân bộ nhớ" khi xử lý dữ liệu lớn (100k dòng).

---

## 1. Sự ngộ nhận của lập trình viên CRUD

Một bạn dev sau khi học Ngày 1 sẽ nghĩ:
> *"Em không lặp 100k lần nữa, em chỉ gọi đúng 1 câu: `userDeviceRepository.findAll()`. Chỉ có đúng **1 câu lệnh SQL** gửi xuống DB, chỉ tốn **1 lần Round-trip** mạng. Tại sao nó vẫn mất gần 1 giây và ngốn hàng trăm MB RAM?"*

Câu trả lời nằm ở: **Những gì Hibernate âm thầm làm sau lưng bạn trên bộ nhớ JVM Heap!**

---

## 2. Ba "Tội đồ" sau lưng bạn khi Hibernate chạy `findAll()`

```
PostgreSQL (Dữ liệu nhị phân thô)
             │
             ▼
   [1. Entity Hydration]     ──► Bùng nổ: 1 dòng DB biến thành 5-8 Objects con trong Java Heap
             │                   100,000 dòng = ~700,000 - 800,000 Java Objects!
             ▼
   [2. Persistence Context]  ──► Bắt giữ: 100,000 Objects bị lưu chặt trong First-Level Cache Map
             │                   Không thể giải phóng cho đến khi Transaction kết thúc!
             ▼
   [3. Dirty Checking]       ──► Nhân đôi RAM: Hibernate tạo thêm 100,000 bản Snapshot (Clone)
                                 để theo dõi xem bạn có sửa trường nào không!
```

### Tội đồ 1: Bùng nổ số lượng Object trên Heap (Entity Hydration)
Một dòng trong DB chỉ tốn vài chục bytes. Nhưng khi vào Hibernate:
* 1 `UserDevice` (Object bao ngoài)
* 1 `Long` (id)
* 1 `Long` (userId)
* 1 `String` (deviceToken) + 1 mảng `byte[]` nội bộ bên trong
* 1 `String` (osType)
* 1 `LocalDateTime` (createdAt) + 1 `LocalDate` + 1 `LocalTime` bên trong

👉 **100,000 dòng DB sinh ra gần 800,000 Java Objects** được cấp phát cấp tốc trên bộ nhớ Heap!

### Tội đồ 2: First-Level Cache (Persistence Context)
Hibernate được thiết kế cho các nghiệp vụ OLTP nhỏ (sửa 1 hóa đơn, thêm 1 user). Nó có một vùng nhớ gọi là Persistence Context. Mọi Entity được load ra đều bị Hibernate "bắt cóc" lưu vào một `Map` nội bộ của `EntityManager`. Bộ gom rác (Garbage Collector - GC) **không thể thu hồi** bộ nhớ này cho đến khi transaction kết thúc hoàn toàn.

### Tội đồ 3: Dirty Checking Snapshot (Bộ nhớ bị nhân đôi)
Để bạn có thể sửa data mà không cần gọi `save()` (chỉ cần `device.setOs("IOS")` là Hibernate tự `UPDATE`), Hibernate âm thầm nhân bản một bản sao (Snapshot) của toàn bộ 100,000 Entities lúc vừa load lên để làm mốc so sánh khi kết thúc Transaction.
👉 **Tức là bộ nhớ bị nhân đôi một cách hoàn toàn lãng phí** trong các nghiệp vụ chỉ cần đọc (Read-only)!

---

## 3. Số liệu thực nghiệm đã đo đạc trực tiếp trên máy của bạn

| Tiêu chí | 1. JPA `findAll()` (Managed Entity) | 2. JDBC + Java 17 Record | Kết luận |
| :--- | :--- | :--- | :--- |
| **RAM tiêu thụ** | **97.12 MB** | **35.92 MB** | JDBC tiết kiệm gần **3 LẦN BỘ NHỚ** |
| **Thời gian chạy** | **854 ms** | **110 ms** | JDBC nhanh hơn gần **8 LẦN** |
| **Bản chất Object** | Managed Entity (Có Proxy, Metadata, Snapshot) | Plain Data Carrier (Unmanaged, Read-only) | Dùng xong là GC dọn sạch ngay |
| **Dirty Checking** | Bị quét và so sánh 100,000 dòng | **0% CPU** (Không có Dirty Checking) | Giải phóng CPU hoàn toàn |

---

## 4. Thí nghiệm "Ép tử" JVM (`-Xmx150m`)

Khi giới hạn RAM của ứng dụng bằng VM Option `-Xmx150m`:
* **Cách JDBC + Record:** Chỉ chiếm ~35MB RAM $\rightarrow$ Ứng dụng vẫn chạy mượt mà, ổn định.
* **Cách JPA `findAll()`:** Ngốn 97MB + RAM cơ bản của Spring Boot $\rightarrow$ Tràn bộ nhớ Heap $\rightarrow$ Sập ứng dụng ngay lập tức với lỗi:
  ```text
  java.lang.OutOfMemoryError: Java heap space
  ```

---

## 5. Quy tắc sống còn khi đi làm (Senior Best Practices)

| Nghiệp vụ | Có dùng JPA Entity không? | Dùng giải pháp gì thay thế? |
| :--- | :---: | :--- |
| CRUD cơ bản (thêm/sửa 1 đơn hàng, xem chi tiết 1 user) |  **NÊN DÙNG** | JPA Entity thông thường (tận dụng validation, cascade, dirty checking tiện lợi). |
| Đọc dữ liệu lớn (Batch, Báo cáo, Export Excel/CSV > 10,000 dòng) | ❌ **TUYỆT ĐỐI KHÔNG** | Dùng **Spring Data JPA Projection** (Interface/DTO), **JdbcTemplate** hoặc nạp vào **In-Memory Record**. |
| High-Throughput Flash Event (Duolingo 4M noti trong 5s) | ❌ **TUYỆT ĐỐI KHÔNG** | Nạp sẵn vào **Local In-Memory Cache** từ trước giờ G, loại bỏ 100% truy vấn tới DB. |
---

### 🧠 Câu hỏi tự kiểm tra

> **Tại sao trong các tác vụ đọc dữ liệu lớn (Batch/Report), người ta khuyên KHÔNG NÊN dùng JPA Entity mà nên dùng DTO Projection hoặc JDBC thuần?**

**1. Managed Entity & First-Level Cache**

Hibernate phải giữ 100k object trong Persistence Context.

**2. Dirty Checking**

Hibernate tốn CPU để theo dõi snapshot xem có trường nào bị sửa đổi hay không.

> 💡 **Nên dùng DTO Projection hoặc JDBC thuần** vì chúng tạo ra các object dữ liệu nhẹ hơn và tránh phần lớn overhead quản lý Entity của Hibernate.

### 🔍 Thực tế

Cả **JPA Entity** lẫn **DTO/JDBC** đều phải gửi query xuống **Database** để kéo dữ liệu lên.

### ⚡ Khác biệt thật sự nằm ở chỗ

- **JPA Entity:** Giống như một chiếc xe bọc thép cồng kềnh có gắn camera giám sát 24/7 (**Dirty Checking**) + kho chứa đồ (**First-Level Cache**).

- **DTO Projection / JDBC Record:** Giống như một chiếc xe máy chở đúng gói hàng bạn cần. Nó là đối tượng dữ liệu thuần (**Unmanaged / Read-only**), không bị Hibernate quản lý như Entity.

> **💡 Quy tắc cần nhớ:**  
> **DTO/JDBC không làm Database nhanh hơn.**  
> Chúng giúp giảm **overhead ở phía Java/Hibernate** khi phải xử lý một lượng lớn dữ liệu.