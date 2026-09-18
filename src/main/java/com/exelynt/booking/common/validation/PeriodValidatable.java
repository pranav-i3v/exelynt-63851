package com.exelynt.booking.common.validation;

import java.time.LocalDateTime;

/** Implemented by request DTOs that carry a start/end period. */
public interface PeriodValidatable {

    LocalDateTime startTime();

    LocalDateTime endTime();
}
