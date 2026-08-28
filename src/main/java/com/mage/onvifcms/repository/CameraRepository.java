package com.mage.onvifcms.repository;

import com.mage.onvifcms.domain.Camera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    Optional<Camera> findByStableKey(String stableKey);
    List<Camera> findByDetectionEnabledTrueAndOnlineTrue();
}

