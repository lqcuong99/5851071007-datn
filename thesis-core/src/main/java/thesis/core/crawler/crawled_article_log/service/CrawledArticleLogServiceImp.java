package thesis.core.crawler.crawled_article_log.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import thesis.core.crawler.crawled_article_log.CrawledArticleLog;
import thesis.core.crawler.crawled_article_log.repository.CrawledArticleLogRepository;

import java.util.Optional;

@Component
public class CrawledArticleLogServiceImp implements CrawledArticleLogService {
    @Autowired
    private CrawledArticleLogRepository errorArticleRepository;

    @Override
    public Optional<Boolean> add(CrawledArticleLog crawledArticleLog) {
        return errorArticleRepository.insert(crawledArticleLog);
    }
}
