package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.AdminStudentListItemResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;

@Service
public class AdminUserManagementService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherManagementMapper teacherManagementMapper;

    public AdminUserManagementService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            TeacherManagementMapper teacherManagementMapper) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherManagementMapper = teacherManagementMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> listUsers(
            String role,
            String keyword,
            String status,
            int page,
            int size) {
        int sanitizedPage = Math.max(page, 0);
        int sanitizedSize = Math.max(size, 1);
        RoleName roleFilter = parseRole(role);

        List<User> allUsers = userRepository.findAllByOrderByCreatedAtDesc();
        List<User> filteredUsers = allUsers.stream()
                .filter(user -> matchesKeyword(user, keyword))
                .filter(user -> matchesRole(user, roleFilter))
                .toList();

        Map<Long, TeacherProfile> teacherProfiles = teacherProfileRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity(), (left, right) -> left));

        List<AdminUserListItemResponse> filteredItems = filteredUsers.stream()
                .map(user -> teacherManagementMapper.toAdminUserListItem(user, teacherProfiles.get(user.getId())))
                .filter(item -> matchesTeacherStatus(item.teacherStatus(), status))
                .toList();

        int fromIndex = Math.min(sanitizedPage * sanitizedSize, filteredItems.size());
        int toIndex = Math.min(fromIndex + sanitizedSize, filteredItems.size());
        List<AdminUserListItemResponse> pageItems = filteredItems.subList(fromIndex, toIndex);
        int totalPages = filteredItems.isEmpty() ? 0 : (int) Math.ceil((double) filteredItems.size() / sanitizedSize);

        return new PageResponse<>(pageItems, sanitizedPage, sanitizedSize, filteredItems.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminStudentListItemResponse> listStudents(int page, int size) {
        int sanitizedPage = Math.max(page, 0);
        int sanitizedSize = Math.max(size, 1);

        List<StudentProfile> allStudents = studentProfileRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        int fromIndex = Math.min(sanitizedPage * sanitizedSize, allStudents.size());
        int toIndex = Math.min(fromIndex + sanitizedSize, allStudents.size());

        List<AdminStudentListItemResponse> items = allStudents.subList(fromIndex, toIndex).stream()
                .map(teacherManagementMapper::toAdminStudentListItem)
                .toList();
        int totalPages = allStudents.isEmpty() ? 0 : (int) Math.ceil((double) allStudents.size() / sanitizedSize);
        return new PageResponse<>(items, sanitizedPage, sanitizedSize, allStudents.size(), totalPages);
    }

    private boolean matchesKeyword(User user, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return user.getDisplayName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                || user.getEmail().toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private boolean matchesRole(User user, RoleName role) {
        if (role == null) {
            return true;
        }

        return user.getRoles().stream().anyMatch(value -> value.getName() == role);
    }

    private boolean matchesTeacherStatus(com.example.aichinese.entity.enums.TeacherStatus teacherStatus, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }

        if (teacherStatus == null) {
            return false;
        }

        return teacherStatus.name().equalsIgnoreCase(status.trim());
    }

    private RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }

        try {
            return RoleName.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid user role filter.");
        }
    }
}
