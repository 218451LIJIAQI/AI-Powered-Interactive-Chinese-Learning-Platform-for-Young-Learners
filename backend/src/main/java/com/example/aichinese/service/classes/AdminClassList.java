package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.AdminClassViews.ContentRow;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.EnumLookups;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/** The admin content list combines classes and their lessons in one ordered result. */
@Service
@RequiredArgsConstructor
public class AdminClassList {

    private static final Set<String> CONTENT_TYPES = Set.of("CLASS", "LESSON");
    private static final String CLASS_SELECT = """
            select 'CLASS' as type, c.id, c.title, c.id as class_id, null as class_title,
                   c.content_status as status, c.updated_at
            from course_packages c
            """;
    private static final String LESSON_SELECT = """
            select 'LESSON' as type, l.id, l.title, c.id as class_id, c.title as class_title,
                   l.content_status as status,
                   greatest(l.updated_at, coalesce(rev.max_updated_at, l.updated_at)) as updated_at
            from course_packages c
            join package_items item on item.course_package_id = c.id
            join lessons l on l.module_id = item.item_id
            left join (
                select revision_of_lesson_id, max(updated_at) as max_updated_at
                from lessons
                where revision_of_lesson_id is not null
                group by revision_of_lesson_id
            ) rev on rev.revision_of_lesson_id = l.id
            where l.revision_of_lesson_id is null
            """;
    private static final String FILTER_CLAUSE = """
             where (:status is null or content.status = :status)
               and (:keyword is null or locate(:keyword, lower(content.title)) > 0
                    or locate(:keyword, lower(content.class_title)) > 0)
            """;

    private final JdbcClient jdbc;

    /** A lesson's unpublished edits affect its place in the list while its published title stays visible. */
    @Transactional(readOnly = true)
    public PageResponse<ContentRow> list(String keyword, String type, String status, int page, int size) {
        String typeFilter = filterValue(type);
        if (typeFilter != null && !CONTENT_TYPES.contains(typeFilter)) {
            throw new ResponseStatusException(BAD_REQUEST, "Type must be CLASS or LESSON.");
        }
        String statusFilter = filterValue(status);
        if (statusFilter != null && EnumLookups.tryFrom(ContentStatus.class, statusFilter).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Status filter is not supported.");
        }
        String source = switch (typeFilter == null ? "" : typeFilter) {
            case "CLASS" -> CLASS_SELECT;
            case "LESSON" -> LESSON_SELECT;
            default -> CLASS_SELECT + " union all " + LESSON_SELECT;
        };
        String filteredRows = " from (" + source + ") content " + FILTER_CLAUSE;
        PageRequest request = PaginationUtils.pageRequest(page, size);
        var parameters = new MapSqlParameterSource()
                .addValue("keyword", TextUtils.lowerCaseOrNull(keyword))
                .addValue("status", statusFilter)
                .addValue("limit", request.getPageSize())
                .addValue("offset", request.getOffset());
        long total = jdbc.sql("select count(*)" + filteredRows).paramSource(parameters).query(Long.class).single();
        List<ContentRow> rows = List.of();
        if (request.getOffset() < total) {
            rows = jdbc.sql("select content.*" + filteredRows + """
                    order by updated_at desc, type, id desc, class_id
                    limit :limit offset :offset
                    """)
                    .paramSource(parameters).query(ContentRow.class).list();
        }
        return new PageResponse<>(rows, request.getPageNumber(), request.getPageSize(), total);
    }

    private static String filterValue(String value) {
        String normalized = TextUtils.trimToNull(value);
        return normalized == null || "ALL".equalsIgnoreCase(normalized) ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
