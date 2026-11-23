package com.example.aichinese.service.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * {@code student_profiles.school_name}, which no code maps any more. V2 drops it only where it held no value, so an
 * upgraded database may still keep the school names students gave earlier; deleting a student's account clears theirs.
 * The column is looked up on every call: a database V2 emptied of it, or a schema Hibernate built, has none.
 */
@Component
@RequiredArgsConstructor
public class RetainedSchoolNames {

    private final JdbcTemplate jdbcTemplate;

    /** Clears the school name of the account's student profile, where the column still exists. */
    public void clear(Long userId) {
        if (columnExists()) {
            jdbcTemplate.update("update student_profiles set school_name = null where user_id = ?", userId);
        }
    }

    private boolean columnExists() {
        Integer columns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = 'student_profiles' and column_name = 'school_name'
                """, Integer.class);
        return columns != null && columns > 0;
    }
}
