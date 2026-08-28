package com.ecommerce.project.repository;

import com.ecommerce.project.model.InvoiceNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceNumberSequenceRepository extends JpaRepository<InvoiceNumberSequence, Integer> {

    /**
     * Creates the counter row for a year if it is missing. {@code ON CONFLICT DO
     * NOTHING} makes it safe to call concurrently and never raises, so it cannot
     * poison the caller's transaction the way a racing INSERT would.
     */
    @Modifying
    @Query(value = "INSERT INTO invoice_number_sequences (fiscal_year, last_value) "
            + "VALUES (:fiscalYear, 0) ON CONFLICT (fiscal_year) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("fiscalYear") int fiscalYear);

    /**
     * Loads the counter row under a row-level write lock ({@code SELECT ... FOR
     * UPDATE}). Concurrent issuers block here until the holder's transaction
     * ends, which is what serialises number assignment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceNumberSequence s where s.fiscalYear = :fiscalYear")
    Optional<InvoiceNumberSequence> findByFiscalYearForUpdate(@Param("fiscalYear") int fiscalYear);
}
