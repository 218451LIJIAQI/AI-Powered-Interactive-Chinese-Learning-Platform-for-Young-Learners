package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LiveClassEnrolment;

public interface LiveClassEnrolmentRepository extends JpaRepository<LiveClassEnrolment, Long> {

    @EntityGraph(attributePaths = { "student", "student.user" })
    List<LiveClassEnrolment> findByLiveClassIdIn(Collection<Long> liveClassIds);
}
