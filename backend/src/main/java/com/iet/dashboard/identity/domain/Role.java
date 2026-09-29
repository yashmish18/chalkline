package com.iet.dashboard.identity.domain;

public enum Role {
    COORDINATOR,
    FACULTY,
    DEAN;

    public String toAuthority() {
        return "ROLE_" + this.name();
    }
}
