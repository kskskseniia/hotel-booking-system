package com.kseniia.hotelbooking.booking.service;

import com.kseniia.hotelbooking.booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class UserDeletionReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDeletionReconciliationService.class);

    private final UserRepository userRepository;
    private final UserDeletionService userDeletionService;

    public UserDeletionReconciliationService(
            UserRepository userRepository,
            UserDeletionService userDeletionService
    ) {
        this.userRepository = userRepository;
        this.userDeletionService = userDeletionService;
    }

    @Scheduled(
            fixedDelayString = "${user-deletion.reconciliation-delay:30000}",
            initialDelayString = "${user-deletion.reconciliation-initial-delay:60000}"
    )
    public void reconcile() {
        userRepository.findIdsByDeletionRequestedTrue().forEach(this::resume);
    }

    private void resume(Long userId) {
        try {
            userDeletionService.resume(userId, "");
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to reconcile user deletion {}", userId, exception);
        }
    }
}
