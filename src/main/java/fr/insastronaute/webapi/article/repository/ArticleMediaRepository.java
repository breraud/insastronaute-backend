package fr.insastronaute.webapi.article.repository;

import fr.insastronaute.webapi.article.domain.ArticleMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArticleMediaRepository extends JpaRepository<ArticleMedia, UUID> {

    List<ArticleMedia> findByArticleIdOrderByCreatedAtAsc(UUID articleId);

    @Query("select media.objectKey from ArticleMedia media")
    List<String> findAllObjectKeys();
}
