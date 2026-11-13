package fr.insastronaute.webapi.article.repository;

import fr.insastronaute.webapi.article.domain.Article;
import fr.insastronaute.webapi.article.domain.ArticleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {

    /**
     * Les cles S3 des couvertures televersees.
     *
     * <p>Une couverture n'est pas une ligne article_media : sa cle vit dans une
     * colonne de l'article. Le ramasse-miettes des medias doit donc la lire ici
     * pour ne pas la prendre pour un orphelin. Les couvertures renseignees par
     * URL externe ont une cle nulle et ne concernent pas notre bucket.</p>
     */
    @Query("select article.coverImageObjectKey from Article article where article.coverImageObjectKey is not null")
    List<String> findAllCoverImageObjectKeys();

    @EntityGraph(attributePaths = "category")
    List<Article> findAllByOrderByUpdatedAtDescCreatedAtDesc();

    @EntityGraph(attributePaths = "category")
    List<Article> findByStatusOrderByUpdatedAtDescCreatedAtDesc(ArticleStatus status);

    @EntityGraph(attributePaths = "category")
    java.util.Optional<Article> findByIdAndStatus(UUID id, ArticleStatus status);

    boolean existsByCategoryId(UUID categoryId);
}
