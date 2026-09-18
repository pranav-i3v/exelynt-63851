package com.exelynt.booking.user;

/** Application roles. Stored as a string so the DB stays readable. */
public enum Role {
    ADMIN,
    USER;

    /** Spring Security authority name for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
