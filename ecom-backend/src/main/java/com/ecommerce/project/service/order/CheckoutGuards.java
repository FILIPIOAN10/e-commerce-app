package com.ecommerce.project.service.order;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The preconditions every checkout entry point shares. {@code placeOrder},
 * {@code previewOrder} and {@code calculateShippingCost} each re-implemented
 * these, and they drifted — the shipping quote skipped the address-ownership
 * check for a while, so a signed-in user could probe other accounts' address ids
 * from the rate. One component, one implementation.
 *
 * <p>Kept as two explicit methods rather than an ordered {@code List<Check>}
 * chain: there are only two guards, one of them resolves and returns the address
 * the caller then needs, and a throw-only chain would still leave that lookup
 * duplicated. Revisit if a third precondition appears.
 */
@Component
@RequiredArgsConstructor
public class CheckoutGuards {

    private final AddressRepository addressRepository;

    /**
     * The address for {@code addressId}, confirmed to belong to {@code email}.
     *
     * @throws ResourceNotFoundException no address with that id
     * @throws APIException              the address belongs to someone else
     */
    public Address resolveOwnedAddress(String email, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
        if (address.getUser() == null || !email.equalsIgnoreCase(address.getUser().getEmail())) {
            throw new APIException("Address does not belong to the current user");
        }
        return address;
    }

    /**
     * Rejects a checkout whose cart is missing or holds nothing but
     * saved-for-later lines — those are excluded from the cart total, so an order
     * built from them would ship uncharged items.
     */
    public void requireActiveItems(List<CartItem> activeCartItems) {
        if (activeCartItems == null || activeCartItems.isEmpty()) {
            throw new APIException("Cart has no active items to purchase");
        }
    }
}
