package com.ecommerce.project.repository;

import com.ecommerce.project.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Every address on the account — the GDPR export reads these. */
    java.util.List<Address> findByUserUserIdOrderByAddressIdAsc(Long userId);
}
