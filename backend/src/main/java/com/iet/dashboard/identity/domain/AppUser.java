package com.iet.dashboard.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entra_oid", unique = true, length = 64)
    private String entraOid;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** BCrypt hash of the coordinator password; null until the person completes coordinator setup. */
    @Column(name = "coordinator_password_hash", length = 100)
    private String coordinatorPasswordHash;

    @Column(name = "coordinator_failed_attempts", nullable = false)
    private int coordinatorFailedAttempts;

    @Column(name = "coordinator_locked_until")
    private Instant coordinatorLockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AppUser() {
    }

    public AppUser(String email, String name, Role role) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntraOid() {
        return entraOid;
    }

    public void setEntraOid(String entraOid) {
        this.entraOid = entraOid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCoordinatorPasswordHash() {
        return coordinatorPasswordHash;
    }

    public void setCoordinatorPasswordHash(String coordinatorPasswordHash) {
        this.coordinatorPasswordHash = coordinatorPasswordHash;
    }

    public int getCoordinatorFailedAttempts() {
        return coordinatorFailedAttempts;
    }

    public void setCoordinatorFailedAttempts(int coordinatorFailedAttempts) {
        this.coordinatorFailedAttempts = coordinatorFailedAttempts;
    }

    public Instant getCoordinatorLockedUntil() {
        return coordinatorLockedUntil;
    }

    public void setCoordinatorLockedUntil(Instant coordinatorLockedUntil) {
        this.coordinatorLockedUntil = coordinatorLockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
