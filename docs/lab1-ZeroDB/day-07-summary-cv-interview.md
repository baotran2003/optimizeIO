# 📅 NGÀY 7: TỔNG KẾT, PHỎNG VẤN & ĐƯNG VÀO CV / GITHUB

> **Đây là ngày quan trọng nhất.** 6 ngày qua bạn đã tích lũy kiến thức về phần cứng, JVM Internals, Database, Connection Pool và System Design. Ngày 7 biến toàn bộ kiến thức đó thành **tài sản thực sự** — thứ giúp bạn vượt qua phỏng vấn và tạo ấn tượng mạnh với nhà tuyển dụng.

---

## Part 1: Comprehensive 15-Question Technical Assessment (7-Day Review)

> **Instructions:** Select the best answer (A, B, C, or D) for each question. The answer key and technical explanations are provided in the expandable section at the end of Part 1.

---

### [Day 1: Hardware Latency & Latency Numbers]

#### Question 1: What is the approximate latency difference between reading from RAM and completing a single Network Round-trip to a Database?
- [ ] A. RAM takes ~100 µs, while Database takes ~1 ms (a 10x difference).
- [ ] B. RAM takes ~100 ns, while Database takes ~0.5 - 2 ms (a 5,000x - 20,000x difference).
- [ ] C. RAM and Database have identical latency when the Database runs on localhost.
- [ ] D. Reading from an NVMe SSD is faster than RAM due to PCIe 4.0 bandwidth.

#### Question 2: If a system iterates through 100,000 user IDs using a loop: `for (Long id : ids) { userRepo.findByUserId(id); }`, and each query takes 0.5 ms, what is the total elapsed time?
- [ ] A. 500 ms (0.5 seconds).
- [ ] B. 5,000 ms (5 seconds).
- [ ] C. 50,000 ms (50 seconds).
- [ ] D. 500,000 ms (500 seconds).

---

### [Day 2: JPA / Hibernate Memory Anatomy]

#### Question 3: When invoking `userDeviceRepository.findAll()` to load 100,000 records, why does the JVM Heap memory suddenly surge by hundreds of megabytes?
- [ ] A. Because PostgreSQL forces the client to download all table indexes into client RAM.
- [ ] B. Because Hibernate performs Entity Hydration (~800k Java objects created), retains them in the First-Level Cache (Persistence Context), and duplicates memory to store Dirty Checking Snapshots.
- [ ] C. Because the JVM automatically clones each entity 10 times to prevent concurrent modifications.
- [ ] D. Because HikariCP buffers query result sets directly inside its connection pool memory.

#### Question 4: For large-scale read-only workloads (batch processing, reporting, exporting 100k rows to CSV/Excel), which approach yields optimal performance and memory efficiency?
- [ ] A. Use standard `repository.findAll()` to take advantage of automatic Dirty Checking.
- [ ] B. Use JPA Entities and invoke `entityManager.clear()` after processing each individual row.
- [ ] C. Use `JdbcTemplate` or Native Queries mapped directly to immutable Java 17 Records or DTO Projections (bypassing Hibernate Managed Entities entirely).
- [ ] D. Increase JVM `-Xmx` to 32GB so Hibernate can run without heap constraints.

---

### [Day 3: JDBC Batching & The saveAll Trap]

#### Question 5: Why does Hibernate SILENTLY DISABLE JDBC batching when an Entity uses `@GeneratedValue(strategy = GenerationType.IDENTITY)`?
- [ ] A. It is an unresolved bug in the current release of Hibernate.
- [ ] B. Because `IDENTITY` delegates ID generation to the Database; Hibernate must immediately execute each `INSERT` to retrieve the generated ID required by the Persistence Context, making delayed batching impossible.
- [ ] C. Because the SQL standard prohibits multi-row inserts on tables with primary keys.
- [ ] D. Because PostgreSQL JDBC drivers do not support batch executions.

#### Question 6: What is the primary purpose of the `reWriteBatchedInserts=true` parameter in a PostgreSQL JDBC connection URL?
- [ ] A. It automatically converts `INSERT` statements to `UPDATE` statements upon key collisions.
- [ ] B. It rewrites multiple individual `INSERT INTO ... VALUES (...)` statements into a single multi-value statement: `INSERT INTO ... VALUES (...), (...), (...)`.
- [ ] C. It compresses network packets using GZIP before transmitting over TCP.
- [ ] D. It disables PostgreSQL Write-Ahead Logging (WAL) to maximize disk write speed.

