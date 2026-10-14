package com.iet.dashboard.shared.admin;

import com.iet.dashboard.academics.repo.AssessmentRepository;
import com.iet.dashboard.academics.repo.RemedialPlanRepository;
import com.iet.dashboard.academics.repo.SyllabusTopicRepository;
import com.iet.dashboard.academics.repo.TimetableSlotRepository;
import com.iet.dashboard.deadlines.repo.DeadlineAssignmentRepository;
import com.iet.dashboard.deadlines.repo.DeadlineRepository;
import com.iet.dashboard.notifications.repo.NotificationRepository;
import com.iet.dashboard.teaching.repo.TeachingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes everything the activity layer accumulated: class logs, continuous assessments, remedial
 * plans, the timetable, deadlines and their submissions, and notifications. Allocations, courses,
 * faculty and documents are left untouched. This is the operator's "start again" switch.
 */
@Service
@Transactional
public class ActivityResetServiceImpl implements ActivityResetService {

    private final TeachingSessionRepository sessionRepository;
    private final AssessmentRepository assessmentRepository;
    private final RemedialPlanRepository remedialRepository;
    private final TimetableSlotRepository slotRepository;
    private final DeadlineAssignmentRepository assignmentRepository;
    private final DeadlineRepository deadlineRepository;
    private final NotificationRepository notificationRepository;
    private final SyllabusTopicRepository syllabusRepository;

    public ActivityResetServiceImpl(TeachingSessionRepository sessionRepository,
                                    AssessmentRepository assessmentRepository,
                                    RemedialPlanRepository remedialRepository,
                                    TimetableSlotRepository slotRepository,
                                    DeadlineAssignmentRepository assignmentRepository,
                                    DeadlineRepository deadlineRepository,
                                    NotificationRepository notificationRepository,
                                    SyllabusTopicRepository syllabusRepository) {
        this.sessionRepository = sessionRepository;
        this.assessmentRepository = assessmentRepository;
        this.remedialRepository = remedialRepository;
        this.slotRepository = slotRepository;
        this.assignmentRepository = assignmentRepository;
        this.deadlineRepository = deadlineRepository;
        this.notificationRepository = notificationRepository;
        this.syllabusRepository = syllabusRepository;
    }

    @Override
    public ResetResult resetActivity(boolean includeSyllabus) {
        long sessions = sessionRepository.count();
        long assessments = assessmentRepository.count();
        long plans = remedialRepository.count();
        long slots = slotRepository.count();
        long deadlines = deadlineRepository.count();
        long notifications = notificationRepository.count();
        long topics = includeSyllabus ? syllabusRepository.count() : 0;

        // Children first: session topics, submission files and submissions cascade from their parents.
        sessionRepository.deleteAllInBatch();
        assessmentRepository.deleteAllInBatch();
        remedialRepository.deleteAllInBatch();
        slotRepository.deleteAllInBatch();
        assignmentRepository.deleteAllInBatch();
        deadlineRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        if (includeSyllabus) {
            syllabusRepository.deleteAllInBatch();
        }

        return new ResetResult(sessions, assessments, plans, slots, deadlines, notifications, topics);
    }
}
