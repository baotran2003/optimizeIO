# 📅 NGÀY 4: GIẢI PHẪU BỘ NHỚ JVM & JAVA 17 RECORD

> **Mục tiêu:** Hiểu sâu về cấu tạo vật lý của 1 Object trong Java Heap (Object Memory Layout), tại sao Java 17 Record lại tiết kiệm bộ nhớ vượt trội, và so sánh chi phí RAM / hiệu năng của 3 cấu trúc dữ liệu: `ArrayList`, `HashMap`, `ConcurrentHashMap`.

---

## 1. Bản chất một Object trong Java Heap (Object Memory Layout)

Nhiều lập trình viên nghĩ rằng: một class chỉ có 1 trường `long userId` thì nó sẽ tốn đúng 8 bytes trong RAM. **Thực tế hoàn toàn không phải vậy!**

Mọi đối tượng (Object) trong Java khi nằm trên bộ nhớ Heap đều có cấu trúc 3 phần:

```
┌─────────────────────────────────────────────────────────────┐
│                    JAVA OBJECT LAYOUT                       │
├──────────────────────────────┬──────────────────────────────┤
│ 1. Mark Word (8 bytes)       │ Hashcode, GC Age, Lock Flags │ ◄── Object Header
│ 2. Klass Word (4 bytes)      │ Trỏ tới Class trong Metaspace│     (12 - 16 bytes)
├──────────────────────────────┼──────────────────────────────┤
│ 3. Instance Data             │ Các trường dữ liệu thực tế   │
│    - primitives (long, int)  │ (8 bytes, 4 bytes...)        │
│    - reference pointers      │ Con trỏ trỏ tới Object khác  │
├──────────────────────────────┼──────────────────────────────┤
│ 4. Padding (0 - 7 bytes)     │ Đệm cho đủ bội số của 8 bytes│
└──────────────────────────────┴──────────────────────────────┘
```

### Chi tiết các thành phần:
1. **Object Header (12 - 16 bytes):**
   * **Mark Word (8 bytes trên JVM 64-bit):** Lưu thông tin nhận dạng (identity hash code), tuổi của object để GC dọn dẹp (GC Age), và trạng thái khóa luồng (Lock status).
   * **Klass Word (4 bytes khi bật Compressed OOPs):** Con trỏ trỏ tới định nghĩa của Class trong vùng nhớ Metaspace.
2. **Instance Data:** Dữ liệu thật sự của các biến (ví dụ: `long` tốn 8 bytes, `int` tốn 4 bytes, con trỏ tham chiếu tới String tốn 4 bytes).
3. **Padding (Đệm bộ nhớ):** JVM yêu cầu mọi Object phải có kích thước là **bội số của 8 bytes** (8, 16, 24, 32...). Nếu dữ liệu của bạn chỉ tốn 21 bytes, JVM sẽ tự động đệm thêm 3 bytes rỗng cho tròn 24 bytes để CPU có thể đọc dữ liệu trong 1 chu kỳ xung nhịp.

👉 **Kết luận 1:** Trong Java, **một Object rỗng không chứa gì cũng đã tốn tối thiểu 16 bytes RAM**!

---

## 2. So sánh: JPA Entity vs Java 17 Record

### Tại sao JPA Entity lại "nặng nề"?
Một Entity như `UserDevice.java`:
* Chứa Object Header (16B).
* Chứa các wrapper objects (`Long id`, `Long userId`, `String deviceToken`, `LocalDateTime createdAt`).
* Bị Hibernate bao bọc bởi ByteBuddy Proxy.
* Trong `Persistence Context`, mỗi Entity đi kèm một đối tượng `EntityEntry` chứa snapshot, trạng thái quản lý, dirty checking metadata.
* 👉 **Chi phí thực tế: ~160 đến 240 bytes cho MỖI Entity!**

### Tại sao Java 17 Record (`DeviceCacheItem`) lại siêu nhẹ?
```java
public record DeviceCacheItem(long userId, String deviceToken, String osType) {}
```
* **Immutable Data Carrier:** Record sinh ra thuần túy để chứa dữ liệu, không có logic, không có setter.
* `userId` dùng `long` nguyên thủy (primitive) $\rightarrow$ tốn đúng 8 bytes, không tốn thêm 16 bytes của Object `Long`.
* Không có metadata của Hibernate, không có Proxy.
* 👉 **Chi phí thực tế: Chỉ khoảng ~40 đến 56 bytes!** (Tiết kiệm gấp 3 - 4 lần bộ nhớ).

---

## 3. So sánh 3 Cấu Trúc Dữ Liệu Lưu Trữ In-Memory

Khi nạp 100,000 bản ghi vào RAM, chúng ta nên dùng cấu trúc nào?

```
       [1. ArrayList<Record>]              [2. HashMap<Long, String>]
   ┌───┬───┬───┬───┬───┬───┐              ┌────────┐      ┌────────┐
   │ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │              │ Bucket │ ──►  │ Node   │ (Key, Value,
   └───┴───┴───┴───┴───┴───┘              └────────┘      │ Next)  │  Hash, Next)
   Mảng liên tục, nạp 1 lần vào CPU       Rải rác trong Heap, tốn thêm ~32B/Node
```

