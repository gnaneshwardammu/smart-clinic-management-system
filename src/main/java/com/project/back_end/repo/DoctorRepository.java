package com.project.back_end.repo;

import com.project.back_end.models.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Searches ACTIVE doctors. Pass "" (empty string) for a filter you do not want to use.
     */
    @Query("""
            select d from Doctor d
            where d.active = true
              and lower(d.name) like lower(concat('%', :name, '%'))
              and (:specialty = '' or lower(d.specialty) = lower(:specialty))
            order by d.name
            """)
    List<Doctor> search(@Param("name") String name, @Param("specialty") String specialty);

    @Query("select distinct d.specialty from Doctor d where d.active = true order by d.specialty")
    List<String> findActiveSpecialties();

    /** Locks the doctor row so two simultaneous bookings for the same doctor cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
}
