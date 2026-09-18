package com.exelynt.booking.security.user;

import com.exelynt.booking.user.common.Role;
import com.exelynt.booking.user.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated caller. Carries the user id and role so services never have to
 * re-read the user just to know who is asking.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Role role;
    private final boolean enabled;

    public AppUserPrincipal(Long userId, String username, String password, Role role, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(
                user.getId(), user.getUsername(), user.getPassword(), user.getRole(), user.isEnabled());
    }

    public Long getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
