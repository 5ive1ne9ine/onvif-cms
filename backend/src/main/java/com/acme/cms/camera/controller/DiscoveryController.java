package com.acme.cms.camera.controller;

import com.acme.cms.camera.service.DiscoveryService;
import com.acme.cms.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @PostMapping("/scan")
    public R<List<DiscoveryService.DiscoveredDevice>> scan(
            @RequestParam(defaultValue = "4000") int timeoutMs) throws Exception {
        return R.ok(discoveryService.scan(timeoutMs));
    }
}
