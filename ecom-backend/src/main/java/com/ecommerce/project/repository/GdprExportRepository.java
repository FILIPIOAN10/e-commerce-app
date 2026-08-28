package com.ecommerce.project.repository;

import com.ecommerce.project.model.GdprExport;
import com.ecommerce.project.model.GdprExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GdprExportRepository extends JpaRepository<GdprExport, Long> {

    /**
     * The user's most recent request in either live state. Drives the "you
     * already have one in flight / ready" branch, so a user cannot queue an
     * unbounded number of full-account archives.
     */
    @Query("""
            SELECT e FROM GdprExport e
            WHERE e.user.userId = :userId
              AND e.status IN :statuses
              AND e.expiresAt > :now
            ORDER BY e.id DESC
            """)
    List<GdprExport> findLiveForUser(@Param("userId") Long userId,
                                     @Param("statuses") List<GdprExportStatus> statuses,
                                     @Param("now") Instant now);

    default Optional<GdprExport> findLatestLiveForUser(Long userId, Instant now) {
        return findLiveForUser(userId,
                List.of(GdprExportStatus.PENDING, GdprExportStatus.READY), now)
                .stream().findFirst();
    }

    /** Archives whose TTL has passed and that still hold bytes. */
    @Query("SELECT e FROM GdprExport e WHERE e.expiresAt <= :now AND e.payload IS NOT NULL")
    List<GdprExport> findPurgeable(@Param("now") Instant now);
}
