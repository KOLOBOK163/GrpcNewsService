package com.guildhub.grpc_news_service.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.guildhub.grpc_news_service.Entity.NewsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface NewsRepository extends JpaRepository<NewsEntity, Long> {
    Page<NewsEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
