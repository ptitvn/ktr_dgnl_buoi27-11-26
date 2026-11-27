package vn.rikkei.cinemabox.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO nhận dữ liệu tạo khách hàng mới. */
public record CreateCustomerRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng", regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng 0")
        String phone,

        @Pattern(regexp = "^(STANDARD|GOLD)$", message = "Hạng thành viên chỉ nhận STANDARD hoặc GOLD")
        String memberLevel
) {
}
