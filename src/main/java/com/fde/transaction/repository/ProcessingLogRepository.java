package com.fde.transaction.repository;

import com.fde.transaction.model.ProcessingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessingLogRepository extends JpaRepository<ProcessingLog, Long> {

    List<ProcessingLog> findByTransactionIdOrderByLoggedAtAsc(String transactionId);
}
