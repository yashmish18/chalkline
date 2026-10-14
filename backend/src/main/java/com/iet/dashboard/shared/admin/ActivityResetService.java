package com.iet.dashboard.shared.admin;

/** Clears the activity layer (logs, assessments, plans, timetable, deadlines) so it can be re-tested. */
public interface ActivityResetService {

    /**
     * @param includeSyllabus also remove the curriculum topic lists, not just the activity on top of them
     */
    ResetResult resetActivity(boolean includeSyllabus);

    record ResetResult(
            long sessions,
            long assessments,
            long remedialPlans,
            long timetableSlots,
            long deadlines,
            long notifications,
            long syllabusTopics
    ) {}
}
