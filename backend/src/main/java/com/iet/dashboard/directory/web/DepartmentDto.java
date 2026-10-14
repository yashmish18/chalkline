package com.iet.dashboard.directory.web;

/** Department reference data for filters and pickers. */
public record DepartmentDto(Long id, String code, String name) {

    public static DepartmentDto fromEntity(com.iet.dashboard.directory.domain.Department department) {
        return new DepartmentDto(department.getId(), department.getCode(), department.getName());
    }
}
