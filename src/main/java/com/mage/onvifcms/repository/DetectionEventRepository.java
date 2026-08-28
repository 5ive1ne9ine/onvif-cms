package com.mage.onvifcms.repository;

import com.mage.onvifcms.domain.DetectionEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {
    @EntityGraph(attributePaths = "camera")
    List<DetectionEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
    long countByOccurredAtAfter(Instant since);
}
