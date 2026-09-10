package com.servicedesk.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByStatusAndCreatedAtBetween(IncidentStatus status, Instant from, Instant to);

    long countByStatusAndRemediationAttemptsGreaterThanAndCreatedAtBetween(
            IncidentStatus status, int attempts, Instant from, Instant to);

    @Query("select i.severity, count(i) from Incident i " +
            "where i.createdAt between :from and :to group by i.severity")
    List<Object[]> countBySeverityBucket(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select i.category, count(i) from Incident i " +
            "where i.createdAt between :from and :to group by i.category")
    List<Object[]> countByCategoryBucket(@Param("from") Instant from, @Param("to") Instant to);

    /** Native query: JPQL has no portable way to diff two timestamps, so this uses Postgres EXTRACT(EPOCH). */
    @Query(value = "select avg(extract(epoch from (resolved_at - created_at)) / 60.0) " +
            "from incidents where resolved_at is not null and created_at between :from and :to",
            nativeQuery = true)
    Double averageResolutionTimeMinutes(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select date_trunc('day', created_at) as day, count(*) as total " +
            "from incidents where created_at between :from and :to group by day order by day",
            nativeQuery = true)
    List<Object[]> countCreatedPerDay(@Param("from") Instant from, @Param("to") Instant to);
}

