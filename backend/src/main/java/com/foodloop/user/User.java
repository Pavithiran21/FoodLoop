package com.foodloop.user;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    /** What the other side of a claim sees after a receiver deleted their account. */
    public static final String FORMER_USER = "Former user";
    /** What receivers and History show after a donor deleted their account. */
    public static final String FORMER_DONOR = "Former donor";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", length = 72)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    @Column(name = "suspended_reason", length = 200)
    private String suspendedReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "home_label", length = 250)
    private String homeLabel;

    @Column(name = "home_lat")
    private Double homeLat;

    @Column(name = "home_lng")
    private Double homeLng;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        // MySQL keeps microseconds; truncating here makes the response match what is stored.
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasHome() {
        return homeLabel != null;
    }

    public void setHome(String label, Double lat, Double lng) {
        this.homeLabel = label;
        this.homeLat = lat;
        this.homeLng = lng;
    }

    public void suspend(String reason, Instant now) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedReason = reason;
        this.suspendedAt = now;
    }

    public void reinstate() {
        this.status = UserStatus.ACTIVE;
        this.suspendedReason = null;
        this.suspendedAt = null;
    }

    /** Anonymises the account for "Delete my account": the row stays so the other side's records still read. */
    public void anonymise(String formerName) {
        this.name = formerName;
        this.email = "deleted-" + id + "@deleted.foodloop.invalid";
        this.passwordHash = null;
        this.status = UserStatus.DELETED;
        this.suspendedReason = null;
        setHome(null, null, null);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getSuspendedReason() {
        return suspendedReason;
    }

    public String getHomeLabel() {
        return homeLabel;
    }

    public Double getHomeLat() {
        return homeLat;
    }

    public Double getHomeLng() {
        return homeLng;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
