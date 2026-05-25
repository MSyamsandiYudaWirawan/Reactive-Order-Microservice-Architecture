package com.MSyamsandiYW.auth_service.user;

import com.MSyamsandiYW.auth_service.user.request.ProfileUpdateRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class UserMapper {
    public Mono<User> mergerUserInfo(final User user, final ProfileUpdateRequest request) {
        if(StringUtils.isNotBlank(request.getName())
                && !user.getName().equals(request.getName())){
            user.setName(request.getName());
        }
        return Mono.just(user);
    }
}
