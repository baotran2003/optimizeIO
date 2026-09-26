# 🚀 optimizeIO — High-Throughput Notification System Benchmark

> Mô phỏng và đo đạc kiến trúc gửi 300k Push Notification trong 5 giây
> đơn giản để chạy trên máy cá nhân.

## 📖 Bối cảnh bài toán

gửi Push Notification tới **300k người dùng trong 5 giây(60,000noti/s)**
(Tốc độ, Scale, Thời gian)]

## 🏗️ Kiến trúc Lab 1: Zero DB Query At Peak

Tại thời điểm cao điểm, việc truy vấn Database để lấy 100,000 – 4,000,000 token thiết bị chắc chắn sẽ làm cạn kiệt Connection Pool và nghẽn ổ đĩa. Lab 1 mô phỏng giải pháp của Duolingo: **Nạp sẵn dữ liệu vào RAM của Worker trước giờ G**.
PostgreSQL → InMemoryPreloadService → List<DeviceCacheItem> trên RAM]

## 📊 Kết quả đo đạc thực tế (trên máy của tôi)

| Bài thực nghiệm | Cách tiếp cận A                             | Cách tiếp cận B                          | Tốc độ chênh lệch |
|:---|:--------------------------------------------|:-----------------------------------------|:-----------------:|
| Lab 1: Đọc 100k records | JPA `findAll()`: [2182] ms                  | JDBC + Record: [252] ms                  |  [8]x nhanh hơn   |
| Lab 1: RAM tiêu thụ | JPA Entity: [177.70] MB                     | Java 17 Record: [77.51] MB               |  Tiết kiệm [2]x   |
| Lab 3: Chèn 2k records | `saveAll()`: [611] ms ~ 3.273 ops/s         | `batchUpdate()`: [63] ms ~31.75 ops/s    |  [10]x nhanh hơn  |
| Lab 5: 15 req đồng thời | Pool=10: 10 request đầu tiên chạy xong ngay | Pool=15: 10 request đầu tiên chạy ngay, 5 requests sau buộc phải xếp hàng chờ đợt 2 (~4,000ms) |        [?]        |

## 🧪 Cách chạy thực nghiệm

### Yêu cầu
- Java 17+
- PostgreSQL 14+
- Spring Boot 4.x

### Khởi động
```bash
./gradlew bootRun
