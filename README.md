# library-from-the-bottom

A Library Management System REST API (plus a small admin UI) built to demonstrate REST API design,
database interaction, automated testing, and observability practices in Java.

## AI usage disclosure

This project was built with AI coding agent assistance (Claude). The full prompt-by-prompt log —
what was asked, what came back, and what was manually reviewed/adjusted afterward — is kept at
[`src/docs/Progress log of the project.pdf`](src/docs/Progress%20log%20of%20the%20project.pdf).

## Technical stack and tools

| Concern              | Tool / Library                                                        |
|-----------------------|------------------------------------------------------------------------|
| Language & runtime    | Java 21                                                                |
| Framework             | Spring Boot 3.3.5 (`spring-boot-starter-web`, `-data-jpa`, `-validation`) |
| Build tool            | Maven                                                                  |
| Database              | H2, in-memory, accessed via Spring Data JPA / Hibernate                |
| Observability         | OpenTelemetry SDK (manual wiring, `opentelemetry-exporter-logging`) — 1 custom span, 1 custom metric |
| Unit testing          | JUnit 5, Mockito, AssertJ                                              |
| Integration testing   | Spring Boot Test, MockMvc, real H2 database                            |
| Test reporting        | Allure (`allure-junit5` + `allure-maven`) — human-readable HTML report of every test run |
| Frontend (admin UI)   | Static HTML/CSS/vanilla JS (`src/main/resources/static`), no build step, calls the real REST API |
| Manual API testing    | `requests.http` (IntelliJ/VS Code REST Client), `postman_collection.json` |

No frontend build tooling (Node/npm) is required — the admin UI is plain static assets served directly
by Spring Boot.

## Setting up and running the project locally

### 1. Install prerequisites

#### You can use an IDE that bundles everything needed, such as IntelliJ IDEA.

Otherwise, you need a **JDK 21** and **Maven** (no Maven Wrapper is bundled, so use your own `mvn`). For Maven, a package installer is needed depending on you work on Windows or Mac.

#### JDK:
If not yet installed, download JDK from here and install on your matching system: https://www.oracle.com/java/technologies/downloads/#jdk26

#### Maven:
- with Chocolatey on *Windows*:
  - Open Windows PowerShell with Run as administrator.
  - Check your system execution policy by typing `Get-ExecutionPolicy`.
    - If it returns *Restricted*, change it by running `Set-ExecutionPolicy AllSigned` or `Set-ExecutionPolicy Bypass -Scope Process` so the installer script can run.
  - Copy and paste the official install commands into the PowerShell: 
```bash
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
```
```bash
choco install temurin21 maven
```
- with Homebrew on *Mac*:
  - Open a Terminal, and install Homebrew (follow all instructions), then install Maven with Homebrew
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```
```bash
brew install maven
```
If you prefer any other way than these, follow the official instructions here: https://maven.apache.org/install.html

### 2. Verify the installation

```bash
java -version
mvn -version
```

You should see Java 21.x and a Maven version in the output. If the commands aren't found right after
installing, open a new terminal (or restart your IDE) so it picks up the updated `PATH`.

### 3. Get the project

```bash
git clone <this-repository-url>
cd library-from-the-bottom
```

(If you already have the folder locally, just `cd` into it.)

### 4. Build the project

```bash
mvn compile
```

This downloads all dependencies (first run only) and compiles the source. No database setup step is
needed — H2 is in-memory and configured entirely through [`application.yml`](src/main/resources/application.yml).

### 5. Run the application

```bash
mvn spring-boot:run
```

Wait for a line like:

```
Started LibraryApplication in 2.5 seconds (process running for 2.7)
```

The app is now running on `http://localhost:8080`.

### 6. Open it

| What                  | URL                                     |
|------------------------|------------------------------------------|
| Admin UI               | `http://localhost:8080/`                 |
| REST API base          | `http://localhost:8080/api/`             |
| H2 database console    | `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:librarydb`, user `sa`, empty password) |

On startup, [`data.sql`](src/main/resources/data.sql) automatically seeds the database with 10 sample
books and 10 sample borrowers, so there's data to look at immediately — no manual setup needed.

### 7. Stop the application

Press `Ctrl+C` in the terminal running `mvn spring-boot:run`. Since the database is in-memory, all data
(including anything you added through the UI/API) is discarded on stop — the next run starts fresh from
the seed data again.

## Data model

- **Book**: `title`, `author`, `yearOfPublication`, `edition`, `quantity` (a non-negative integer — how
  many copies are currently available to borrow), plus a server-generated `id` — lowercase, underscore-joined:
  `author_title_yearofpublication_edition` (e.g. `robert_c_martin_clean_code_2008_1st`).
