package io.picstr.app.repository;

import io.picstr.app.model.Photo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

	List<Photo> findByDeleteDateIsNull(Sort sort);

	Page<Photo> findByDeleteDateIsNull(Pageable pageable);

	List<Photo> findByDeleteDateIsNullAndCategory_NameIgnoreCase(String categoryName, Sort sort);

	Page<Photo> findByDeleteDateIsNullAndCategory_NameIgnoreCase(String categoryName, Pageable pageable);

	List<Photo> findDistinctByDeleteDateIsNullAndTags_NameIgnoreCase(String tagName, Sort sort);

	Page<Photo> findDistinctByDeleteDateIsNullAndTags_NameIgnoreCase(String tagName, Pageable pageable);

	List<Photo> findByDeleteDateIsNotNull(Sort sort);

	Page<Photo> findByDeleteDateIsNotNull(Pageable pageable);

	List<Photo> findByDeleteDateBefore(Instant threshold);

	Optional<Photo> findByIdAndDeleteDateIsNull(Long id);

	Optional<Photo> findByIdAndDeleteDateIsNotNull(Long id);

	Optional<Photo> findByInternalFilename(String internalFilename);
}