---

### [Day 4: Object Memory Layout & In-Memory Data Structures]

#### Question 7: In a standard 64-bit HotSpot JVM with Compressed OOPs enabled, what is the minimum memory footprint of an empty Java Object?
- [ ] A. 0 bytes.
- [ ] B. 4 or 8 bytes.
- [ ] C. At least 16 bytes (Object Header: Mark Word 8B + Klass Word 4B + 4B Padding).
- [ ] D. Exactly 64 bytes to match the CPU Cache Line size.

#### Question 8: Why is iterating sequentially over an `ArrayList<DeviceCacheItem>` (Record) significantly faster than traversing a `HashMap<Long, String>` for 100k items?
- [ ] A. Because HashMap full traversal has a time complexity of O(N^2).
- [ ] B. Because ArrayList elements are stored contiguously in memory, leveraging Spatial Locality so the CPU hardware prefetcher loads 64-byte Cache Lines into L1/L2/L3 caches, whereas HashMap Nodes are scattered across the Heap causing frequent CPU Cache Misses.
- [ ] C. Because HashMap must compute MD5 hashes for each key during traversal.
- [ ] D. Because ArrayList utilizes off-heap memory, bypassing Garbage Collection.

---

### [Day 5: HikariCP Connection Pool & Cascading Failures]

#### Question 9: What is the default `maximum-pool-size` configured by HikariCP in a Spring Boot application?
- [ ] A. 10.
- [ ] B. 50.
- [ ] C. 100.
- [ ] D. Unlimited.

#### Question 10: How does a "Cascading Failure" unfold when a database connection pool becomes fully exhausted under sudden traffic spikes?
- [ ] A. The database automatically drops inactive tables to reclaim disk space.
- [ ] B. Incoming HTTP requests block waiting for database connections, exhausting the Web Server (Tomcat) worker thread pool and rendering completely unrelated non-database endpoints unresponsive.
- [ ] C. The host operating system temporarily shuts down network interfaces to protect the CPU.
- [ ] D. HikariCP automatically falls back to an embedded SQLite instance on local disk.

#### Question 11: What is the empirical formula recommended by PostgreSQL and HikariCP maintainers for determining the optimal connection pool size?
- [ ] A. `Pool Size = Concurrent Online Users`.
- [ ] B. `Pool Size = Max Tomcat Threads / 2`.
- [ ] C. `Pool Size = (DB Server CPU Cores × 2) + Effective Spindle Count (Disks)`.
- [ ] D. `Pool Size = DB Server RAM (GB) × 10`.

---

### [Day 6: Architecture Scale & Trade-offs]

#### Question 12: In high-throughput architectures like Duolingo (800,000 requests/second), why does Worker Local In-Memory RAM fundamentally outperform a remote Redis cluster?
- [ ] A. Because Redis cannot store standard String key-value pairs.
- [ ] B. Because Redis is a remote database requiring network round-trips (~0.5ms - 2ms RTT); handling 800k req/s would require a massive, expensive cluster, whereas Local RAM is accessed via CPU buses in nanoseconds with zero extra infrastructure cost.
- [ ] C. Because Redis is strictly limited to Windows operating systems.
- [ ] D. Because Redis synchronously flushes all writes to rotational magnetic disks.

#### Question 13: Why did Duolingo use AWS S3 as an intermediate data staging layer instead of querying DynamoDB directly prior to broadcast time?
- [ ] A. Because DynamoDB cannot perform lookups by `user_id`.
- [ ] B. Because S3 provides low-cost, high-bandwidth parallel downloads, preventing the depletion of DynamoDB Read Capacity Units (RCUs) and eliminating resource contention on the active production database.
- [ ] C. Because DynamoDB enforces a strict ceiling of 100,000 items per table.
- [ ] D. Because AWS S3 can automatically dispatch push notifications to APNS and FCM.

