package org.example.neustackapp.repository;

import jakarta.persistence.LockModeType;
import org.example.neustackapp.model.DiscountCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountCouponRepository extends JpaRepository<DiscountCoupon, Integer> {

    Optional<DiscountCoupon> findByCode(String code);

    Optional<DiscountCoupon> findByCodeAndIsUsedFalse(String code);

    List<DiscountCoupon> findByIsUsedFalse();

    long countByIsUsed(Boolean isUsed);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DiscountCoupon d WHERE d.code = :code")
    Optional<DiscountCoupon> findByCodeForUpdate(@Param("code") String code);
}
