package com.example.aichinese.repository.projection;

/** One row of a grouped count: the id of the parent row and the number of matching child rows. */
public interface IdCountProjection {

    Long getId();

    long getTotal();
}
