package fr.insastronaute.webapi.article.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.articles.media-cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ArticleMediaGarbageCollectorScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleMediaGarbageCollectorScheduler.class);

    private final ArticleMediaGarbageCollectorService garbageCollectorService;

    public ArticleMediaGarbageCollectorScheduler(ArticleMediaGarbageCollectorService garbageCollectorService) {
        this.garbageCollectorService = garbageCollectorService;
    }

    @Scheduled(cron = "${app.articles.media-cleanup-cron:0 0 3 * * *}")
    public void deleteOrphanedArticleMedia() {
        try {
            garbageCollectorService.deleteOrphanedArticleMedia();
        } catch (RuntimeException exception) {
            LOGGER.warn("Article media S3 cleanup failed.", exception);
        }
    }
}
