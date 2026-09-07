package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.repository.AddressRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.service.AddressService;
import com.ecommerce.project.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.SortWhitelist;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final ModelMapper modelMapper;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AuthUtil authUtil;


    /**
     * {@code Address.user} is the owning side, so setting it and saving is the
     * whole persist. The previous version also pushed the new address into
     * {@code user.getAddresses()} — the inverse side, which JPA never writes —
     * and that touch of a LAZY collection on a detached User threw
     * LazyInitializationException before the insert was ever attempted.
     */
    @Override
    public AddressDTO createAddress(AddressDTO addressDTO, User user) {
        Address address = modelMapper.map(addressDTO, Address.class);
        address.setUser(user);
        return modelMapper.map(addressRepository.save(address), AddressDTO.class);
    }

    /**
     * The admin address list, one page at a time.
     *
     * <p>Was an unbounded {@code findAll()}: every address in the system, and
     * because {@code Address.user} is an EAGER {@code @ManyToOne} whose
     * {@code User} eagerly pulls its roles, every one of them dragged a user and
     * a roles query along. Fine on a seeded database, an out-of-memory error on
     * a real one.
     */
    @Override
    public List<AddressDTO> getAddresses(Integer pageNumber, Integer pageSize,
                                         String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                "addressId", SortWhitelist.ADDRESS);

        return addressRepository.findAll(pageDetails).getContent().stream()
                .map(address -> modelMapper.map(address, AddressDTO.class))
                .toList();
    }

    @Override
    public AddressDTO getAddressesById(Long addressesId) {

        Address address = getOwnedAddress(addressesId);
        return modelMapper.map(address,AddressDTO.class);
    }

    /**
     * The addresses on one account.
     *
     * <p>Queried by user id rather than walked off {@code user.getAddresses()}:
     * {@code AuthUtil.loggedInUser()} hands back a detached User — its own short
     * repository transaction has already committed — so with
     * {@code open-in-view=false} reading that LAZY collection threw
     * LazyInitializationException, a 500 on every checkout. Ordered so the
     * address list does not reshuffle between loads.
     */
    @Override
    public List<AddressDTO> getUserAddresses(User user) {
        return addressRepository.findByUserUserIdOrderByAddressIdAsc(user.getUserId()).stream()
                .map(address -> modelMapper.map(address, AddressDTO.class))
                .toList();
    }

    @Override
    public AddressDTO updateAddress(Long addressId, AddressDTO addressDTO) {
        Address address = getOwnedAddress(addressId);

        Address updated = modelMapper.map(addressDTO, Address.class);
        updated.setAddressId(address.getAddressId());
        updated.setUser(address.getUser());
        return modelMapper.map(addressRepository.save(updated), AddressDTO.class);
    }

    @Override
    public String deleteAddress(Long addressId) {
        Address address = getOwnedAddress(addressId);

        addressRepository.delete(address);

        return "Address deleted successfully with addressId: " + addressId;
    }

    private Address getOwnedAddress(Long addressId) {
        User current = authUtil.loggedInUser();
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));

        boolean admin = current.getRoles().stream()
                .anyMatch(r -> r.getRoleName() == AppRole.ROLE_ADMIN);
        if (!admin && (address.getUser() == null
                || !address.getUser().getUserId().equals(current.getUserId()))) {
            throw new APIException("You are not allowed to access this address");
        }
        return address;
    }

}