### 1. `ArrayList<DeviceCacheItem>` (Vua tiết kiệm RAM & CPU Cache-friendly)
* **Cơ chế:** Là một mảng liên tục các con trỏ trỏ tới Record.
* **Ưu điểm vượt trội:** Khi CPU duyệt qua danh sách, nhờ tính chất **Spatial Locality (Tính cục bộ không gian)**, bộ điều khiển bộ nhớ (Memory Controller) tự động nạp cả khối **64 bytes (Cache Line)** vào L1/L2/L3 Cache của CPU.
* **Tốc độ:** Quét qua toàn bộ 100,000 phần tử để chuẩn bị gửi notification chỉ tốn **dưới 1 mili-giây** (thường chỉ 0.3ms - 0.8ms)!
* **Nhược điểm:** Tìm kiếm một user cụ thể tốn $O(N)$. Nhưng ở bài toán Duolingo, chúng ta cần **gửi cho TOÀN BỘ user**, tức là duyệt tuần tự $100\%$, nên `ArrayList` là số 1!

### 2. `HashMap<Long, String>` (Đánh đổi RAM lấy $O(1)$ Lookup)
* **Cơ chế:** Lưu theo cặp `userId -> deviceToken` để tra cứu $O(1)$.
* **Cái giá phải trả về RAM:** Để lưu được 1 phần tử trong HashMap, Java phải tạo thêm một đối tượng nội bộ là `Node<K, V>`:
  * Node Header: 12 - 16 bytes
  * `int hash`: 4 bytes
  * `K key`: 4 - 8 bytes
  * `V value`: 4 - 8 bytes
  * `Node next`: 4 - 8 bytes
  * $\Rightarrow$ **Mỗi bản ghi bị tốn thêm ~32 bytes rác chỉ để duy trì cấu trúc Map!**
  * Thêm nữa: Mảng Bucket `Node[]` luôn phải để dư chỗ (Load Factor 0.75) $\rightarrow$ Lãng phí thêm 25% - 50% mảng rỗng.

### 3. `ConcurrentHashMap<Long, String>` (Bảo bối cho Worker đa luồng)
* **Cơ chế:** Chia nhỏ các bucket, sử dụng cơ chế **CAS (Compare-And-Swap)** và chỉ khóa (lock) trên từng Node đầu bucket thay vì khóa toàn bộ Map như `Hashtable` hay `Collections.synchronizedMap()`.
* **Khi nào dùng:** Khi bạn có 20 - 50 Interim Workers chạy song song cùng vừa đọc vừa ghi dữ liệu vào Cache mà không sợ bị xung đột (Race Condition) hay nghẽn khóa toàn cục.

---

## 4. Hướng dẫn thực hành trên máy tính của bạn

1. Khởi động lại ứng dụng Spring Boot trong IntelliJ IDEA.
2. Mở trình duyệt tại 👉 **`http://localhost:8080`**.
3. Kéo xuống mục **"NGÀY 4: Giải Phẫu Bộ Nhớ JVM"**:
   * Bấm nút: **`[So sánh 3 Cấu Trúc Dữ Liệu (100k records)]`**.
4. Quan sát số liệu thực tế đo được trên máy bạn:
   * **`ArrayList<Record>`:** RAM tiêu thụ thấp nhất, thời gian quét 100k records siêu tốc (chưa tới **1 ms**).
   * **`HashMap`:** RAM tiêu thụ sẽ cao hơn rõ rệt (do chi phí của `Node<K,V>`).
   * **`ConcurrentHashMap`:** RAM tương đương hoặc nhỉnh hơn HashMap một chút để đổi lấy tính an toàn đa luồng.

---

## 5. Câu hỏi Senior & Mở rộng quy mô (Scale-up)

### Câu hỏi: Nếu Duolingo cần nạp 50 TRIỆU User vào RAM thì sao?
* Nếu dùng 50 triệu Object Java thông thường: Chỉ riêng Object Header đã ngốn:  
  $$50,000,000 \times 16 \text{ bytes} = 800 \text{ MB RAM rác (chưa tính dữ liệu)!}$$
* **Cách các hệ thống High-Performance xử lý:**
  1. **Dùng Primitive Collections:** Thư viện như `fastutil` hoặc `Koloboke` thay thế `Map<Long, String>` bằng `Long2ObjectOpenHashMap` để loại bỏ boxing `Long` sang đối tượng.
  2. **Mảng nguyên thủy phẳng (Flat Primitive Arrays):** Lưu `long[] userIds` và mảng byte phẳng `byte[] tokens` để giảm số lượng Object xuống gần bằng 0!
  3. **Off-Heap Memory:** Cất dữ liệu ra ngoài bộ nhớ Heap (dùng `ByteBuffer.allocateDirect()` hoặc thư viện như ChronicleMap / LMDB) để **Garbage Collector không phải quét qua 50 triệu object**, loại bỏ hoàn toàn hiện tượng lag máy (GC Pause).
