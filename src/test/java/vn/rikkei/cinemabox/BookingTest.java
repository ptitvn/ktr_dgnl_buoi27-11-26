package vn.rikkei.cinemabox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class BookingTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema.sql"),
                new ClassPathResource("data.sql")
        );
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(jdbc.getDataSource());
    }

    @Test
    void datVeThanhCongKhachStandardVaKhachGold() throws Exception {
        // Khách 2 (STANDARD) đặt 2 vé suất 9 (price = 80000, available_seats = 100) -> totalAmount = 160000
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": 2, "showtimeId": 9, "quantity": 2 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(2))
                .andExpect(jsonPath("$.showtimeId").value(9))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(160000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentRef").isNotEmpty());

        Integer seatsAfterStandard = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 9", Integer.class);
        assertThat(seatsAfterStandard).isEqualTo(98);

        // Khách 1 (GOLD) đặt 1 vé suất 3 (price = 75000) -> giảm 10% còn 67500
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": 1, "showtimeId": 3, "quantity": 1 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(67500))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void thanhToanBiTuChoiHoanTacDuLieu() throws Exception {
        // Suất 11: price = 130000, available_seats = 25.
        // Khách 2 (STANDARD) đặt 8 vé -> totalAmount = 1.040.000 > 1.000.000 -> bị FakePaymentGateway từ chối (402)
        Integer initialSeats = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 11", Integer.class);
        Integer initialConfirmedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE showtime_id = 11 AND status = 'CONFIRMED'", Integer.class);

        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": 2, "showtimeId": 11, "quantity": 8 }
                                """))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.error").value("PAYMENT_FAILED"));

        Integer afterSeats = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 11", Integer.class);
        Integer afterConfirmedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE showtime_id = 11 AND status = 'CONFIRMED'", Integer.class);

        assertThat(afterSeats).isEqualTo(initialSeats);
        assertThat(afterConfirmedCount).isEqualTo(initialConfirmedCount);
    }

    @Test
    void khongDuGheTraVe409NotEnoughSeats() throws Exception {
        // Suất 7 chỉ còn 8 ghế trống, đặt 9 vé -> 409 NOT_ENOUGH_SEATS
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": 2, "showtimeId": 7, "quantity": 9 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("NOT_ENOUGH_SEATS"));

        Integer seats = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 7", Integer.class);
        assertThat(seats).isEqualTo(8);
    }

    @Test
    void kiemTraValidationVaNotFoundKhiDatVe() throws Exception {
        // Quantity ngoài khoảng 1..10 -> 400 VALIDATION_FAILED
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": 2, "showtimeId": 1, "quantity": 11 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // Khách không tồn tại -> 404 NOT_FOUND
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": 9999, "showtimeId": 1, "quantity": 1 }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        // Suất chiếu không tồn tại -> 404 NOT_FOUND
        mvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "customerId": 2, "showtimeId": 9999, "quantity": 1 }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void huyVeConfirmedThanhCongVaHuyLaiTra409InvalidState() throws Exception {
        // Vé id=1 (showtime_id=1, quantity=2, status=CONFIRMED); suất 1 đang có 35 ghế
        mvc.perform(post("/api/bookings/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.quantity").value(2));

        Integer seatsAfterCancel = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 1", Integer.class);
        assertThat(seatsAfterCancel).isEqualTo(37);

        // Huỷ lần nữa vé đã CANCELLED -> 409 INVALID_STATE
        mvc.perform(post("/api/bookings/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("INVALID_STATE"));

        // Huỷ vé không tồn tại -> 404 NOT_FOUND
        mvc.perform(post("/api/bookings/9999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void rangBuocDatabaseChanAvailableSeatsAm() {
        assertThatThrownBy(() -> jdbc.update("UPDATE showtimes SET available_seats = -1 WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void datVeDongThoiKhongBanVuotSoGhe() throws Exception {
        // Suất 7 có available_seats = 8, price = 90000.
        // Cho 8 luồng cùng đặt mỗi luồng 2 vé -> tối đa 4 luồng thành công (8 ghế), 4 luồng bị 409 NOT_ENOUGH_SEATS.
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                MvcResult res = mvc.perform(post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "customerId": 2, "showtimeId": 7, "quantity": 2 }
                                        """))
                        .andReturn();
                return res.getResponse().getStatus();
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        long count201 = statuses.stream().filter(s -> s == 201).count();
        long count409 = statuses.stream().filter(s -> s == 409).count();

        assertThat(count201).isEqualTo(4);
        assertThat(count409).isEqualTo(4);

        Integer remainingSeats = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 7", Integer.class);
        assertThat(remainingSeats).isZero();
    }

    @Test
    void datVeDongThoiLanRequestThanhToanLoiDuLieuVanDung() throws Exception {
        // Suất 6 có available_seats = 20, price = 110000.
        // Gửi đồng thời:
        // - 3 request đặt 10 vé bởi khách 2 (STANDARD): 10 * 110000 = 1.100.000 > 1.000.000 -> 402 PAYMENT_FAILED
        // - 5 request đặt 5 vé bởi khách 2 (STANDARD): 5 * 110000 = 550.000 -> 4 request thành công (20 ghế), 1 request 409 NOT_ENOUGH_SEATS
        Integer initialSeats = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 6", Integer.class);
        Integer initialSold = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM bookings WHERE showtime_id = 6 AND status = 'CONFIRMED'",
                Integer.class);

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> quantities = new ArrayList<>(List.of(10, 10, 10, 5, 5, 5, 5, 5));
        Collections.shuffle(quantities);

        List<Future<Integer>> futures = new ArrayList<>();
        for (int qty : quantities) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                MvcResult res = mvc.perform(post("/api/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "customerId": 2, "showtimeId": 6, "quantity": %d }
                                        """.formatted(qty)))
                        .andReturn();
                return res.getResponse().getStatus();
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        long count201 = statuses.stream().filter(s -> s == 201).count();
        long count402 = statuses.stream().filter(s -> s == 402).count();
        long count409 = statuses.stream().filter(s -> s == 409).count();

        assertThat(count201).isEqualTo(4);
        assertThat(count402 + count409).isEqualTo(4);

        Integer finalSeats = jdbc.queryForObject(
                "SELECT available_seats FROM showtimes WHERE id = 6", Integer.class);
        Integer finalSold = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM bookings WHERE showtime_id = 6 AND status = 'CONFIRMED'",
                Integer.class);
        Integer missingPaymentRef = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE showtime_id = 6 AND status = 'CONFIRMED' AND payment_ref IS NULL",
                Integer.class);

        assertThat(finalSeats).isZero();
        assertThat(initialSeats).isEqualTo(finalSeats + (finalSold - initialSold));
        assertThat(missingPaymentRef).isZero();
    }
}
