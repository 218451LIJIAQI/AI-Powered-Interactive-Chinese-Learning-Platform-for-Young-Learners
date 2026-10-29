package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.enums.PackageItemType;
import java.util.Collection;

public interface PackageItemRepository extends JpaRepository<PackageItem, Long> {

    List<PackageItem> findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(Long coursePackageId);


    @EntityGraph(attributePaths = { "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user" })
    List<PackageItem> findByItemTypeAndItemId(PackageItemType itemType, Long itemId);

    List<PackageItem> findByCoursePackageIdAndItemTypeOrderBySortOrderAscIdAsc(Long coursePackageId, PackageItemType itemType);

    List<PackageItem> findByCoursePackageIdInAndItemTypeOrderBySortOrderAscIdAsc(
            Collection<Long> coursePackageIds,
            PackageItemType itemType);
}
