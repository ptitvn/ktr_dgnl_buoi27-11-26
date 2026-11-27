# Bài làm CinemaBox – Database + Backend

Họ tên: Lê Trung Đông 
Mã sinh viên: PTIT-HN-010
Lớp: CNNT3 

## Pha 1 (không dùng AI)

Tự đánh dấu phần đã xong trước mốc 1 (chỉ để GV tham khảo, không thay cho kết quả chấm):

- [x] I1  - [x] I2  - [x] I3  - [x] Endpoint GET /api/showtimes/{id}
- [x] F1  - [x] F2  - [x] F3  - [x] API khách hàng (tạo, xem, danh sách, validate, lỗi)

Ghi chú (nếu có): Đã hoàn thành toàn bộ truy vấn SQL và API của Pha 1 theo đúng chuẩn cấu trúc và định dạng lỗi `ApiError`.

## Pha 2 (được dùng AI)

### Cách bạn chống bán vượt số ghế khi nhiều người đặt cùng lúc

- **Cách đã chọn**: Sử dụng khóa bi quan ở tầng cơ sở dữ liệu (`@Lock(LockModeType.PESSIMISTIC_WRITE)` tương ứng với câu lệnh `SELECT ... FROM showtimes WHERE id = ? FOR UPDATE`) kết hợp với ràng buộc `CHECK (available_seats >= 0)` trực tiếp trong bảng `showtimes` (`schema.sql`).
- **Vì sao chọn**: Khóa bi quan buộc các giao dịch cùng đặt vé vào một suất chiếu phải xếp hàng chờ khóa dòng của suất chiếu đó. Khác với khóa lạc quan (Optimistic Locking) dễ làm các request hợp lệ bị từ chối oan do xung đột phiên bản khi vẫn còn đủ ghế, khóa bi quan đảm bảo mọi request hợp lệ đều lần lượt đặt được vé cho tới khi thực sự hết ghế mới trả về `409 NOT_ENOUGH_SEATS`. Ràng buộc `CHECK (available_seats >= 0)` đóng vai trò chốt chặn cuối cùng ở tầng CSDL để `available_seats` không bao giờ âm.
- **Đã kiểm chứng bằng gì**: Kiểm chứng bằng 2 test đa luồng (`ExecutorService` + `CountDownLatch`) trong `BookingTest`: `datVeDongThoiKhongBanVuotSoGhe` (10 luồng đồng thời đặt 2 ghế trên suất còn 8 ghế -> đúng 4 luồng trả `201`, 6 luồng trả `409`, ghế còn lại bằng `0`) và `datVeDongThoiLanRequestThanhToanLoiDuLieuVanDung`.

### Cách bạn hoàn tác khi thanh toán lỗi

- Phương thức `BookingService.createBooking` được đánh dấu `@Transactional`. Khi khách đặt vé, hệ thống khóa dòng suất chiếu, kiểm tra và trừ `availableSeats`, sau đó gọi `paymentGateway.charge(customerId, totalAmount)` rồi mới lưu bản ghi `Booking` với trạng thái `CONFIRMED`.
- Khi cổng thanh toán từ chối giao dịch (ném `PaymentFailedException` kế thừa từ `RuntimeException`), Spring Transaction tự động rollback toàn bộ giao dịch hiện tại: số ghế `available_seats` trên suất chiếu được khôi phục nguyên trạng, không có bản ghi `Booking` `CONFIRMED` nào được ghi xuống CSDL, khóa dòng được giải phóng cho các request kế tiếp và `GlobalExceptionHandler` bắt `PaymentFailedException` để trả về mã `402 PAYMENT_FAILED`.

### Test bạn đã viết (tên lớp, tên test, mỗi test kiểm tra điều gì)

1. Lớp **`BookingTest`** (`src/test/java/vn/rikkei/cinemabox/BookingTest.java`):
   - `datVeThanhCongKhachStandardVaKhachGold`: Kiểm tra đặt vé thành công trả `201`, trừ đúng số ghế, tính đúng `totalAmount` cho khách `STANDARD` (100% giá) và khách `GOLD` (giảm 10% làm tròn xuống tới đồng).
   - `thanhToanBiTuChoiHoanTacDuLieu`: Kiểm tra khi tổng tiền vượt `1.000.000` đồng làm `FakePaymentGateway` ném `PaymentFailedException` thì API trả `402 PAYMENT_FAILED`, số ghế trống của suất chiếu giữ nguyên và không sinh thêm vé `CONFIRMED` nào.
   - `khongDuGheTraVe409NotEnoughSeats`: Kiểm tra đặt số lượng vé lớn hơn `available_seats` trả về `409 NOT_ENOUGH_SEATS` và giữ nguyên số ghế.
   - `kiemTraValidationVaNotFoundKhiDatVe`: Kiểm tra `quantity` ngoài khoảng `1..10` trả `400 VALIDATION_FAILED`, và `customerId` hoặc `showtimeId` không tồn tại trả `404 NOT_FOUND`.
   - `huyVeConfirmedThanhCongVaHuyLaiTra409InvalidState`: Kiểm tra `POST /api/bookings/{id}/cancel` đổi trạng thái vé sang `CANCELLED`, cộng trả lại ghế cho suất chiếu; nếu gọi huỷ lại vé đã `CANCELLED` thì trả `409 INVALID_STATE`; vé không tồn tại trả `404 NOT_FOUND`.
   - `rangBuocDatabaseChanAvailableSeatsAm`: Kiểm tra trực tiếp bằng `JdbcTemplate` rằng câu lệnh SQL cập nhật `available_seats = -1` bị CSDL chặn bởi ràng buộc `CHECK (available_seats >= 0)`.
   - `datVeDongThoiKhongBanVuotSoGhe`: Dùng 10 luồng đồng thời cùng đặt 2 vé vào suất chiếu còn 8 ghế; kiểm chứng đúng 4 luồng thành công (`201`), 6 luồng hết ghế (`409`), số ghế còn lại bằng `0`.
   - `datVeDongThoiLanRequestThanhToanLoiDuLieuVanDung`: Dùng 8 luồng đồng thời vào suất chiếu còn 20 ghế (3 luồng đặt 10 vé bị lỗi thanh toán `402` xen lẫn 5 luồng đặt 5 vé hợp lệ); kiểm chứng 4 luồng hợp lệ thành công (`201`), số ghế còn lại bằng `0`, tổng ghế ban đầu bằng ghế còn lại cộng tổng ghế `CONFIRMED` bán thêm, và mọi vé `CONFIRMED` đều có `payment_ref`.
