package com.foodloop.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.foodloop.report.ReportEnums.Kind;
import com.foodloop.report.ReportEnums.Status;

import jakarta.persistence.LockModeType;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndKindAndTargetRef(Long reporterId, Kind kind, Long targetRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> lockById(Long id);

    /** The queue: open reports first, oldest first inside each group. */
    @Query("""
            select r from Report r
            order by case when r.status = com.foodloop.report.ReportEnums.Status.OPEN then 0 else 1 end, r.createdAt, r.id
            """)
    Page<Report> findQueue(Pageable pageable);

    Page<Report> findByStatusOrderByCreatedAtAscIdAsc(Status status, Pageable pageable);

    /** The other reports about the same account, newest first, for the report view's "past reports". */
    List<Report> findTop10BySubjectIdAndIdNotOrderByCreatedAtDesc(Long subjectId, Long id);

    /** Suspending an account settles every open report about it. */
    @Modifying
    @Transactional
    @Query("""
            update Report r set r.status = com.foodloop.report.ReportEnums.Status.ACTIONED, r.resolvedBy = :adminId, r.resolvedAt = :now
            where r.subjectId = :subjectId and r.status = com.foodloop.report.ReportEnums.Status.OPEN
            """)
    int actionOpenAbout(Long subjectId, Long adminId, Instant now);
}
