package com.foodloop.user;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.foodloop.auth.AuthUser;
import com.foodloop.common.error.ApiException;

/**
 * Turns the user id in the access token into a {@link CurrentUser} for controller methods.
 * The JWT filter never reads the database, so this is where a removed account is stopped: an access
 * token that is still valid for up to 15 minutes gets 401 as soon as the account is not ACTIVE.
 */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private final UserRepository users;

    public CurrentUserResolver(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw ApiException.unauthenticated();
        }
        User user = users.findById(authUser.id()).filter(User::isActive).orElseThrow(ApiException::unauthenticated);
        return new CurrentUser(user.getId(), user.getName(), user.getRole(), user.isDemo());
    }
}
