package com.ondok.library.repository;

import com.ondok.library.entity.Borrower;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerRepository extends JpaRepository<Borrower, String> {
}