#### Question 14: When does the "Stale Data" trade-off manifest in Duolingo's architecture, and why was it deemed acceptable?
- [ ] A. It occurs during power outages; accepted because backup diesel generators exist.
- [ ] B. It occurs when users change mobile devices after the static snapshot is taken; accepted because a minor delivery failure rate (1-2%) is a worthwhile trade-off for guaranteeing sub-5-second delivery for 98% of users without crashing backend infrastructure.
- [ ] C. It occurs when AWS SQS drops packets; accepted because notification messages are deemed low priority.
- [ ] D. It occurs when Redis runs out of memory and evicts older cache entries.

#### Question 15: In `InMemoryPreloadService`, what critical multi-threading guarantee does the `volatile` modifier provide for `private volatile boolean isPreloaded`?
- [ ] A. It prevents any modifications to the variable after initialization (identical to `final`).
- [ ] B. It automatically serializes the field value to disk when the JVM shuts down.
- [ ] C. It ensures CPU Cache Coherence and Visibility: writes and reads bypass per-core L1/L2 caches directly to Main Memory, establishing a Happens-Before relationship so reader threads see all previously populated cache items.
- [ ] D. It automatically synchronizes all method calls within the declaring class without requiring explicit `synchronized` blocks.

---

<details>
<summary>👉 <b>CLICK HERE TO VIEW ANSWER KEY & DETAILED EXPLANATIONS</b></summary>

### Answer Key:
| Question | Answer | Key Rationale |
|:---:|:---:|:---|
| **1** | **B** | RAM access is measured in nanoseconds (~100ns), whereas network round-trips take milliseconds (0.5-2ms) — a difference of 5,000x to 20,000x. |
| **2** | **C** | $100,000 \times 0.5\text{ ms} = 50,000\text{ ms} = 50\text{ seconds}$. |
| **3** | **B** | Entity Hydration generates hundreds of thousands of heap objects, First-Level Cache locks them in memory, and Dirty Checking duplicates snapshots. |
| **4** | **C** | DTOs and Java Records are unmanaged data carriers with zero persistence context overhead, allowing instant garbage collection. |
| **5** | **B** | `IDENTITY` forces immediate single-row `INSERT` execution to obtain generated primary keys, breaking JDBC batching. |
| **6** | **B** | Rewrites multiple insert statements into a single composite multi-value `INSERT INTO ... VALUES (...), (...)` statement. |
| **7** | **C** | Standard 64-bit object header: Mark Word (8B) + Klass Word (4B with Compressed OOPs) + 4B padding = 16 bytes minimum. |
| **8** | **B** | Sequential arrays leverage CPU Cache Lines (64B) and Spatial Locality; linked hash nodes cause pointer-chasing and cache misses. |
| **9** | **A** | HikariCP defaults to a conservative maximum pool size of 10 connections. |
| **10** | **B** | Blocked DB threads exhaust web server worker pools (Tomcat), causing system-wide cascading failure. |
| **11** | **C** | Standard formula: $\text{Core} \times 2 + \text{Effective Spindle Count}$. Exceeding this introduces severe OS context-switching overhead. |
| **12** | **B** | Local RAM bypasses network serialization and socket latency, delivering tens of millions of ops/sec at zero extra cost. |
| **13** | **B** | S3 serves as an inexpensive, horizontally scalable data lake, isolating production databases from massive pre-warming read spikes. |
| **14** | **B** | A deliberate engineering trade-off: accepting negligible stale device tokens to guarantee 100% database isolation at peak. |
| **15** | **C** | Eliminates stale reads caused by CPU core L1/L2 caches and guarantees thread visibility via the JVM memory model's Happens-Before contract. |

---

### Skill Assessment Rating:
* **14 - 15 Correct:** 🏆 **Mid/Senior Backend Engineer** — Comprehensive mastery of hardware latency, JVM memory layout, connection pools, and distributed system design trade-offs.
* **11 - 13 Correct:** 🥇 **Solid Junior+ Engineer** — Strong understanding of core performance concepts with minor gaps in low-level details.
* **Below 11 Correct:** 📖 Recommended to revisit Days 1 through 6 documentation to reinforce foundational concepts.
</details>

---

## Phần 2: Bộ câu hỏi phỏng vấn thực chiến (Interview Q&A)

Đây là **7 câu hỏi mà Interviewer chuyên sâu hay hỏi** về Performance và System Design. Với mỗi câu, hãy đọc câu hỏi, **tự viết câu trả lời của bạn trước**, rồi mới so sánh với câu trả lời tham khảo bên dưới.

