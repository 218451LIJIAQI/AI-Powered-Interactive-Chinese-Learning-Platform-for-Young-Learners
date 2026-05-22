package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PackageItem;

public interface PackageItemRepository extends JpaRepository<PackageItem, Long> {

    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    List<PackageItem> findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(Long coursePackageId);

    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    Page<PackageItem> findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(Long coursePackageId, Pageable pageable);

    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    Optional<PackageItem> findByIdAndCoursePackageTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    List<PackageItem> findByItemTypeAndItemId(com.example.aichinese.entity.enums.PackageItemType itemType, Long itemId);
}