- **Borrower**: `name` (letters and spaces only), `dateOfBirth`, `address`, plus a server-generated
  `id`: `name_dateofbirth` (e.g. `jane_doe_19900512`).

IDs are generated once, at creation time, from those fields. **Editing a book or borrower afterward
does not regenerate the ID** — it stays fixed so loan references and URLs never break.

### Borrowing rules

- A library can hold multiple copies of the same book — `quantity` tracks how many are currently free.
- Adding a book whose title/author/year/edition match an existing one (i.e. generates the same ID)
  does **not** fail with a conflict — it adds the new request's `quantity` onto the existing book's
  `quantity` instead, and returns the updated book. Borrowers only ever see one entry per book.
- Borrowing decrements `quantity` by one; returning increments it back. Once `quantity` reaches 0,
  further borrow attempts get `409 Conflict` until a copy is returned.
- Different copies of the same book can go to different borrowers at the same time — borrowing just
  requires `quantity > 0`, it doesn't matter who else currently has a copy.
- One borrower can never hold more than one copy of the *same* book at once — a second borrow attempt
  for a book they already have on loan gets `409 Conflict`, regardless of remaining `quantity`. Once
  they return it, they're free to borrow that same book again.

## Endpoints

| Method | Path                          | Description                                                  |
|--------|-------------------------------|----------------------------------------------------------------|
| GET    | `/api/books`                   | List books, optionally filtered by `?id=`, `?title=`, `?author=` (title/author match case-insensitively, substring) |
| POST   | `/api/books`                   | Add a book (ID generated server-side); if one with the same ID already exists, adds this request's `quantity` onto it instead of creating a duplicate |
| PUT    | `/api/books/{bookId}`          | Update a book's title/author/year/edition (ID unchanged)     |
| DELETE | `/api/books/{bookId}`          | Delete a book (also removes its active loan, if any)         |
| POST   | `/api/books/{bookId}/borrow`   | Borrow a book (body: `{"borrowerId": "..."}`)                |
| GET    | `/api/borrowers`                | List all borrowers                                            |
| POST   | `/api/borrowers`                | Register a new borrower (ID generated server-side)             |
| GET    | `/api/borrowers/{id}`           | Get borrower details                                           |
| PUT    | `/api/borrowers/{id}`           | Update a borrower's name/date of birth/address (ID unchanged)  |
| DELETE | `/api/borrowers/{id}`           | Delete a borrower (also removes their active loan, if any)     |
| GET    | `/api/borrowers/{id}/books`     | List books currently borrowed by a borrower                    |
| GET    | `/api/loans`                    | List all active loans (with book title / borrower name)        |
| DELETE | `/api/loans/{loanId}`           | Return a book (deletes the loan, increments the book's quantity) |

Error responses: `400` (validation), `404` (not found), `409` (book has no copies left, the same borrower
already has this book on loan, or a borrower with the same generated ID already exists — adding a
duplicate *book* is not an error, see above).

### Example requests

```bash
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"Clean Code","author":"Robert C. Martin","yearOfPublication":2008,"edition":"1st","quantity":3}'

curl "http://localhost:8080/api/books?title=clean&author=martin"

curl -X POST http://localhost:8080/api/borrowers \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Doe","dateOfBirth":"1990-05-12","address":"123 Main St"}'

curl -X POST http://localhost:8080/api/books/robert_c_martin_clean_code_2008_1st/borrow \
  -H "Content-Type: application/json" \
  -d '{"borrowerId":"jane_doe_19900512"}'

curl http://localhost:8080/api/borrowers/jane_doe_19900512/books

curl -X DELETE http://localhost:8080/api/loans/1
```

## Admin UI

A small admin page ships at `http://localhost:8080/` (Spring Boot serves it as a static resource) —
add/edit/delete books and borrowers, filter the book list, and pair/return loans, all against the real
endpoints above (no mock data).

## Manual API testing

Two ready-to-run request collections walk through the full flow (add books, register a borrower,
borrow a book, hit the 404/409/400 error cases):

- [`requests.http`](requests.http) — open in IntelliJ IDEA (built-in HTTP Client) or VS Code with the
  "REST Client" extension, and run requests top to bottom.
- [`postman_collection.json`](postman_collection.json) — import into Postman/Insomnia and run via the
  Collection Runner; `bookId`/`borrowerId` are captured automatically from responses.

## Running the tests, and checking the results

### Run everything

```bash
mvn test
```

This project has two kinds of tests, both run by the command above:

- **Unit tests** — `*ServiceTest` and `IdGeneratorTest` under `src/test/java/.../service` and `.../util`.
  Mockito-mocked repositories, no Spring context, run in milliseconds.
- **Integration tests** — `*ControllerIntegrationTest` under `src/test/java/.../controller`. Full
  `@SpringBootTest` + `MockMvc`, hitting real HTTP endpoints against a real (in-memory) H2 database.

To run only one group:

```bash
mvn test -Dtest=*ServiceTest,IdGeneratorTest        # unit tests only
mvn test -Dtest=*ControllerIntegrationTest           # integration tests only
mvn test -Dtest=BookServiceTest                      # a single class
```

### Where to see the results

- **Console output** (while `mvn test` runs): a per-class summary line, e.g.

  ```
  [INFO] Running library.service.BookServiceTest
  [INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.514 s
  ```

  followed by an overall summary at the end:

  ```
  [INFO] Results:
  [INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```

  Any failure prints the assertion error and stack trace right there in the console.

- **Surefire reports on disk** (generated after every `mvn test` run, kept even after the terminal closes):
  `target/surefire-reports/`
  - `*.txt` — a human-readable summary per test class
  - `*.xml` — the same data in JUnit XML format (what CI systems typically parse)

- **From an IDE** (IntelliJ IDEA / VS Code with the Java extensions): right-click a test class or method
  → *Run*. Results show in the built-in test runner panel with a green/red tree per test, and you can
  re-run just the failed ones.

- **Allure HTML report** — a browsable, human-readable report of the same run (per-suite pass/fail,
  durations, a trend graph, failure stack traces), built from both the unit and integration tests with
  no extra test code needed:

  ```bash
  mvn test                     # writes raw results to target/allure-results
  mvn io.qameta.allure:allure-maven:2.15.2:report
  ```

  This generates a static site at `target/site/allure-maven-plugin/`. Because it fetches its data via
  JS, opening `index.html` directly (`file://`) shows a blank page — serve the folder instead, e.g. with
  the JDK's built-in server:

  ```bash
  cd target/site/allure-maven-plugin
  ```

  ```bash
  jwebserver -p 8811
  ```

  then open `http://localhost:8811/`. (If you have the standalone Allure CLI installed, `mvn
  io.qameta.allure:allure-maven:2.15.2:serve` does the build-and-serve-and-open in one step.)

## Observability: checking the span and the metric

`BookService.borrowBook()` is instrumented with one custom **span** and one custom **metric**, both
exported via OpenTelemetry's console logging exporter — nothing external to install or configure to see
them.

### Seeing the span

1. Start the app: `mvn spring-boot:run` (leave the terminal open — this is where the output appears).
2. Trigger a borrow, any of these work:
   - In the [Admin UI](http://localhost:8080/), go to the **Loans** tab, pick a book and a borrower, and
     click **Pair (Borrow)**.
   - `POST /api/books/{bookId}/borrow` via `curl`, `requests.http`, or the Postman collection.
3. Look at the terminal running the app. A line like this appears immediately:

   ```
   INFO ... i.o.e.logging.LoggingSpanExporter : 'borrow-book' : 6622366f7e7b373e8125249a2bef4e9e 1017d9a6fe4da721 INTERNAL [tracer: library-management-system:] AttributesMap{data={book.id=robert_c_martin_clean_code_2008_1st, borrower.id=jane_doe_19900512}, capacity=128, totalAddedValues=2}
   ```

   That's the `borrow-book` span: its trace ID, span ID, kind (`INTERNAL`), and the two attributes
   (`book.id`, `borrower.id`) it was tagged with in [`BookService.java`](src/main/java/library/service/BookService.java).

The same span also prints during `mvn test`, since several integration tests call the borrow endpoint —
scroll the test console output for `LoggingSpanExporter` lines.

### Seeing the metric

The `library.books.borrowed` counter (defined in [`LibraryMetrics.java`](src/main/java/library/observability/LibraryMetrics.java))
increments on every successful borrow. It's exported on a 10-second timer (not immediately like the
span), so after triggering a borrow, wait up to 10 seconds and look for a `LoggingMetricExporter` line
in the same terminal:

```
INFO ... i.o.e.logging.LoggingMetricExporter : Received a collection of 1 metrics for export.
INFO ... i.o.e.logging.LoggingMetricExporter : metric: ImmutableMetricData{...name=library.books.borrowed, ...points=[...value=1...]}
```

### Swapping in a real backend later

Both exporters are wired in [`OpenTelemetryConfig.java`](src/main/java/library/config/OpenTelemetryConfig.java).
To send this to an actual observability backend (e.g. Jaeger, an OTel Collector) instead of the console,
replace `LoggingSpanExporter` / `LoggingMetricExporter` with `OtlpGrpcSpanExporter` / `OtlpGrpcMetricExporter`
pointed at the collector's endpoint — nothing else in the codebase needs to change.
