package com.babymonitor.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Runs work once the surrounding transaction has committed.
 *
 * <p>Used for side effects that must not happen when a write rolls back: a
 * camera's Motion process is only signalled after the flag that asked for it is
 * safely stored. With no transaction in progress the action runs immediately.
 */
@ApplicationScoped
public class AfterCommit {

    @Inject
    TransactionSynchronizationRegistry transactions;

    public void run(Runnable action) {
        if (transactions.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            action.run();
            return;
        }
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    action.run();
                }
            }
        });
    }
}
