package com.exelynt.booking.audit.common;

import com.exelynt.booking.audit.entity.AuditLog;

/** Every action that produces an {@link AuditLog} row. */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    TOKEN_REFRESH,
    TOKEN_REFRESH_FAILURE,
    REFRESH_TOKEN_REUSE_DETECTED,
    RESOURCE_CREATED,
    RESOURCE_UPDATED,
    RESOURCE_DELETED,
    RESERVATION_CREATED,
    RESERVATION_UPDATED,
    RESERVATION_STATUS_CHANGED,
    RESERVATION_DELETED
}