2. Lớp **`Pha1VerifyTest`** (`src/test/java/vn/rikkei/cinemabox/Pha1VerifyTest.java`):
   - `testSqlQueriesAndF1Table`: Kiểm tra các câu truy vấn SQL `I1`, `I2`, `I3`, `F2`, `F3` và các ràng buộc `FOREIGN KEY`, `CHECK (rating BETWEEN 1 AND 5)`, `UNIQUE (customer_id, movie_id)`, `DEFAULT CURRENT_TIMESTAMP` của bảng `reviews` (`F1`).
   - `testShowtimeApi`: Kiểm tra `GET /api/showtimes/{id}` khi tìm thấy (`200`) và không tìm thấy (`404 NOT_FOUND`).
   - `testCustomerEndpoints`: Kiểm tra tạo khách hàng (`201`), trùng email (`409 CONFLICT`), dữ liệu sai quy tắc (`400 VALIDATION_FAILED`), JSON sai cú pháp (`400 BAD_REQUEST`), xem theo ID (`200`/`404`), và phân trang danh sách khách hàng (`200`/`400`).

### Nhật ký dùng AI

| # | Bạn hỏi AI điều gì | AI gợi ý gì | Bạn giữ / sửa / bỏ, vì sao |
|---|---|---|---|
| 1 | Nên dùng Optimistic Locking (`@Version`), câu lệnh `UPDATE ... WHERE available_seats >= :qty` hay Pessimistic Locking (`PESSIMISTIC_WRITE`) để chống bán vượt số ghế khi có nhiều người đặt cùng lúc lẫn giao dịch thanh toán lỗi? | AI phân tích 3 cách: (1) `@Version` sẽ làm các luồng xung đột bị lỗi `OptimisticLockException` ngay cả khi vẫn còn đủ ghế nếu không cài đặt vòng lặp retry phức tạp; (2) `UPDATE ... WHERE available_seats >= :qty` trước khi gọi cổng thanh toán vẫn giữ row lock trong transaction cho tới khi `charge()` xong, tương đương khóa bi quan; (3) `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) trên `ShowtimeRepository` vừa rõ ràng trong JPA, vừa xếp hàng tuần tự các request cùng suất chiếu và tự động nhả khóa + hoàn ghế khi `PaymentFailedException` kích hoạt rollback. | **Giữ** phương án `PESSIMISTIC_WRITE` kết hợp với ràng buộc `CHECK (available_seats >= 0)` trong `schema.sql`, **bỏ** phương án Optimistic Locking vì đề yêu cầu *"Khi suất chiếu còn đủ ghế thì request hợp lệ phải đặt được; chỉ từ chối bằng 409 NOT_ENOUGH_SEATS khi thật sự không còn đủ ghế"*. **Đã kiểm chứng** bằng test `datVeDongThoiLanRequestThanhToanLoiDuLieuVanDung` trong `BookingTest` với 8 luồng đồng thời (gồm cả luồng bị `402 PAYMENT_FAILED` mất 80ms): cả 4 luồng hợp lệ (tổng 20 ghế) đều đặt thành công `201`, không luồng nào bị từ chối oan. |
| 2 | Cách tính giảm giá 10% cho khách hạng `GOLD` và làm tròn xuống tới đồng trong Java tránh sai số dấu phẩy động như thế nào? | AI gợi ý dùng phép toán số nguyên `long`: `totalAmount = (rawAmount * 90L) / 100L` thay vì nhân với `0.9` kiểu `double`. | **Giữ** nguyên phép tính số nguyên `(rawAmount * 90L) / 100L` vì phép chia số nguyên dương trong Java tự động làm tròn xuống tới đồng và tuyệt đối không bị sai số dấu phẩy động (`double`). Đã kiểm chứng qua test `datVeThanhCongKhachStandardVaKhachGold`. |
| 3 | Khi làm API huỷ vé `POST /api/bookings/{id}/cancel`, cần lưu ý gì nếu có 2 request cùng gọi huỷ 1 vé đồng thời? | AI gợi ý dùng `@Lock(LockModeType.PESSIMISTIC_WRITE)` khi đọc cả `Booking` và `Showtime` trong `cancelBooking` để tránh 2 request cùng thấy trạng thái `CONFIRMED` và cộng trả ghế 2 lần. | **Giữ** và áp dụng `findByIdForUpdate` cho cả `BookingRepository` và `ShowtimeRepository` trong `cancelBooking`, đảm bảo request thứ hai sẽ thấy trạng thái `CANCELLED` và trả về `409 INVALID_STATE`. Đã kiểm chứng qua test `huyVeConfirmedThanhCongVaHuyLaiTra409InvalidState`. |
