package com.exelynt.booking.audit;

/** Every action that produces an {@link AuditLog} row. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    TOKEN_REFRESH,
    TOKEN_REFRESH_FAILURE,
    RESOURCE_CREATED,
    RESOURCE_UPDATED,
    RESOURCE_DELETED,
    RESERVATION_CREATED,
    RESERVATION_UPDATED,
    RESERVATION_STATUS_CHANGED,
    RESERVATION_DELETED
}
