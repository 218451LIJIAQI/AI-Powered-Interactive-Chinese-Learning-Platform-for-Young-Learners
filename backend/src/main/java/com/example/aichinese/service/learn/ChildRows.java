package com.example.aichinese.service.learn;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Writes the items of an edited Practice set or level check question to its child rows by position: item {@code i}
 * keeps the row it had at position {@code i}, so an edit keeps every item's row id, and rows past the new last item
 * are deleted. What names those ids goes on over the edit: a Practice run in progress and a waiting mistake on one of
 * its items ({@link PracticeService#mistakeRef}), or a level check part-way through, which is graded on the option ids
 * its answers name.
 */
final class ChildRows {

    private ChildRows() {
    }

    /** Fills one row from the item at {@code position}. */
    @FunctionalInterface
    interface Writer<R, I> {

        void write(R row, int position, I item);
    }

    /** {@code existing} are the parent's managed rows in position order. */
    static <R, I> void writeByPosition(List<R> existing, List<I> items, Supplier<R> newRow,
            JpaRepository<R, Long> repository, Writer<R, I> writer) {
        if (existing.size() > items.size()) {
            repository.deleteAll(existing.subList(items.size(), existing.size()));
        }
        int retained = Math.min(existing.size(), items.size());
        for (int position = 0; position < retained; position++) {
            writer.write(existing.get(position), position, items.get(position));
        }
        for (int position = retained; position < items.size(); position++) {
            R row = newRow.get();
            writer.write(row, position, items.get(position));
            repository.save(row);
        }
    }
}
