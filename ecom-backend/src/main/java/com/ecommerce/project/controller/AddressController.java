package com.ecommerce.project.controller;

import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.service.AddressService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AddressController extends BaseController {


    private final AddressService addressService;
    private final AuthUtil authUtil;

    public AddressController(AddressService addressService, AuthUtil authUtil) {
        this.addressService = addressService;
        this.authUtil = authUtil;
    }

    @Tag(name = "Address")
    @PostMapping("/addresses")
    public ResponseEntity<AddressDTO> createAddress(@Valid @RequestBody AddressDTO addressDTO ) {
        User user = authUtil.loggedInUser();
        AddressDTO savedAddressDTO = addressService.createAddress(addressDTO,user);
        return created(savedAddressDTO);
    }

    @Tag(name = "Address")
    @GetMapping("/addresses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AddressDTO>> getAddresses(@ModelAttribute PaginationParams params) {
        List<AddressDTO> addressList = addressService.getAddresses(
                params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder());
        return ok(addressList);
    }


    @Tag(name = "Address")
    @GetMapping("/addresses/{addressesId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AddressDTO> getAddressById(@PathVariable Long addressesId) {
        AddressDTO addressDTO = addressService.getAddressesById(addressesId);
        return ok(addressDTO);
    }

    @Tag(name = "Address")
    @GetMapping("/users/addresses")
    public ResponseEntity<List<AddressDTO>> getUserAddresses() {
        User user = authUtil.loggedInUser();
        List<AddressDTO> addressList = addressService.getUserAddresses(user);
        return ok(addressList);

    }

    @Tag(name = "Address")
    @PutMapping("/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AddressDTO> updateAddress(@PathVariable Long addressId,
                                                    @Valid @RequestBody AddressDTO addressDTO) {
        AddressDTO updatedAddress = addressService.updateAddress(addressId, addressDTO);
        return ok(updatedAddress);
    }

    @Tag(name = "Address")
    @DeleteMapping("/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> deleteAddress(@PathVariable Long addressId){
        String status = addressService.deleteAddress(addressId);
        return ok(status);
    }



}
