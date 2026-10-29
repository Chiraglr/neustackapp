package org.example.neustackapp.repository;

import org.example.neustackapp.model.SampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository
public interface Repository extends JpaRepository<SampleEntity, Long> {
}