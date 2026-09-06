package com.ecommerce.project.service.order;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.AddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutGuardsTest {

    @Mock private AddressRepository addressRepository;
    @InjectMocks private CheckoutGuards guards;

    private Address addressOwnedBy(String email) {
        User user = new User();
        user.setEmail(email);
        Address address = new Address("Line 1", "Line 2", "City", "State", "Country", "000000");
        address.setAddressId(7L);
        address.setUser(user);
        return address;
    }

    @Test
    void resolveOwnedAddress_returnsTheAddressWhenItBelongsToTheCaller() {
        when(addressRepository.findById(7L)).thenReturn(Optional.of(addressOwnedBy("buyer@example.com")));

        Address resolved = guards.resolveOwnedAddress("BUYER@example.com", 7L);

        assertThat(resolved.getAddressId()).isEqualTo(7L);
    }

    @Test
    void resolveOwnedAddress_unknownId_isNotFound() {
        when(addressRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guards.resolveOwnedAddress("buyer@example.com", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveOwnedAddress_someoneElsesAddress_isRejected() {
        when(addressRepository.findById(7L)).thenReturn(Optional.of(addressOwnedBy("owner@example.com")));

        assertThatThrownBy(() -> guards.resolveOwnedAddress("intruder@example.com", 7L))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void resolveOwnedAddress_addressWithNoUser_isRejected() {
        Address orphan = new Address("Line 1", "Line 2", "City", "State", "Country", "000000");
        when(addressRepository.findById(7L)).thenReturn(Optional.of(orphan));

        assertThatThrownBy(() -> guards.resolveOwnedAddress("buyer@example.com", 7L))
                .isInstanceOf(APIException.class);
    }

    @Test
    void requireActiveItems_rejectsNullAndEmpty() {
        assertThatThrownBy(() -> guards.requireActiveItems(null)).isInstanceOf(APIException.class);
        assertThatThrownBy(() -> guards.requireActiveItems(List.of())).isInstanceOf(APIException.class);
    }

    @Test
    void requireActiveItems_passesWhenSomethingIsInTheCart() {
        assertThatCode(() -> guards.requireActiveItems(List.of(new CartItem())))
                .doesNotThrowAnyException();
    }
}
