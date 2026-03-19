package com.skaeht.synapse.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.skaeht.synapse.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ARCHITECTURE NOTE: Security Principal Representation
 * This class acts as the bridge between our domain `User` entity and Spring Security's
 * internal authentication mechanisms. It is purposefully decoupled from the JPA entity
 * to allow safe serialization into Redis without triggering lazy-loading exceptions or
 * recursive JSON loops.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetailsImpl implements UserDetails {

    private Long id;
    private String actualUsername;
    private String email;
    private String password;

    /*
     * SERIALIZATION HACK:
     * Spring Security's default authority collections are unmodifiable, which causes
     * Jackson deserialization to crash when fetching this object back out of Redis.
     * By forcing it into a standard ArrayList, we ensure cache compatibility.
     */
    @JsonDeserialize(as = ArrayList.class, contentAs = SimpleGrantedAuthority.class)
    private Collection<? extends GrantedAuthority> authorities;

    public static UserDetailsImpl build(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Future RBAC Expansion: Load actual roles from the DB here instead of hardcoding
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        return new UserDetailsImpl(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                authorities);
    }

    /**
     * Spring Security uses "Username" as a generic term for the primary identity key.
     * Since Synapse mandates email-based logins, we route this to return the email.
     */
    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}