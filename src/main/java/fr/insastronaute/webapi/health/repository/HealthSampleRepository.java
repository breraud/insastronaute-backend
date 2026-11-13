package fr.insastronaute.webapi.health.repository;

import fr.insastronaute.webapi.health.domain.HealthSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface HealthSampleRepository extends JpaRepository<HealthSample, java.util.UUID> {

    List<HealthSample> findBySampledAtGreaterThanEqualOrderBySampledAtAsc(Instant since);

    @Modifying
    @Query("delete from HealthSample sample where sample.sampledAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
