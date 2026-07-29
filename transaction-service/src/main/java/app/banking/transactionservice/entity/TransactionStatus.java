package app.banking.transactionservice.entity;

/**
 * Transaction LifeCycle flow
 *
 * PENDING -> PROCESSING -> COMPLETED (CLEAN TRANSACTION)
 *                       -> PENDING_VERIFICATION (SUSPICIOUS DETECTED)
 *                                 -> COMPLETED (VERIFIED)
 *                                 -> FLAGGED (SAGA REFUND)
 *                       -> FAILED
 *                       -> FLAGGED
 */

public enum TransactionStatus {

    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    FLAGGED,
    PENDING_VERIFICATION

}
