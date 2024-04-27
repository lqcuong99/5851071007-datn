package thesis.core.app.article.service;

import thesis.core.app.article.Article;

import java.util.List;
import java.util.Optional;

public interface ArticleService {
    List<Article> findByUrls(List<String> urls);

    Optional<Boolean> add(Article article);

    Optional<Boolean> addMany(List<Article> articles);
}
