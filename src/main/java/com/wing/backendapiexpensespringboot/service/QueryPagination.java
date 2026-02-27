package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.exception.AppException;

import java.util.Collections;
import java.util.List;

public final class QueryPagination {

    private static final int MAX_LIMIT = 200;

    private QueryPagination() {
    }

    public static void validate(int offset, int limit) {
        if (offset < 0) {
            throw AppException.badRequest("offset must be >= 0");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw AppException.badRequest("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static <T> List<T> slice(List<T> data, int offset, int limit) {
        if (data == null || data.isEmpty() || offset >= data.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(data.size(), offset + limit);
        return data.subList(offset, toIndex);
    }
}