---

### Câu 1 (Junior → Mid): "Tại sao không nên dùng JPA `findAll()` khi cần đọc số lượng lớn dữ liệu?"

**Câu trả lời tham khảo:**
> *"JPA `findAll()` dù chỉ gửi 1 câu SQL, nhưng Hibernate thực hiện 3 tác vụ rất tốn kém sau đó: (1) **Entity Hydration** - tạo hàng trăm nghìn Java Object trên Heap; (2) **Persistence Context** - nhốt toàn bộ Entity vào First-Level Cache, GC không thu hồi được; (3) **Dirty Checking Snapshot** - nhân đôi bộ nhớ để theo dõi thay đổi. Trong thực tế, với tác vụ chỉ đọc (read-only) như Batch Processing hay Report, nên dùng JDBC thuần hoặc Spring Data JPA Projection để đọc thẳng vào DTO/Record, tránh hoàn toàn overhead của Hibernate."*

---

### Câu 2 (Junior → Mid): "Em đã từng gặp vấn đề `saveAll()` chậm chưa? Em giải quyết như thế nào?"

**Câu trả lời tham khảo:**
> *"Khi dùng `@GeneratedValue(strategy = GenerationType.IDENTITY)`, Hibernate bị buộc phải tắt cơ chế Batching vì nó cần DB trả về ID ngay sau mỗi INSERT để quản lý Persistence Context. Điều này khiến 100k records phải gửi 100k Network Round-trips riêng lẻ. Giải pháp: (1) Chuyển sang `JdbcTemplate.batchUpdate()` để kiểm soát hoàn toàn, gom toàn bộ records vào 1 gói TCP duy nhất; (2) Thêm `reWriteBatchedInserts=true` vào JDBC URL để PostgreSQL gom các INSERT thành 1 câu Multi-value duy nhất. Kết quả: Thời gian chèn 100k records giảm từ vài phút xuống còn 2-3 giây."*

---

### Câu 3 (Mid): "HikariCP Connection Pool mặc định là 10. Khi nào con số này trở thành vấn đề?"

**Câu trả lời tham khảo:**
> *"Khi xảy ra Spike Traffic (đột biến request), nếu mỗi request giữ DB connection trong 500ms và có 100 request đồng thời, throughput chỉ đạt 20 requests/giây — toàn bộ request còn lại phải xếp hàng chờ connection. Nguy hiểm hơn là Cascading Failure: Tomcat Thread Pool bị block theo Connection Pool, khiến ngay cả các API không dùng DB cũng bị lag. Giải pháp: (1) Tối ưu query để giảm thời gian giữ connection; (2) Tăng Pool Size theo công thức `(CPU Cores × 2) + Số ổ đĩa`; (3) Với bài toán Flash Event như Duolingo Super Bowl, loại bỏ hoàn toàn DB query tại giờ cao điểm bằng In-Memory Preloading."*

---

### Câu 4 (Mid): "Tại sao `volatile` keyword lại cần thiết trong môi trường Multi-threading? Khi nào dùng và khi nào không đủ?"

**Câu trả lời tham khảo:**
> *"`volatile` giải quyết bài toán **CPU Cache Visibility**: Mỗi CPU Core có L1/L2 Cache riêng, nếu không có `volatile`, một biến được ghi bởi Thread A ở Core 1 có thể vẫn nằm trong L1 Cache của Core 1 mà Thread B ở Core 2 không nhìn thấy. `volatile` ép mọi thao tác đọc/ghi phải đi thẳng vào Main Memory, đảm bảo Happens-Before relationship. Tuy nhiên, `volatile` chỉ đủ cho bài toán **1 writer, nhiều reader**. Nếu có nhiều threads cùng ghi (ví dụ: counter++), cần dùng `AtomicLong` hoặc `synchronized` vì `volatile` không đảm bảo tính nguyên tử (Atomicity) của các thao tác tổng hợp."*

---

### Câu 5 (Mid → Senior): "Điểm khác biệt cốt lõi giữa Redis Cache và Local In-Memory Cache là gì? Khi nào chọn cái nào?"

