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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final ModelMapper modelMapper;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final AuthUtil authUtil;


    @Override
    public AddressDTO createAddress(AddressDTO addressDTO, User user) {

        Address address = modelMapper.map(addressDTO, Address.class);
        List<Address> addressesList = user.getAddresses();
        addressesList.add(address);
        user.setAddresses(addressesList);
        address.setUser(user);
        Address saveAddressService =  addressRepository.save(address);
        return modelMapper.map(saveAddressService,AddressDTO.class);
    }

    @Override
    public List<AddressDTO> getAddresses() {

        List<Address> addresses = addressRepository.findAll();
        return addresses.stream()
                .map(address->modelMapper.map(address,AddressDTO.class))
                .toList();
    }

    @Override
    public AddressDTO getAddressesById(Long addressesId) {

        Address address = getOwnedAddress(addressesId);
        return modelMapper.map(address,AddressDTO.class);
    }

    @Override
    public List<AddressDTO> getUserAddresses(User user) {
        List<Address> addresses = user.getAddresses();
        return  addresses.stream()
                .map(address->modelMapper.map(address,AddressDTO.class))
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
