package vn.rikkei.cinemabox.common;

/** Ném ra khi tham số đầu vào không hợp lệ (trả về 400 BAD_REQUEST). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