**Câu trả lời tham khảo:**
> *"Redis là **Remote In-Memory** — mỗi lần đọc phải qua mạng TCP (~0.5-2ms). Local In-Memory là dữ liệu ngay trên RAM của process đang chạy (~100ns). Với throughput 800,000 ops/giây, Redis cần ~400 connections và một cụm server rất lớn trong khi Local RAM không tốn thêm chi phí và nhanh hơn 10,000 lần. Chọn Redis khi cần **chia sẻ trạng thái** giữa nhiều server (session, distributed lock, rate limiting). Chọn Local In-Memory khi cần **throughput tối đa**, dữ liệu ít/không thay đổi trong khoảng thời gian ngắn, và mỗi worker tự chủ hoàn toàn."*

---

### Câu 6 (Senior): "Thiết kế hệ thống gửi 10 triệu Push Notification trong 10 giây."

**Câu trả lời tham khảo:**
> *"Nguyên tắc cốt lõi: **Zero DB Query At Peak**. Pipeline gồm 3 giai đoạn: (1) **Offline**: Trích xuất 10 triệu user ra file phẳng (CSV) lên S3 từ trước — không tốn RCU của production DB; (2) **Pre-warming vài tiếng trước**: Scale 20+ Interim Workers đọc file từ S3, nạp vào Local RAM theo chunk, sử dụng Java Record + ArrayList tối ưu CPU Cache; (3) **Tại giờ G**: 100% đọc từ RAM (0 DB query), gom batch 250-500 user/message, đẩy vào Message Queue, Notification Workers bắn sang APNS/FCM. Trade-off chấp nhận: ~1-2% Stale Data (user đổi thiết bị), đổi lấy 98% còn lại nhận tin tức thì."*

---

### Câu 7 (Senior): "Bạn đã xây dựng project benchmark nào để đo hiệu năng chưa?"

**Câu trả lời mẫu (dành riêng cho bạn — tự viết):**
> *"Mình đã xây dựng project `optimizeIO` để mô phỏng và đo đạc kiến trúc In-Memory Preloading của Duolingo..."*

👉 **Đây là câu quan trọng nhất — câu trả lời của bạn sẽ được viết ở Phần 4.**

---

## Phần 3: Hướng dẫn viết CV Bullet Points

### ❌ Cách viết Junior (không ấn tượng):
> "Xây dựng hệ thống gửi thông báo người dùng sử dụng Spring Boot và PostgreSQL."

### ✅ Cách viết Engineer biết về Performance (gây ấn tượng mạnh):
> "Nghiên cứu và xây dựng project benchmark mô phỏng kiến trúc High-Throughput Notification của Duolingo (case study 4M noti/5s): **(1)** Thiết kế cơ chế In-Memory Pre-warming với Java 17 Record + ArrayList, loại bỏ 100% DB query tại giờ cao điểm, tăng throughput từ ~50k lên 5M+ ops/s (100x); **(2)** Chứng minh thực nghiệm overhead của JPA Hibernate (`findAll()` 100k records: ngốn 97MB RAM vs JDBC Record: 35MB — tiết kiệm 3x); **(3)** Benchmark JDBC `batchUpdate()` vs JPA `saveAll()`: nhanh hơn 30-50x nhờ loại bỏ Network Round-trips; **(4)** Xây dựng lab thực nghiệm Connection Pool Exhaustion với HikariCP, đo đạc Cascading Failure trực quan."

### Cấu trúc viết bullet CV mạnh (STAR-P Format):

```
[Hành động] + [Kỹ thuật dùng] + [Con số đo được] + [Bài học / Kết quả]
```

**Bài tập:** Tự viết 2-3 bullet points cho CV của bạn dựa trên những gì BẠN thực sự đã làm và đo được trong 7 ngày qua (số liệu thực trên máy của bạn).

---

## Phần 4: Bài tập cuối cùng — Viết README.md chuyên nghiệp cho GitHub

Đây là bài tập quan trọng nhất của toàn bộ lộ trình. **Hãy tự viết README.md** theo template dưới đây.

File cần tạo: `README.md` (đặt ở thư mục gốc của project)

---

### Template README.md — Bạn tự điền vào chỗ `[...]`

