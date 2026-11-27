package vn.rikkei.cinemabox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class Pha1VerifyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("DROP TABLE IF EXISTS reviews CASCADE");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema.sql"),
                new ClassPathResource("data.sql")
        );
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(jdbc.getDataSource());
    }

    private String readSql(String relativePath) throws Exception {
        String raw = Files.readString(Path.of(relativePath));
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    @Test
    void testSqlQueriesAndF1Table() throws Exception {
        // I1
        List<Map<String, Object>> i1 = jdbc.queryForList(readSql("sql/intern/i1_phim_hanh_dong.sql"));
        assertThat(i1).hasSize(4);
        assertThat(i1.get(0).get("title")).isEqualTo("Cuộc Đua Cuối Cùng");
        assertThat(i1.get(1).get("title")).isEqualTo("Biệt Đội Sấm Sét");

        // I2
        List<Map<String, Object>> i2 = jdbc.queryForList(readSql("sql/intern/i2_suat_chieu_05_12.sql"));
        assertThat(i2).hasSize(6);
        assertThat(i2.get(0).get("room_name")).isEqualTo("Phòng 3"); // 00:00:00
        assertThat(i2.get(2).get("room_name")).isEqualTo("Phòng 2"); // 14:00:00 Phòng 2 trước Phòng IMAX
        assertThat(i2.get(3).get("room_name")).isEqualTo("Phòng IMAX");

        // I3
        List<Map<String, Object>> i3 = jdbc.queryForList(readSql("sql/intern/i3_ve_cua_khach.sql"));
        assertThat(i3).hasSize(3);
        assertThat(i3.get(0).get("booking_id")).isEqualTo(1L);
        assertThat(i3.get(1).get("booking_id")).isEqualTo(13L);
        assertThat(i3.get(2).get("booking_id")).isEqualTo(2L);

        // F1
        jdbc.execute(readSql("sql/fresher/f1_reviews.sql"));
        jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating, content) VALUES (1, 1, 5, 'Hay')");
        Map<String, Object> rev = jdbc.queryForMap("SELECT * FROM reviews WHERE customer_id = 1 AND movie_id = 1");
        assertThat(rev.get("created_at")).isNotNull();

        // Duplicate customer_id + movie_id -> fail
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (1, 1, 4)"))
                .isInstanceOf(DataAccessException.class);
        // Invalid rating 0 or 6 -> fail
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (1, 2, 0)"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (1, 2, 6)"))
                .isInstanceOf(DataAccessException.class);
        // Non-existent FK -> fail
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reviews (customer_id, movie_id, rating) VALUES (999, 1, 3)"))
                .isInstanceOf(DataAccessException.class);

        // F2
        List<Map<String, Object>> f2 = jdbc.queryForList(readSql("sql/fresher/f2_doanh_thu_theo_phim.sql"));
        assertThat(f2).hasSize(10);
        assertThat(f2.get(0).get("title")).isEqualTo("Biệt Đội Sấm Sét");
        assertThat(((Number) f2.get(0).get("so_ve")).longValue()).isEqualTo(9L);
        assertThat(((Number) f2.get(0).get("doanh_thu")).longValue()).isEqualTo(1020000L);
        assertThat(f2.get(9).get("title")).isEqualTo("Vũ Điệu Rồng Con");
        assertThat(((Number) f2.get(9).get("so_ve")).longValue()).isEqualTo(0L);
        assertThat(((Number) f2.get(9).get("doanh_thu")).longValue()).isEqualTo(0L);

        // F3
        List<Map<String, Object>> f3 = jdbc.queryForList(readSql("sql/fresher/f3_khach_chi_tieu_cao.sql"));
        assertThat(f3).hasSize(4);
        assertThat(f3.get(0).get("email")).isEqualTo("binh.tran@example.com");
        assertThat(((Number) f3.get(0).get("tong_chi_tieu")).longValue()).isEqualTo(800000L);
        assertThat(((Number) f3.get(0).get("so_lan_dat")).longValue()).isEqualTo(3L);
    }

    @Test
    void testShowtimeApi() throws Exception {
        mvc.perform(get("/api/showtimes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.movieTitle").value("Biệt Đội Sấm Sét"))
                .andExpect(jsonPath("$.roomName").value("Phòng IMAX"))
                .andExpect(jsonPath("$.startTime").value("2026-12-04T19:00:00"))
                .andExpect(jsonPath("$.price").value(120000))
                .andExpect(jsonPath("$.availableSeats").value(35));

        mvc.perform(get("/api/showtimes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void testCustomerEndpoints() throws Exception {
        // Create valid customer without optional fields
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Trần Thị Thu",
                                  "email": "thu.tran@example.com",
                                  "phone": "0912345678"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.fullName").value("Trần Thị Thu"))
                .andExpect(jsonPath("$.email").value("thu.tran@example.com"))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.memberLevel").value("STANDARD"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Duplicate email -> 409 CONFLICT
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Người Khác",
                                  "email": "an.nguyen@example.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        // Invalid fields -> 400 VALIDATION_FAILED
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "   ",
                                  "email": "not-an-email",
                                  "phone": "12345",
                                  "memberLevel": "VIP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(4));

        // Invalid JSON -> 400
        mvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ bad json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // GET by ID
        mvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("an.nguyen@example.com"));

        mvc.perform(get("/api/customers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        // GET paginated
        mvc.perform(get("/api/customers?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(9))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.totalPages").value(1));

        // Page beyond totalPages -> 200 with empty content
        mvc.perform(get("/api/customers?page=5&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(9));

        // Invalid pagination params -> 400 BAD_REQUEST
        mvc.perform(get("/api/customers?page=-1&size=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/customers?page=0&size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/customers?page=0&size=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mvc.perform(get("/api/customers?page=abc&size=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
