package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.PackageItem;

public interface PackageItemRepository extends JpaRepository<PackageItem, Long> {

    /** Derived delete removes managed PackageItem rows from the L1 cache before the parent class is deleted. */
    void deleteByCoursePackage(CoursePackage coursePackage);

    record ClassUnit(Long classId, LearningModule unit) {
    }

    @Query("""
            select new com.example.aichinese.repository.PackageItemRepository$ClassUnit(item.coursePackage.id, unit)
            from PackageItem item join item.module unit
            where item.coursePackage.id in :classIds
            order by item.sortOrder, item.id
            """)
    List<ClassUnit> findUnitsInOrder(@Param("classIds") Collection<Long> classIds);

    @Query("select coalesce(max(item.sortOrder), 0) + 1 from PackageItem item where item.coursePackage.id = :classId")
    int nextSortOrder(@Param("classId") Long classId);

    @Query("""
            select distinct c from PackageItem item join item.coursePackage c
            where item.module.id = :unitId order by c.id
            """)
    List<CoursePackage> findClassesOfUnit(@Param("unitId") Long unitId);

    @Query("""
            select distinct item.coursePackage.id from PackageItem item
            where item.module.id = :unitId order by item.coursePackage.id
            """)
    List<Long> findClassIdsOfUnit(@Param("unitId") Long unitId);

    /** Legacy units may occur in more than one class; only exclusive units can be deleted with a class. */
    @Query("""
            select distinct item.module.id from PackageItem item
            where item.coursePackage.id = :classId
              and not exists (
                  select shared.id from PackageItem shared
                  where shared.module.id = item.module.id and shared.coursePackage.id <> :classId
              )
            """)
    List<Long> findExclusiveItemIdsByClassId(@Param("classId") Long classId);
}
