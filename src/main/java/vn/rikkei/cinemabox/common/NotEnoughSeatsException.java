package vn.rikkei.cinemabox.common;

/** Ném ra khi suất chiếu không còn đủ ghế trống (trả về 409 NOT_ENOUGH_SEATS). */
public class NotEnoughSeatsException extends RuntimeException {

    public NotEnoughSeatsException(String message) {
        super(message);
    }
}
