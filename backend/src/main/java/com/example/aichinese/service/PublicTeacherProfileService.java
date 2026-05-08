package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.publicview.PublicTeacherProfileResponse;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.repository.TeacherProfileRepository;

@Service
public class PublicTeacherProfileService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherManagementMapper teacherManagementMapper;

    public PublicTeacherProfileService(
            TeacherProfileRepository teacherProfileRepository,
            TeacherManagementMapper teacherManagementMapper) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherManagementMapper = teacherManagementMapper;
    }

    @Transactional(readOnly = true)
    public PublicTeacherProfileResponse getApprovedTeacherProfile(Long teacherId) {
        TeacherProfile teacherProfile = teacherProfileRepository
                .findByIdAndTeacherStatusAndPublicVisibleTrue(teacherId, TeacherStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Approved teacher profile was not found."));

        return teacherManagementMapper.toPublicTeacherProfileResponse(teacherProfile);
    }
}
