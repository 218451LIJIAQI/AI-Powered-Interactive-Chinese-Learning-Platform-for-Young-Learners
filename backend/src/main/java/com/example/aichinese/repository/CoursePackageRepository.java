package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.dto.admin.AdminDashboardResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.repository.projection.StatusCountProjection;

import jakarta.persistence.LockModeType;

public interface CoursePackageRepository extends JpaRepository<CoursePackage, Long> {

    String CATALOGUE_LIVE_LESSONS = """
             join PackageItem item on item.coursePackage = p
             join Lesson lesson on lesson.module = item.module
             where p.contentStatus = com.example.aichinese.entity.enums.ContentStatus.PUBLISHED
               and lesson.contentStatus = com.example.aichinese.entity.enums.ContentStatus.PUBLISHED
               and lesson.revisionOf is null
            """;
    String CATALOGUE_BASE = " from CoursePackage p " + CATALOGUE_LIVE_LESSONS;
    String CATALOGUE_ROWS = " from CoursePackage p left join p.category topic " + CATALOGUE_LIVE_LESSONS;
    String EXCLUDE_ENROLLED = """
               and (:studentId is null or not exists (
                   select 1 from ClassEnrolment enrolment
                   where enrolment.coursePackage = p and enrolment.student.id = :studentId
               ))
            """;
    String CLASS_CARDS = """
            select new com.example.aichinese.dto.classes.ClassViews$ClassCard(
                p.id, p.title, p.description, topic.name, p.bestAfterUnit, cast(count(lesson.id) as integer))
            """ + CATALOGUE_ROWS;
    String GROUP_BY_CLASS = " group by p.id, topic.name";
    String COUNT_CATALOGUE = "select count(distinct p.id)" + CATALOGUE_BASE + EXCLUDE_ENROLLED;

    @Override
    @EntityGraph(attributePaths = "category")
    @NonNull
    Optional<CoursePackage> findById(@NonNull Long id);

    /**
     * The class, write-locked until the transaction ends: every authoring write on a class, its units and its lessons
     * takes this lock first, so two admins' writes on one class run one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CoursePackage p where p.id = :id")
    Optional<CoursePackage> findLockedById(@Param("id") Long id);

    /** Joining and leaving may overlap for different learners, but must see a pending archive before deciding. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from CoursePackage p where p.id = :id")
    Optional<CoursePackage> findForEnrolment(@Param("id") Long id);

    @Query("select p.contentStatus as status, count(p) as count from CoursePackage p group by p.contentStatus")
    List<StatusCountProjection> groupByContentStatus();

    /** Published classes with a published lesson. When a learner is supplied, omit classes they already joined. */
    @Query(value = CLASS_CARDS + EXCLUDE_ENROLLED + GROUP_BY_CLASS + " order by p.createdAt desc, p.id desc",
            countQuery = COUNT_CATALOGUE)
    Page<ClassViews.ClassCard> findCatalogue(@Param("studentId") Long studentId, Pageable pageable);

    @Query(CLASS_CARDS + " and p.id in :ids" + GROUP_BY_CLASS)
    List<ClassViews.ClassCard> findCatalogueByIds(@Param("ids") Collection<Long> ids);

    @Query(COUNT_CATALOGUE)
    long countCatalogue(@Param("studentId") Long studentId);

    @Query("select exists (select 1" + CATALOGUE_BASE + " and p.id in :classIds)")
    boolean existsInCatalogue(@Param("classIds") Collection<Long> classIds);

    /** Page the class ids first so the dashboard only aggregates lessons of the two drafts it shows. */
    @Query("select p.id from CoursePackage p where p.contentStatus = com.example.aichinese.entity.enums.ContentStatus.DRAFT"
            + " order by p.createdAt desc, p.id desc")
    Page<Long> findNewestDraftIds(Pageable pageable);

    @Query("""
            select new com.example.aichinese.dto.admin.AdminDashboardResponse$ClassDraft(
                p.id, p.title,
                cast(count(lesson.id) filter (where lesson.contentStatus <> com.example.aichinese.entity.enums.ContentStatus.ARCHIVED) as integer),
                greatest(p.updatedAt, coalesce(max(lesson.updatedAt), p.updatedAt)))
            from CoursePackage p
            left join PackageItem item on item.coursePackage = p
            left join Lesson lesson on lesson.module = item.module and lesson.revisionOf is null
            where p.id in :classIds
            group by p.id
            order by p.createdAt desc, p.id desc
            """)
    List<AdminDashboardResponse.ClassDraft> summarizeDrafts(@Param("classIds") Collection<Long> classIds);
}
