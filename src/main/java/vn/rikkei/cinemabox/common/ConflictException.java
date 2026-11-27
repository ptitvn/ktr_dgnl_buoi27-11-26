package vn.rikkei.cinemabox.common;

/** Ném ra khi dữ liệu xung đột, ví dụ trùng email (trả về 409 CONFLICT). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
