package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The single allocation row survives deletion of the requests whose numbers it issued. */
@Getter
@Setter
@Entity
@Table(name = "support_request_numbers")
public class SupportRequestNumber {

    @Id
    private Integer id;

    // A long can represent exhaustion of the public integer number without wrapping around.
    @Column(name = "next_number", nullable = false)
    private long nextNumber;
}
