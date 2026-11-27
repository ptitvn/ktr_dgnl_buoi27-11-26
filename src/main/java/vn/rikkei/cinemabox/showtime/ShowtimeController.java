package vn.rikkei.cinemabox.showtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    /** GET /api/showtimes/{id} */
    @GetMapping("/{id}")
    public ShowtimeResponse getById(@PathVariable Long id) {
        return showtimeService.getById(id);
    }
}
