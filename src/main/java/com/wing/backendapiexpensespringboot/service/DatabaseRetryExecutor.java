package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseRetryExecutor {

    private final AppConfig appConfig;

    public void run(String operation, Runnable action) {
        execute(operation, () -> {
            action.run();
            return null;
        });
    }

    public <T> T execute(String operation, Supplier<T> supplier) {
        AppConfig.DatabaseRetry retry = appConfig.getDatabaseRetry();
        int maxAttempts = retry.getMaxAttempts();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (!isTransient(exception) || attempt >= maxAttempts) {
                    throw exception;
                }

                long backoffMs = calculateBackoffMillis(attempt, retry);
                log.warn(
                        "Transient database failure during {} (attempt {}/{}). Retrying in {} ms: {}",
                        operation,
                        attempt,
                        maxAttempts,
                        backoffMs,
                        exception.getMessage());
                sleep(backoffMs);
            }
        }

        throw lastFailure;
    }

    private long calculateBackoffMillis(int attempt, AppConfig.DatabaseRetry retry) {
        double exponentialBackoff = retry.getInitialBackoffMs() * Math.pow(retry.getMultiplier(), attempt - 1L);
        long cappedBackoff = (long) Math.min(exponentialBackoff, retry.getMaxBackoffMs());
        return Math.max(cappedBackoff, 0L);
    }

    private void sleep(long backoffMs) {
        if (backoffMs <= 0L) {
            return;
        }

        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying a transient database failure.",
                    interruptedException);
        }
    }

    private boolean isTransient(Throwable throwable) {
        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;

        while (current != null && visited.add(current)) {
            if (current instanceof CannotCreateTransactionException
                    || current instanceof TransientDataAccessException
                    || current instanceof QueryTimeoutException
                    || current instanceof TransactionTimedOutException
                    || current instanceof JDBCConnectionException
                    || current instanceof SQLTransientException
                    || current instanceof SQLRecoverableException) {
                return true;
            }

            if (current instanceof SQLException sqlException && isTransientSqlState(sqlException.getSQLState())) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isTransientSqlState(String sqlState) {
        if (sqlState == null || sqlState.isBlank()) {
            return false;
        }

        return sqlState.startsWith("08")
                || sqlState.startsWith("53")
                || sqlState.startsWith("57P")
                || "40001".equals(sqlState)
                || "40P01".equals(sqlState);
    }
}
