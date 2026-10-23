package com.fde.transaction.repository;

import com.fde.transaction.enums.TransactionStatus;
import com.fde.transaction.model.TransactionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRequestRepository extends JpaRepository<TransactionRequest, Long> {

    Optional<TransactionRequest> findByRequestId(String requestId);

    Optional<TransactionRequest> findByTransactionId(String transactionId);

    List<TransactionRequest> findByAccountId(String accountId);

    List<TransactionRequest> findByStatusIn(List<TransactionStatus> statuses);

    @Query("SELECT tr FROM TransactionRequest tr WHERE tr.status = :status ORDER BY tr.sequenceNumber ASC")
    List<TransactionRequest> findByStatusOrderBySequenceNumberAsc(@Param("status") TransactionStatus status);

    @Query("SELECT tr FROM TransactionRequest tr WHERE tr.accountId = :accountId AND tr.sequenceNumber < :seqNum AND tr.status NOT IN :terminalStatuses")
    List<TransactionRequest> findUnresolvedPredecessors(@Param("accountId") String accountId,
                                                        @Param("seqNum") Integer seqNum,
                                                        @Param("terminalStatuses") List<TransactionStatus> terminalStatuses);

    long countByStatus(TransactionStatus status);
}
