-- Seed data for local/demo runs. Runs automatically against the H2 in-memory
-- database on every application start (see spring.jpa.defer-datasource-initialization
-- in application.yml). IDs are precomputed with the same slug rules as
-- IdGenerator, so they match what the API would generate for identical input.
-- Not loaded during tests - see spring.sql.init.mode=never on the integration
-- test classes.

INSERT INTO books (id, title, author, year_of_publication, edition, available) VALUES
('robert_c_martin_clean_code_2008_1st', 'Clean Code', 'Robert C. Martin', 2008, '1st', TRUE),
('joshua_bloch_effective_java_2018_3rd', 'Effective Java', 'Joshua Bloch', 2018, '3rd', TRUE),
('andrew_hunt_the_pragmatic_programmer_1999_1st', 'The Pragmatic Programmer', 'Andrew Hunt', 1999, '1st', TRUE),
('erich_gamma_design_patterns_1994_1st', 'Design Patterns', 'Erich Gamma', 1994, '1st', TRUE),
('george_orwell_1984_1949_1st', '1984', 'George Orwell', 1949, '1st', TRUE),
('aldous_huxley_brave_new_world_1932_1st', 'Brave New World', 'Aldous Huxley', 1932, '1st', TRUE),
('frank_herbert_dune_1965_1st', 'Dune', 'Frank Herbert', 1965, '1st', TRUE),
('j_r_r_tolkien_the_hobbit_1937_1st', 'The Hobbit', 'J.R.R. Tolkien', 1937, '1st', TRUE),
('yuval_noah_harari_sapiens_2011_1st', 'Sapiens', 'Yuval Noah Harari', 2011, '1st', TRUE),
('gabriel_garcia_marquez_cien_anos_de_soledad_1967_1st', 'Cien Años de Soledad', 'Gabriel García Márquez', 1967, '1st', TRUE);

INSERT INTO borrowers (id, name, date_of_birth, address) VALUES
('jane_doe_19900512', 'Jane Doe', '1990-05-12', '123 Main St'),
('john_smith_19850101', 'John Smith', '1985-01-01', '1 Elm Street'),
('emily_clark_19921123', 'Emily Clark', '1992-11-23', '45 Baker Street'),
('michael_brown_19780704', 'Michael Brown', '1978-07-04', '9 Liberty Ave'),
('sarah_johnson_20000229', 'Sarah Johnson', '2000-02-29', '22 Rose Lane'),
('david_wilson_19880915', 'David Wilson', '1988-09-15', '77 King Road'),
('laura_martinez_19950308', 'Laura Martinez', '1995-03-08', '5 Sunset Blvd'),
('daniel_lee_19831219', 'Daniel Lee', '1983-12-19', '310 Pine Street'),
('olivia_taylor_19990630', 'Olivia Taylor', '1999-06-30', '88 Maple Ave'),
('noah_anderson_19910417', 'Noah Anderson', '1991-04-17', '14 Birch Court');
