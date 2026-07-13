package com.system.complaints.repository;

import com.system.complaints.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    @Query("select b from Branch b where lower(trim(b.bank)) in :banks")
    List<Branch> findByNormalizedBanks(@Param("banks") Set<String> banks);
}
