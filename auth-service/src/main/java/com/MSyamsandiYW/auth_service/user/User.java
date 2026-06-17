package com.MSyamsandiYW.auth_service.user;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("users")
public class User implements UserDetails {

    @Id
    @Column("id")
    private UUID id;

    @Column("email")
    private String email;

    @Column("name")
    private String name;

    @Column("password")
    private String password;

    @Column("phonenumber")
    private String phoneNumber;

    @Column("roles")
    private String roles;

    @CreatedDate
    @Column("created_date")
    private ZonedDateTime createdDate;

    @LastModifiedDate
    @Column("last_modified_date")
    private ZonedDateTime lastModifiedDate;

    @Column("is_locked")
    private boolean locked;

    @Column("is_credentials_expired")
    private boolean credentialsExpired;

    @Column("is_enabled")
    private boolean enabled;

    @Column("is_email_verified")
    private boolean emailVerified;

    @Column("is_phone_verified")
    private boolean phoneVerified;

    @Column("is_deleted")
    private boolean deleted;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split("\\|"))
                .map(String::trim)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !credentialsExpired;
    }
}
