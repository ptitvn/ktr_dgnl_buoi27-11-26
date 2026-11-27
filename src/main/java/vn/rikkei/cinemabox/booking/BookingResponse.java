package vn.rikkei.cinemabox.booking;

import java.time.LocalDateTime;

/** DTO trả ra ngoài cho vé đặt. */
public record BookingResponse(
        Long id,
        Long customerId,
        Long showtimeId,
        Integer quantity,
        Long totalAmount,
        String status,
        String paymentRef,
        LocalDateTime createdAt) {

    public static BookingResponse from(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getCustomer().getId(),
                b.getShowtime().getId(),
                b.getQuantity(),
                b.getTotalAmount(),
                b.getStatus(),
                b.getPaymentRef(),
                b.getCreatedAt()
        );
    }
}
