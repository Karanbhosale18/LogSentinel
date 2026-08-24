package com.digiplus.loganalyzer.repository;

import com.digiplus.loganalyzer.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {
    Optional<AiAnalysis> findByLogEntryId(Long logEntryId);
}
