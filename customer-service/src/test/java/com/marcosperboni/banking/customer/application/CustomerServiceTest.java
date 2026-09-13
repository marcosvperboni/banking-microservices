package com.marcosperboni.banking.customer.application;

import com.marcosperboni.banking.common.exception.ResourceNotFoundException;
import com.marcosperboni.banking.customer.domain.Customer;
import com.marcosperboni.banking.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByIdReturnsCustomerWhenPresent() {
        UUID id = UUID.randomUUID();
        Customer customer = Customer.register("Jane Doe", "jane@example.com", "12345678901", "hashed");
        when(customerRepository.findById(id)).thenReturn(Optional.of(customer));

        assertThat(customerService.findById(id)).isEqualTo(customer);
    }

    @Test
    void updateThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(id, new UpdateCustomerCommand("New Name", "new@example.com")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(customerRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> customerService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
