# 📅 NGÀY 1: BẢN CHẤT PHẦN CỨNG, ĐỘ TRỄ VẬT LÝ & TƯ DUY IN-MEMORY

> **Mục tiêu:** Thoát khỏi tư duy "chỉ viết code chạy đúng", hiểu sâu về độ trễ phần cứng ($ns$ vs $ms$) và nguyên lý vì sao In-Memory lại là vũ khí tối thượng cho hệ thống High-Throughput (như Duolingo 4M noti/5s).

---

## 1. Bối cảnh bài toán Duolingo & Cái bẫy "Naive CRUD"

* **Thách thức:** Gửi 4,000,000 push notifications trong vòng **5 giây** khi quảng cáo Super Bowl phát sóng $\rightarrow$ Cần đạt **800,000 notifications/giây**.
* **Thực tế của một dev mới làm CRUD:**
  ```java
  // ❌ VÒNG LẶP CHẾT CHÓC
  List<Long> userIds = ...; // 100,000 người dùng
  for (Long userId : userIds) {
      // Mỗi lần lặp lại chọc vào DB lấy Device Token
      UserDevice device = userDeviceRepository.findByUserId(userId);
      sendPush(device.getDeviceToken());
  }
  ```
  * Mỗi lần gọi `findByUserId()` = 1 lần gửi gói tin qua mạng tới DB và chờ kết quả về (**Network Round-Trip Time - RTT**).
  * Một RTT mạng nội bộ trung bình tốn khoảng **0.5 ms**.

---

## 2. Kim tự tháp phần cứng & Bảng cửu chương độ trễ (Latency Numbers)

```
        ▲          [CPU Registers]           ~0.5 ns
       ╱ ╲         [L1 Cache]                ~1 ns
      ╱   ╲        [L2 Cache]                ~3 - 10 ns
     ╱     ╲       [L3 Cache]                ~10 - 20 ns
    ╱       ╲      [RAM]                     ~100 ns
   ╱         ╲     [NVMe SSD (Ổ cứng)]       ~10 - 50 µs (10,000 - 50,000 ns)
  ╱___________╲    [Network / Database RTT]  ~0.5 - 2 ms (500,000 - 2,000,000 ns)
```

### Quy đổi đơn vị thời gian bắt buộc phải nhớ:
* $1 \text{ giây (s)} = 1,000 \text{ mili-giây (ms)}$
* $1 \text{ mili-giây (ms)} = 1,000 \text{ micro-giây (µs)} = 1,000,000 \text{ nano-giây (ns)}$
* $\Rightarrow 1 \text{ giây} = 1,000,000,000 \text{ nano-giây}$ (1 tỷ ns).

---

## 3. Phép tính nhẩm 100,000 bản ghi (Tự kiểm chứng)

| Cách tiếp cận | Thời gian / 1 bản ghi | Tổng thời gian cho 100,000 bản ghi | Nhận xét thực tế |
| :--- | :--- | :--- | :--- |
| **Chọc DB từng dòng (Naive)** | **0.5 ms** | $100,000 \times 0.5\text{ms} = 50,000\text{ms} =$ **50 GIÂY** | Khách hàng chờ quay vòng gần 1 phút. Quảng cáo 5s kết thúc từ lâu! |
| **Đọc trên RAM (In-Memory)** | **100 ns** | $100,000 \times 100\text{ns} = 10\text{ms} =$ **0.01 GIÂY** | Một cái chớp mắt vô hình với mắt người. Đạt chuẩn Real-time. |

$$\frac{50 \text{ giây}}{0.01 \text{ giây}} = \mathbf{5,000 \text{ LẦN CHÊNH LỆCH!}}$$

---

## 4. Đối chiếu thực tế trên chiếc máy tính của bạn

* **Thông số máy của bạn:**
  * L1 Cache: **1.6 MB**
  * L2 Cache: **18.0 MB**
  * L3 Cache: **24.0 MB** $\rightarrow$ Tổng L2 + L3 = **42.0 MB**
  * RAM Speed: **7467 MT/s** (băng thông cực lớn ~50-60 GB/s)
* **Phát hiện quan trọng:**
  * Khi nạp 100,000 bản ghi bằng Java 17 Record (`DeviceCacheItem`), toàn bộ danh sách chỉ tốn khoảng **~10 - 15 MB**.
  * Con số **15 MB** này nằm **vừa khít bên trong L3 Cache (24MB)** của CPU bạn!
  * CPU thậm chí **không cần chạy ra thanh RAM ngoài** mà đọc trực tiếp trên bề mặt con chip với tốc độ ánh sáng (~10-20ns).

---

## 5. Những gì đã code & đúc kết trong Lab 1

* Tạo `DeviceCacheItem.java` (Java 17 Record siêu nhẹ, plain immutable data).
* Tạo `InMemoryPreloadService.java`: Mô phỏng **20 Interim Workers** của Duolingo: Nạp sẵn toàn bộ danh sách vào RAM trước giờ G vài tiếng.
* Khi giờ G đến (bấm nút đỏ): **100% truy vấn diễn ra trên RAM**, Database không bị gọi bất kỳ một lần nào (**Zero Database Query at Peak**).

> **💡 Quy tắc vàng Ngày 1:**  
> *"Khoảng cách từ CPU tới RAM tính bằng xentimét. Khoảng cách từ CPU tới Database phải vượt qua hệ điều hành, TCP socket, context-switch và ổ đĩa. Giờ cao điểm tuyệt đối không đụng vào Database nếu dữ liệu có thể chuẩn bị trước."*

### 🧠 Câu hỏi tự kiểm tra

> **Tại sao một query `SELECT *` dù DB đã đánh index vẫn không thể nhanh bằng đọc RAM?**

> 💡 **Nguyên tắc cần nhớ:**  
> Khoảng cách từ **CPU** tới **RAM** tính bằng **xentimét**.  
> Khoảng cách từ **CPU** tới **Database** phải đi qua **OS → TCP socket → context switch → ...**