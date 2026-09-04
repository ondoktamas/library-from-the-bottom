package library.concurrency;

import library.dto.BookRequest;
import library.dto.BorrowRequest;
import library.dto.BorrowerRequest;
import library.repository.BookRepository;
import library.repository.BorrowerRepository;
import library.repository.LoanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency regression tests for the borrow/return stock accounting.
 *
 * <p>Unlike the other integration tests these deliberately do <em>not</em> use
 * {@code @Transactional} and run over a real HTTP port: a test-managed
 * transaction would serialise everything and hide exactly the interleaving
 * under test. Data is cleaned up explicitly instead.
 *
 * <p>This class also guards the OpenTelemetry wiring: its distinct property set
 * forces a second Spring context in the same JVM, which fails to start outright
 * if the SDK ever goes back to registering itself as the {@code GlobalOpenTelemetry}
 * singleton.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        // Each blocked borrow holds a pooled connection while it waits on the
        // row lock, so the pool must comfortably exceed the thread count below.
        "spring.datasource.hikari.maximum-pool-size=32"
})
class BorrowConcurrencyIntegrationTest {

    private static final int THREADS = 10;

    @Autowired private TestRestTemplate rest;
    @Autowired private LoanRepository loanRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private BorrowerRepository borrowerRepository;

    @AfterEach
    void cleanUp() {
        loanRepository.deleteAll();
        bookRepository.deleteAll();
        borrowerRepository.deleteAll();
    }

    @Test
    void concurrentBorrowsNeverHandOutMoreCopiesThanExist() throws Exception {
        int copies = 3;
        String bookId = createBook("Race Test", "Concurrency Author", 2020, "1st", copies);
        List<String> borrowerIds = createBorrowers(THREADS);

        List<Integer> statuses = inParallel(borrowerIds.stream()
                .map(borrowerId -> (Callable<Integer>) () -> borrow(bookId, borrowerId))
                .toList());

        long created = statuses.stream().filter(s -> s == 201).count();
        long conflicts = statuses.stream().filter(s -> s == 409).count();

        assertThat(statuses).as("no request may fail with a server error").allMatch(s -> s < 500);
        assertThat(created).as("exactly one 201 per available copy").isEqualTo(copies);
        assertThat(conflicts).as("every other borrower is turned away").isEqualTo(THREADS - copies);
        assertThat(loanRepository.count()).as("one loan row per copy actually lent").isEqualTo(copies);
        assertThat(bookRepository.findById(bookId).orElseThrow().getQuantity())
                .as("stock is exhausted, never negative").isZero();
    }

    @Test
    void concurrentDuplicateBorrowsBySameBorrowerYieldExactlyOneLoan() throws Exception {
        String bookId = createBook("Duplicate Test", "Concurrency Author", 2021, "1st", THREADS);
        String borrowerId = createBorrowers(1).get(0);

        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            attempts.add(() -> borrow(bookId, borrowerId));
        }
        List<Integer> statuses = inParallel(attempts);

        assertThat(statuses).as("no request may fail with a server error").allMatch(s -> s < 500);
        assertThat(statuses.stream().filter(s -> s == 201).count())
                .as("one borrower may hold at most one copy of a given book").isEqualTo(1);
        assertThat(loanRepository.count()).isEqualTo(1);
        assertThat(bookRepository.findById(bookId).orElseThrow().getQuantity())
                .as("only one copy left the shelf").isEqualTo(THREADS - 1);
    }

    @Test
    void concurrentReturnsRestoreEveryCopy() throws Exception {
        String bookId = createBook("Return Test", "Concurrency Author", 2022, "1st", THREADS);
        List<String> borrowerIds = createBorrowers(THREADS);

        List<Long> loanIds = new ArrayList<>();
        for (String borrowerId : borrowerIds) {
            ResponseEntity<BorrowBody> response = rest.postForEntity(
                    "/api/books/{bookId}/borrow", new BorrowRequest(borrowerId), BorrowBody.class, bookId);
            assertThat(response.getStatusCode().value()).isEqualTo(201);
            loanIds.add(response.getBody().loanId());
        }
        assertThat(bookRepository.findById(bookId).orElseThrow().getQuantity()).isZero();

        List<Integer> statuses = inParallel(loanIds.stream()
                .map(loanId -> (Callable<Integer>) () -> {
                    ResponseEntity<Void> r = rest.exchange("/api/loans/{loanId}",
                            org.springframework.http.HttpMethod.DELETE, null, Void.class, loanId);
                    return r.getStatusCode().value();
                })
                .toList());

        assertThat(statuses).as("every return succeeds").allMatch(s -> s == 204);
        assertThat(loanRepository.count()).isZero();
        assertThat(bookRepository.findById(bookId).orElseThrow().getQuantity())
                .as("no returned copy is lost to a concurrent increment").isEqualTo(THREADS);
    }

    /** Runs the given calls on separate threads, released together to maximise overlap. */
    private <T> List<T> inParallel(List<Callable<T>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch startGun = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> call : calls) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    return call.call();
                }));
            }
            startGun.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int borrow(String bookId, String borrowerId) {
        return rest.postForEntity("/api/books/{bookId}/borrow",
                new BorrowRequest(borrowerId), String.class, bookId).getStatusCode().value();
    }

    private String createBook(String title, String author, int year, String edition, int quantity) {
        ResponseEntity<IdBody> response = rest.postForEntity("/api/books",
                new BookRequest(title, author, year, edition, quantity), IdBody.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return response.getBody().id();
    }

    private List<String> createBorrowers(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ResponseEntity<IdBody> response = rest.postForEntity("/api/borrowers",
                    new BorrowerRequest("Racer" + (char) ('A' + i), LocalDate.of(1990, 1, 1), "1 Test Way"),
                    IdBody.class);
            assertThat(response.getStatusCode().value()).isEqualTo(201);
            ids.add(response.getBody().id());
        }
        return ids;
    }

    private record IdBody(String id) {
    }

    private record BorrowBody(Long loanId) {
    }
}
