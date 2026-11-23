package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learn.AdminPracticeSetRow;
import com.example.aichinese.dto.learn.PracticeCompleteResponse;
import com.example.aichinese.dto.learn.PracticeSetsResponse;
import com.example.aichinese.entity.DragDropActivity;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.TypingExercise;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** The six activity tables share one rule for which sets belong to the platform Practice library. */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeSetDirectory {

    private static final String STUDENT_ORDER = " order by a.hsk_level is null, a.hsk_level, a.id";

    private final EntityManager entityManager;
    private final JdbcClient jdbc;

    private record Source(Class<? extends PracticeActivityContent> entity, String table, String itemCount) {
        String from() {
            return " from " + table + " a";
        }

        String scope() {
            String ownership = """
                     where (a.created_by_user_id is null or exists (
                         select 1 from user_roles ur join roles r on r.id = ur.role_id
                         where ur.user_id = a.created_by_user_id and r.name = 'ADMIN'))
                    """;
            return LessonActivity.class.isAssignableFrom(entity) ? ownership + " and a.lesson_id is null" : ownership;
        }
    }

    private static Source source(PracticeType type) {
        return switch (type) {
            case FLASHCARDS -> new Source(Flashcard.class, "flashcards", childCount("flashcard_items", "flashcard_id"));
            case LISTENING -> new Source(ListeningExercise.class, "listening_exercises",
                    jsonCount("question_payload", "questions"));
            case TRACING -> new Source(TracingExercise.class, "tracing_exercises", "1");
            case DRAG_DROP -> new Source(DragDropActivity.class, "drag_drop_activities",
                    childCount("drag_drop_items", "drag_drop_activity_id"));
            case MATCHING -> new Source(MatchingGame.class, "matching_games",
                    childCount("matching_game_items", "matching_game_id"));
            case TYPING -> new Source(TypingExercise.class, "typing_exercises", jsonCount("word_payload", "words"));
        };
    }

    private static String childCount(String table, String parent) {
        return "(select count(*) from " + table + " i where i." + parent + " = a.id)";
    }

    /** Match the player's iteration over a stored JSON container, including incomplete legacy drafts. */
    private static String jsonCount(String column, String key) {
        return """
                case when json_valid(a.%1$s) then
                    case when json_type(json_extract(a.%1$s, '$.%2$s')) in ('ARRAY','OBJECT')
                         then json_length(a.%1$s, '$.%2$s') else 0 end
                else 0 end
                """.formatted(column, key);
    }

    Optional<PracticeActivityContent> find(PracticeType type, Long id) {
        Source source = source(type);
        return entityManager.unwrap(Session.class)
                .createNativeQuery("select a.*" + source.from() + source.scope() + " and a.id = :id", source.entity)
                .addSynchronizedEntityClass(source.entity)
                .setParameter("id", id).uniqueResultOptional().map(PracticeActivityContent.class::cast);
    }

    PracticeActivityContent require(PracticeType type, Long id) {
        return find(type, id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Practice set not found."));
    }

    /** Platform sets for the demo fixtures, oldest first. */
    public List<PracticeActivityContent> sets(PracticeType type, ContentStatus status) {
        Source source = source(type);
        var query = entityManager.unwrap(Session.class).createNativeQuery(
                "select a.*" + source.from() + source.scope()
                        + (status == null ? "" : " and a.content_status = :status") + " order by a.id", source.entity)
                .addSynchronizedEntityClass(source.entity);
        if (status != null) {
            query.setParameter("status", status.name());
        }
        return List.copyOf(query.list());
    }

    List<PracticeSetsResponse.Summary> studentSets(PracticeType type, Long studentId) {
        Source source = source(type);
        String query = "select a.id, a.title, a.hsk_level, " + source.itemCount + " as item_count, a.xp_reward,"
                + " (p.first_success is not null) as done, (p.first_success is null and p.activity_id is not null) as started"
                + publishedWithProgress(type) + STUDENT_ORDER;
        return jdbc.sql(query).param("student", studentId)
                .query(PracticeSetsResponse.Summary.class).list();
    }

    /** Prefer an unfinished set after the current one, then wrap. With every set done, offer only its successor. */
    Optional<PracticeCompleteResponse.NextSet> nextSet(
            PracticeType type, Long studentId, PracticeActivityContent current) {
        String candidates = """
                select a.id, a.title, a.hsk_level, (p.first_success is not null) as done,
                       (a.hsk_level is null, coalesce(a.hsk_level, 0), a.id) > (:noLevel, :level, :current) as later
                """ + publishedWithProgress(type) + " and a.id <> :current";
        return jdbc.sql("select id, title from (" + candidates + ") candidates" + """
                 where done = 0 or later
                 order by done, later desc, hsk_level is null, hsk_level, id
                 limit 1
                """)
                .param("student", studentId).param("current", current.getId())
                .param("noLevel", current.getHskLevel() == null)
                .param("level", current.getHskLevel() == null ? 0 : current.getHskLevel())
                .query(PracticeCompleteResponse.NextSet.class).optional();
    }

    /** The learning plan suggests the first unfinished set in the order of the student's Practice list. */
    public Optional<PracticeCompleteResponse.NextSet> firstUnfinished(PracticeType type, Long studentId) {
        return jdbc.sql("select a.id, a.title" + publishedWithProgress(type)
                        + " and p.first_success is null" + STUDENT_ORDER + " limit 1")
                .param("student", studentId)
                .query(PracticeCompleteResponse.NextSet.class).optional();
    }

    /** Published library sets (a) and the student's first fully correct run of each set (p.first_success). */
    static String publishedWithProgress(PracticeType type) {
        Source source = source(type);
        return source.from() + """
                 left join (
                     select activity_id, min(case when item_count > 0 and correct_count = item_count
                                                  then created_at end) as first_success
                     from practice_completions where student_id = :student and practice_type = '%s'
                     group by activity_id
                 ) p on p.activity_id = a.id
                """.formatted(type.name()) + source.scope() + " and a.content_status = 'PUBLISHED'";
    }

    /** How many platform Practice library sets currently exist for {@code type}. */
    public long countPlatformSets(PracticeType type) {
        Source source = source(type);
        return jdbc.sql("select count(*)" + source.from() + source.scope()).query(Long.class).single();
    }

    public PageResponse<AdminPracticeSetRow> adminSets(
            String keyword, PracticeType practiceType, ContentStatus status, int page, int size) {
        String normalizedKeyword = TextUtils.lowerCaseOrNull(keyword);
        boolean filterStatus = status != null;
        boolean filterKeyword = normalizedKeyword != null;
        var request = PaginationUtils.pageRequest(page, size);
        var parameters = new MapSqlParameterSource()
                .addValue("limit", request.getPageSize())
                .addValue("offset", request.getOffset());
        if (filterStatus) {
            parameters.addValue("status", status.name());
        }
        if (filterKeyword) {
            parameters.addValue("keyword", normalizedKeyword);
        }
        String countUnion = Arrays.stream(PracticeType.values())
                .filter(type -> practiceType == null || type == practiceType)
                .map(type -> adminCountRows(type, filterStatus, filterKeyword))
                .collect(Collectors.joining(" union all "));
        long total = jdbc.sql("select coalesce(sum(cnt), 0) from (" + countUnion + ") library")
                .paramSource(parameters).query(Long.class).single();
        if (request.getOffset() >= total) {
            return new PageResponse<>(List.of(), request.getPageNumber(), request.getPageSize(), total);
        }
        String rowsUnion = Arrays.stream(PracticeType.values())
                .filter(type -> practiceType == null || type == practiceType)
                .map(type -> adminRows(type, filterStatus, filterKeyword))
                .collect(Collectors.joining(" union all "));
        List<AdminPracticeSetRow> rows = jdbc.sql("select library.* from (" + rowsUnion + ") library"
                        + " order by updated_at desc, id desc, type_order limit :limit offset :offset")
                .paramSource(parameters).query(AdminPracticeSetRow.class).list();
        return new PageResponse<>(rows, request.getPageNumber(), request.getPageSize(), total);
    }

    private static String adminRows(PracticeType type, boolean filterStatus, boolean filterKeyword) {
        Source source = source(type);
        return "select a.id, '" + type.name() + "' as type, a.title, a.hsk_level, a.content_status as status,"
                + " a.updated_at, " + type.ordinal() + " as type_order" + adminFilterFrom(source, filterStatus, filterKeyword);
    }

    private static String adminCountRows(PracticeType type, boolean filterStatus, boolean filterKeyword) {
        return "select count(*) as cnt" + adminFilterFrom(source(type), filterStatus, filterKeyword);
    }

    private static String adminFilterFrom(Source source, boolean filterStatus, boolean filterKeyword) {
        StringBuilder sql = new StringBuilder(source.from()).append(source.scope());
        if (filterStatus) {
            sql.append(" and a.content_status = :status");
        }
        if (filterKeyword) {
            sql.append(" and locate(:keyword, lower(a.title)) > 0");
        }
        return sql.toString();
    }
}
