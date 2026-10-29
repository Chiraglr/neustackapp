package org.example.neustackapp.controller;

import org.example.neustackapp.dto.RequestDto;
import org.example.neustackapp.model.SampleEntity;
import org.example.neustackapp.service.Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class Controller {

    private final Service service;

    @GetMapping
    public ResponseEntity<List<SampleEntity>> getAllResources() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SampleEntity> getResourceById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<SampleEntity> createResource(@Valid @RequestBody RequestDto requestDto) {
        SampleEntity created = service.create(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}