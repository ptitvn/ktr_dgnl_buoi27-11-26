package vn.rikkei.cinemabox.common;

/** Ném ra khi trạng thái bản ghi không cho phép thực hiện thao tác (trả về 409 INVALID_STATE). */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
