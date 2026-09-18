package com.exelynt.booking.common.logging;

import org.slf4j.MDC;

/** Helper for the {@code username} MDC entry populated once a request is authenticated. */
public final class MdcUser {

    public static final String MDC_KEY = "username";

    private MdcUser() {
    }

    public static void set(String username) {
        if (username != null && !username.isBlank()) {
            MDC.put(MDC_KEY, username);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
