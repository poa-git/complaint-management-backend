package com.system.complaints.repository;

import com.system.complaints.model.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {

    // existing method - untouched
    Visitor findByName(String name);

    // NEW method (case-insensitive)
    Optional<Visitor> findFirstByNameIgnoreCase(String name);

    @Query("select distinct trim(v.city) from Visitor v " +
            "where v.city is not null and trim(v.city) <> '' order by trim(v.city)")
    List<String> findDistinctStationCities();
}
