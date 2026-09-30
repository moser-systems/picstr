package io.picstr.app.repository;

import io.picstr.app.model.Photo;
import io.picstr.app.model.PhotoLocation;
import io.picstr.app.model.ProcessingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PhotoRepository extends JpaRepository<Photo, Long>, JpaSpecificationExecutor<Photo> {

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

	List<Photo> findByProcessingStatus(ProcessingStatus processingStatus);

	boolean existsByInternalFilenameInAndDeleteDateIsNotNull(Collection<String> internalFilenames);

	@Query("select new io.picstr.app.model.PhotoLocation(p.id, p.latitude, p.longitude, p.internalFilename, p.originalFilename)"
			+ " from Photo p where p.deleteDate is null and p.latitude is not null and p.longitude is not null"
			+ " and p.processingStatus = io.picstr.app.model.ProcessingStatus.READY")
	List<PhotoLocation> findActiveLocations();
}
