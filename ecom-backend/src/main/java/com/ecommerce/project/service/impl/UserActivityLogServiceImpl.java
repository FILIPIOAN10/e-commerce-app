package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.UserActivityLog;
import com.ecommerce.project.payload.UserActivityLogDTO;
import com.ecommerce.project.payload.UserActivityLogResponse;
import com.ecommerce.project.repository.UserActivityLogRepository;
import com.ecommerce.project.service.UserActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserActivityLogServiceImpl implements UserActivityLogService {

    private final UserActivityLogRepository userActivityLogRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    @Transactional
    public UserActivityLog log(String username, String action, String details) {
        UserActivityLog log = UserActivityLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .build();
        return userActivityLogRepository.save(log);
    }

    @Override
    public UserActivityLogResponse getLogs(Pageable pageable) {
        Page<UserActivityLog> page = userActivityLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        UserActivityLogResponse response = new UserActivityLogResponse();
        response.setContent(page.getContent().stream().map(this::mapToDTO).toList());
        response.setTotalElements(page.getTotalElements());
        return response;
    }

    private UserActivityLogDTO mapToDTO(UserActivityLog log) {
        UserActivityLogDTO dto = new UserActivityLogDTO();
        dto.setId(log.getId());
        dto.setUsername(log.getUsername());
        dto.setAction(log.getAction());
        dto.setDetails(log.getDetails());
        dto.setCreatedAt(log.getCreatedAt().format(FORMATTER));
        return dto;
    }
}
