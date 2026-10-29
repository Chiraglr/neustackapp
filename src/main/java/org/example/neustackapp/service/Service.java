package org.example.neustackapp.service;

import org.example.neustackapp.dto.RequestDto;
import org.example.neustackapp.exception.ResourceNotFoundException;
import org.example.neustackapp.model.SampleEntity;
import org.example.neustackapp.repository.Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class Service {

    private final Repository repository;

    @Transactional(readOnly = true)
    public List<SampleEntity> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public SampleEntity findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource with id " + id + " not found."));
    }

    @Transactional
    public SampleEntity create(RequestDto dto) {
        SampleEntity entity = new SampleEntity();
        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());
        return repository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Cannot delete. Resource with id " + id + " not found.");
        }
        repository.deleteById(id);
    }
}