package ru.itmo.infosec.dto;

import org.springframework.web.util.HtmlUtils;
import ru.itmo.infosec.model.User;

import java.time.Instant;

public record UserResponse(Long id, String username, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), HtmlUtils.htmlEscape(user.getUsername()), user.getCreatedAt());
    }
}
