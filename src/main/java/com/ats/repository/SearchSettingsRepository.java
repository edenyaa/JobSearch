package com.ats.repository;

import com.ats.domain.SearchSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SearchSettingsRepository extends JpaRepository<SearchSettings, Long> {

	Optional<SearchSettings> findByUserId(Long userId);

	@Query("select s.user.id from SearchSettings s where s.daily = true")
	List<Long> findDailyUserIds();

}
