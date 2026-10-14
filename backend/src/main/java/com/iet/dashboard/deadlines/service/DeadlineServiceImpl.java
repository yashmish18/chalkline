package com.iet.dashboard.deadlines.service;

import com.iet.dashboard.academics.service.AcademicsService;
import com.iet.dashboard.academics.web.TeachingAssignmentDto;
import com.iet.dashboard.deadlines.domain.Deadline;
import com.iet.dashboard.deadlines.domain.DeadlineAssignment;
import com.iet.dashboard.deadlines.domain.DeadlineType;
import com.iet.dashboard.deadlines.repo.DeadlineAssignmentRepository;
import com.iet.dashboard.deadlines.repo.DeadlineRepository;
import com.iet.dashboard.deadlines.web.CreateDeadlineRequest;
import com.iet.dashboard.deadlines.web.DeadlineAssignmentDto;
import com.iet.dashboard.deadlines.web.DeadlineDto;
import com.iet.dashboard.directory.service.FacultyService;
import com.iet.dashboard.directory.web.FacultyDto;
import com.iet.dashboard.identity.service.UserService;
import com.iet.dashboard.identity.web.UserDto;
import com.iet.dashboard.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DeadlineServiceImpl implements DeadlineService {

    private final DeadlineRepository deadlineRepository;
    private final DeadlineAssignmentRepository assignmentRepository;
    private final FacultyService facultyService;
    private final AcademicsService academicsService;
    private final UserService userService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public DeadlineServiceImpl(
            DeadlineRepository deadlineRepository,
            DeadlineAssignmentRepository assignmentRepository,
            FacultyService facultyService,
            AcademicsService academicsService,
            UserService userService,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.deadlineRepository = deadlineRepository;
        this.assignmentRepository = assignmentRepository;
        this.facultyService = facultyService;
        this.academicsService = academicsService;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadlineDto> getAllDeadlines() {
        List<Deadline> deadlines = deadlineRepository.findAllOrderByDueAt();
        return deadlines.stream().map(d -> {
            long total = assignmentRepository.findByDeadlineId(d.getId()).size();
            return DeadlineDto.fromEntity(d, total);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DeadlineDto getDeadlineById(Long id) {
        Deadline deadline = deadlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deadline not found with id: " + id));
        long total = assignmentRepository.findByDeadlineId(deadline.getId()).size();
        return DeadlineDto.fromEntity(deadline, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadlineAssignmentDto> getAssignmentsForDeadline(Long deadlineId) {
        List<DeadlineAssignment> assignments = assignmentRepository.findByDeadlineId(deadlineId);
        Map<Long, String> facultyNames = facultyService.getAllFaculty(null, null).stream()
                .collect(Collectors.toMap(FacultyDto::id, FacultyDto::name, (a, b) -> a));

        return assignments.stream()
                .map(a -> DeadlineAssignmentDto.fromEntity(a, facultyNames.getOrDefault(a.getFacultyId(), "Faculty #" + a.getFacultyId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadlineAssignmentDto> getMyDeadlines() {
        UserDto currentUser = userService.getCurrentUser();
        FacultyDto faculty = facultyService.getFacultyByUserId(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("No faculty record found for current user: " + currentUser.email()));

        List<DeadlineAssignment> assignments = assignmentRepository.findByFacultyIdWithDeadline(faculty.id());
        return assignments.stream()
                .map(a -> DeadlineAssignmentDto.fromEntity(a, faculty.name()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DeadlineAssignmentDto getAssignmentById(Long id) {
        DeadlineAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deadline assignment not found with id: " + id));
        String facultyName;
        try {
            facultyName = facultyService.getFacultyById(assignment.getFacultyId()).name();
        } catch (Exception e) {
            facultyName = "Faculty #" + assignment.getFacultyId();
        }
        return DeadlineAssignmentDto.fromEntity(assignment, facultyName);
    }

    @Override
    public DeadlineDto createDeadline(CreateDeadlineRequest request) {
        UserDto currentUser = userService.getCurrentUser();
        DeadlineType type = DeadlineType.valueOf(request.type().toUpperCase());

        Deadline deadline = new Deadline(
                request.title(),
                request.description(),
                type,
                request.dueAt(),
                currentUser.id()
        );
        Deadline savedDeadline = deadlineRepository.save(deadline);

        // Resolve targets based on targeting rule
        List<DeadlineAssignment> assignmentsToCreate = new ArrayList<>();
        String rule = request.targetRule().toUpperCase();

        if ("ALL_FACULTY".equals(rule)) {
            List<FacultyDto> allFaculty = facultyService.getAllFaculty(null, null);
            for (FacultyDto f : allFaculty) {
                assignmentsToCreate.add(new DeadlineAssignment(savedDeadline, f.id(), null));
            }
        } else if ("SELECTED_FACULTY".equals(rule) && request.selectedFacultyIds() != null) {
            for (Long facultyId : request.selectedFacultyIds()) {
                assignmentsToCreate.add(new DeadlineAssignment(savedDeadline, facultyId, null));
            }
        } else if ("BY_SEMESTER".equals(rule) && request.semester() != null) {
            List<TeachingAssignmentDto> teachingAssignments = academicsService.getAllTeachingAssignments(null);
            Set<String> seen = new HashSet<>();
            for (TeachingAssignmentDto ta : teachingAssignments) {
                if (ta.offering().semester() == request.semester()) {
                    String key = ta.facultyId() + "_" + ta.offering().id();
                    if (seen.add(key)) {
                        assignmentsToCreate.add(new DeadlineAssignment(savedDeadline, ta.facultyId(), ta.offering().id()));
                    }
                }
            }
        } else if ("BY_OFFERING".equals(rule) && request.selectedOfferingIds() != null) {
            List<TeachingAssignmentDto> teachingAssignments = academicsService.getAllTeachingAssignments(null);
            Set<Long> targetOfferingIds = new HashSet<>(request.selectedOfferingIds());
            Set<String> seen = new HashSet<>();
            for (TeachingAssignmentDto ta : teachingAssignments) {
                if (targetOfferingIds.contains(ta.offering().id())) {
                    String key = ta.facultyId() + "_" + ta.offering().id();
                    if (seen.add(key)) {
                        assignmentsToCreate.add(new DeadlineAssignment(savedDeadline, ta.facultyId(), ta.offering().id()));
                    }
                }
            }
        } else {
            // Default fallback: assign to all faculty
            List<FacultyDto> allFaculty = facultyService.getAllFaculty(null, null);
            for (FacultyDto f : allFaculty) {
                assignmentsToCreate.add(new DeadlineAssignment(savedDeadline, f.id(), null));
            }
        }

        assignmentRepository.saveAll(assignmentsToCreate);

        if (eventPublisher != null) {
            List<Long> targetFacultyIds = assignmentsToCreate.stream()
                    .map(DeadlineAssignment::getFacultyId)
                    .distinct()
                    .collect(Collectors.toList());

            eventPublisher.publishEvent(new com.iet.dashboard.shared.events.DeadlineAssignedEvent(
                    savedDeadline.getId(),
                    savedDeadline.getTitle(),
                    savedDeadline.getDueAt(),
                    targetFacultyIds
            ));
        }

        return DeadlineDto.fromEntity(savedDeadline, assignmentsToCreate.size());
    }

    @Override
    public void deleteDeadline(Long id) {
        if (!deadlineRepository.existsById(id)) {
            throw new ResourceNotFoundException("Deadline not found: " + id);
        }
        deadlineRepository.deleteById(id);
    }
}
