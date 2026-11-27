package vn.rikkei.cinemabox.customer;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.cinemabox.common.BadRequestException;
import vn.rikkei.cinemabox.common.ConflictException;
import vn.rikkei.cinemabox.common.PageResponse;
import vn.rikkei.cinemabox.common.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest req) {
        String email = req.email().trim();
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email đã tồn tại trong hệ thống: " + email);
        }

        Customer customer = new Customer();
        customer.setFullName(req.fullName().trim());
        customer.setEmail(email);
        customer.setPhone(req.phone());
        customer.setMemberLevel(req.memberLevel() == null ? "STANDARD" : req.memberLevel());
        customer.setCreatedAt(LocalDateTime.now());

        Customer saved = customerRepository.save(customer);
        return CustomerResponse.from(saved);
    }

    public CustomerResponse getById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + id));
        return CustomerResponse.from(customer);
    }

    public PageResponse<CustomerResponse> list(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Tham số page không được âm");
        }
        if (size < 1 || size > 50) {
            throw new BadRequestException("Tham số size phải nằm trong khoảng từ 1 đến 50");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        Page<CustomerResponse> result = customerRepository.findAll(pageable).map(CustomerResponse::from);
        return PageResponse.from(result);
    }
}
