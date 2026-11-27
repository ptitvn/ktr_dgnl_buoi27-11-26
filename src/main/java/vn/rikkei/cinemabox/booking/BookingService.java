package vn.rikkei.cinemabox.booking;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.InvalidStateException;
import vn.rikkei.cinemabox.common.NotEnoughSeatsException;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;
import vn.rikkei.cinemabox.customer.Customer;
import vn.rikkei.cinemabox.customer.CustomerRepository;
import vn.rikkei.cinemabox.payment.PaymentGateway;
import vn.rikkei.cinemabox.showtime.Showtime;
import vn.rikkei.cinemabox.showtime.ShowtimeRepository;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ShowtimeRepository showtimeRepository;
    private final PaymentGateway paymentGateway;

    public BookingService(BookingRepository bookingRepository,
                          CustomerRepository customerRepository,
                          ShowtimeRepository showtimeRepository,
                          PaymentGateway paymentGateway) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.showtimeRepository = showtimeRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest req) {
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + req.customerId()));

        // Khoá bi quan (PESSIMISTIC_WRITE / SELECT ... FOR UPDATE) trên dòng suất chiếu
        // để đảm bảo các request đồng thời vào cùng một suất chiếu được xử lý tuần tự,
        // không bán vượt số ghế và không từ chối nhầm khi vẫn còn đủ ghế.
        Showtime showtime = showtimeRepository.findByIdForUpdate(req.showtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu id=" + req.showtimeId()));

        int quantity = req.quantity();
        if (showtime.getAvailableSeats() < quantity) {
            throw new NotEnoughSeatsException("Suất chiếu không còn đủ ghế trống");
        }

        long rawAmount = showtime.getPrice() * quantity;
        long totalAmount = "GOLD".equalsIgnoreCase(customer.getMemberLevel())
                ? (rawAmount * 90L) / 100L
                : rawAmount;

        // Trừ ghế trước và lưu vé trong cùng transaction; nếu paymentGateway.charge(...)
        // ném PaymentFailedException (RuntimeException), toàn bộ transaction sẽ rollback,
        // hoàn trả lại availableSeats và không lưu vé CONFIRMED nào.
        showtime.setAvailableSeats(showtime.getAvailableSeats() - quantity);
        showtimeRepository.save(showtime);

        String paymentRef = paymentGateway.charge(customer.getId(), totalAmount);

        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setShowtime(showtime);
        booking.setQuantity(quantity);
        booking.setTotalAmount(totalAmount);
        booking.setStatus("CONFIRMED");
        booking.setPaymentRef(paymentRef);
        booking.setCreatedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);
        return BookingResponse.from(saved);
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé id=" + bookingId));

        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            throw new InvalidStateException("Chỉ có thể huỷ vé đang ở trạng thái CONFIRMED");
        }

        Showtime showtime = showtimeRepository.findByIdForUpdate(booking.getShowtime().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu"));

        showtime.setAvailableSeats(showtime.getAvailableSeats() + booking.getQuantity());
        showtimeRepository.save(showtime);

        booking.setStatus("CANCELLED");
        Booking saved = bookingRepository.save(booking);
        return BookingResponse.from(saved);
    }
}
