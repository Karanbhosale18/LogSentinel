package com.digiplus.loganalyzer.repository;

import com.digiplus.loganalyzer.entity.LogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    /**
     * Filtered, paginated search. All filters are optional (null => ignored).
     * @param q should be pre-lowercased by the caller.
     *
     * NOTE: {@code CAST(:q AS string)} is required, not cosmetic. When q is null,
     * PostgreSQL binds the untyped parameter as {@code bytea} and the query fails at
     * plan time with "operator does not exist: text ~~ bytea" — before the
     * {@code :q IS NULL} guard can short-circuit at runtime. The cast pins the
     * parameter to a text type so the LIKE resolves. (H2 tolerates the untyped null,
     * which is why unit tests don't surface this; keep the cast for the PG runtime.)
     */
    @Query("""
            SELECT l FROM LogEntry l
            WHERE (:anomaly IS NULL OR l.anomaly = :anomaly)
              AND (:status IS NULL OR l.statusCode = :status)
              AND (:q IS NULL
                   OR LOWER(COALESCE(l.ipAddress, '')) LIKE CONCAT('%', CAST(:q AS string), '%')
                   OR LOWER(COALESCE(l.location, '')) LIKE CONCAT('%', CAST(:q AS string), '%')
                   OR LOWER(COALESCE(l.requestType, '')) LIKE CONCAT('%', CAST(:q AS string), '%')
                   OR LOWER(COALESCE(l.message, '')) LIKE CONCAT('%', CAST(:q AS string), '%'))
            """)
    Page<LogEntry> search(@Param("anomaly") Boolean anomaly,
                          @Param("status") Integer status,
                          @Param("q") String q,
                          Pageable pageable);

    long countByAnomalyTrue();

    @Query("SELECT COUNT(l) FROM LogEntry l WHERE l.statusCode >= 500")
    long countServerErrors();

    @Query("SELECT COUNT(l) FROM LogEntry l WHERE l.statusCode >= 400 AND l.statusCode < 500")
    long countClientErrors();

    @Query("SELECT COUNT(DISTINCT l.ipAddress) FROM LogEntry l")
    long countDistinctIps();

    @Query("SELECT l.statusCode, COUNT(l) FROM LogEntry l WHERE l.statusCode IS NOT NULL GROUP BY l.statusCode ORDER BY l.statusCode")
    List<Object[]> statusDistribution();
}
