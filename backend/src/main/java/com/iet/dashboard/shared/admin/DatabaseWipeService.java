package com.iet.dashboard.shared.admin;

import java.util.Map;

/**
 * The coordinator's \"empty everything\" switch: removes every operational row in the database —
 * courses, sections, allocations, faculty, their sign-in accounts, class logs, deadlines,
 * submissions, syllabus and uploads — while keeping the coordinator's own login, their faculty
 * profile. The semester settings and every uploaded file go too.
 */
public interface DatabaseWipeService {

    /** Row counts per table group that were cleared. */
    Map<String, Integer> wipeAll();
}
