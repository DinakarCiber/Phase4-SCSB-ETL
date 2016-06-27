package org.recap.repository;

import org.recap.model.jpa.BibliographicEntity;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * Created by pvsubrah on 6/10/16.
 */
public interface BibliographicDetailsRepository extends PagingAndSortingRepository<BibliographicEntity, Integer> {
    public Long countByOwningInstitutionId(Integer institutionId);
    public List<BibliographicEntity> findByOwningInstitutionId(Pageable pageable, Integer institutionId);
}
