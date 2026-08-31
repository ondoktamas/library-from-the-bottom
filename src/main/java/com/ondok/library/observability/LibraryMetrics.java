package com.ondok.library.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.springframework.stereotype.Component;

@Component
public class LibraryMetrics {

    private final LongCounter booksBorrowedCounter;

    public LibraryMetrics(Meter meter) {
        this.booksBorrowedCounter = meter
                .counterBuilder("library.books.borrowed")
                .setDescription("Number of successful book borrow operations")
                .setUnit("{book}")
                .build();
    }

    public void recordBorrow(String bookId, String borrowerId) {
        booksBorrowedCounter.add(1, Attributes.builder()
                .put("book.id", bookId)
                .put("borrower.id", borrowerId)
                .build());
    }
}
