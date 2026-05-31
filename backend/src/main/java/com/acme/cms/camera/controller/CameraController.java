package com.acme.cms.camera.controller;

import com.acme.cms.camera.dto.CameraSaveReq;
import com.acme.cms.camera.dto.CameraVO;
import com.acme.cms.camera.entity.Camera;
import com.acme.cms.camera.service.CameraService;
import com.acme.cms.common.PageResp;
import com.acme.cms.common.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @GetMapping
    public R<PageResp<CameraVO>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String keyword) {
        Page<Camera> p = cameraService.page(page, size, keyword);
        PageResp<CameraVO> resp = PageResp.of(p.getTotal(), p.getCurrent(), p.getSize(),
                p.getRecords().stream().map(CameraVO::from).collect(Collectors.toList()));
        return R.ok(resp);
    }

    @GetMapping("/{id}")
    public R<CameraVO> get(@PathVariable Long id) {
        return R.ok(CameraVO.from(cameraService.get(id)));
    }

    @PostMapping
    public R<CameraVO> create(@RequestBody @Valid CameraSaveReq req) {
        Camera c = new Camera();
        BeanUtils.copyProperties(req, c);
        return R.ok(CameraVO.from(cameraService.create(c)));
    }

    @PutMapping("/{id}")
    public R<CameraVO> update(@PathVariable Long id, @RequestBody CameraSaveReq req) {
        Camera c = new Camera();
        BeanUtils.copyProperties(req, c);
        return R.ok(CameraVO.from(cameraService.update(id, c)));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        cameraService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<CameraVO> test(@PathVariable Long id) throws Exception {
        Camera c = cameraService.get(id);
        return R.ok(CameraVO.from(cameraService.refreshCapabilities(c)));
    }
}
