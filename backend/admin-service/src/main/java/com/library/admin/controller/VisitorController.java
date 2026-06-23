package com.library.admin.controller;

import com.library.admin.entity.VisitorEvent;
import com.library.admin.repository.VisitorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/visitor")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VisitorController {

    private final VisitorEventRepository visitorEventRepository;

    @PostMapping("/track")
    public ResponseEntity<Void> track(@RequestBody(required = false) Map<String, String> body) {
        String page = (body != null) ? body.getOrDefault("page", "/") : "/";
        visitorEventRepository.save(VisitorEvent.builder().page(page).build());
        return ResponseEntity.ok().build();
    }
}
