package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class StudentLearningAccessService {

    private static final String STUDENT_PROFILE_NOT_FOUND = "Student profile could not be found.";

    private final StudentProfileRepository studentProfileRepository;

    public StudentLearningAccessService(
            StudentProfileRepository studentProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
    }

    @Transactional(readOnly = true)
    public StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, STUDENT_PROFILE_NOT_FOUND));
    }
}