```markdown
# 🚀 optimizeIO — High-Throughput Notification System Benchmark

> Mô phỏng và đo đạc kiến trúc gửi 4 triệu Push Notification trong 5 giây 
> của Duolingo (Super Bowl 2024), được đơn giản hóa để chạy trên máy cá nhân.

## 📖 Bối cảnh bài toán

[Bạn tự viết: Tóm tắt bài toán Duolingo Super Bowl trong 3-4 câu. 
Gợi ý: Nói về con số 4 triệu noti trong 5 giây, thách thức gấp 80 lần 
hệ thống hiện tại, và 3 bài toán khó (Tốc độ, Scale, Thời gian)]

## 🏗️ Kiến trúc Lab 1: Zero DB Query At Peak

[Bạn tự vẽ hoặc mô tả bằng text diagram pipeline của Lab 1:
PostgreSQL → InMemoryPreloadService → List<DeviceCacheItem> trên RAM]

## 📊 Kết quả đo đạc thực tế (trên máy của tôi)

| Bài thực nghiệm | Cách tiếp cận A | Cách tiếp cận B | Tốc độ chênh lệch |
|:---|:---|:---|:---:|
| Lab 1: Đọc 100k records | JPA `findAll()`: [?] ms | JDBC + Record: [?] ms | [?]x nhanh hơn |
| Lab 1: RAM tiêu thụ | JPA Entity: [?] MB | Java 17 Record: [?] MB | Tiết kiệm [?]x |
| Lab 3: Chèn 2k records | `saveAll()`: [?] ms | `batchUpdate()`: [?] ms | [?]x nhanh hơn |
| Lab 5: 15 req đồng thời | Pool=10: [mô tả] | Pool=15: [mô tả] | [?] |

## 🧪 Cách chạy thực nghiệm

### Yêu cầu
- Java 17+
- PostgreSQL 14+
- Spring Boot 4.x

### Khởi động
```bash
./gradlew bootRun
```

Mở trình duyệt: `http://localhost:8080`

## 💡 Bài học rút ra

[Bạn tự viết 3-5 bullet points bài học quan trọng nhất]

## 🔗 Tài liệu chi tiết

- [Ngày 1: Độ trễ phần cứng & In-Memory](docs/day-01-hardware-latency-and-in-memory.md)
- [Ngày 2: JPA Hibernate Memory Anatomy](docs/day-02-jpa-hibernate-memory-anatomy.md)
- [Ngày 3: JDBC Batching & saveAll() Trap](docs/day-03-jdbc-batching-and-saveall-trap.md)
- [Ngày 4: JVM Memory Layout & Java 17 Record](docs/day-04-jvm-memory-layout-and-java17-record.md)
- [Ngày 5: HikariCP Connection Pool](docs/day-05-hikaricp-connection-pool.md)
- [Ngày 6: Kiến trúc thực tế & Trade-offs](docs/day-06-architecture-tradeoffs-and-scale.md)
```

---

## Phần 5: Chuẩn bị trả lời Câu 7 phỏng vấn — "Bạn đã xây dựng project benchmark nào?"

Khi Interviewer hỏi câu này, bạn có **45-60 giây**. Cấu trúc lý tưởng:

```
1. [10s] Bối cảnh: "Mình build project mô phỏng bài toán Duolingo..."
2. [20s] Kỹ thuật: "Mình đo đạc 4 thứ: JPA vs JDBC, Batch Insert, Connection Pool..."
3. [20s] Con số: "Kết quả: [số liệu thực tế của bạn]..."
4. [10s] Bài học: "Bài học lớn nhất là..."
```

**Bài tập:** Tự soạn câu trả lời 60 giây này bằng lời của bạn, ghi vào cuối file.

---

## Phần 6: Kết quả thực nghiệm & Câu trả lời của bạn

*(Tự điền sau khi hoàn thành toàn bộ phần trên)*

### CV Bullet Points (Tự viết):
```
- ...
- ...
- ...
```

### Câu trả lời phỏng vấn 60 giây (Tự viết):
```
...
```

### README.md đã hoàn thành?
- [ ] Điền đầy đủ số liệu thực tế
- [ ] Viết phần Bối cảnh bài toán
- [ ] Vẽ/mô tả sơ đồ kiến trúc
- [ ] Viết phần Bài học rút ra
- [ ] Push lên GitHub
