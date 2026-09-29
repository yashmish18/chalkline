package com.iet.dashboard.shared.events;

import java.time.Instant;
import java.util.List;

public record DeadlineAssignedEvent(
        Long deadlineId,
        String title,
        Instant dueAt,
        List<Long> facultyIds
) {}
