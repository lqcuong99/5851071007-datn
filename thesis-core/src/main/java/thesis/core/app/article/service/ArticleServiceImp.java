package thesis.core.app.article.service;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import thesis.core.app.article.Article;
import thesis.core.app.article.command.CommandQueryArticle;
import thesis.core.app.article.repository.ArticleRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ArticleServiceImp implements ArticleService {
    @Autowired
    private ArticleRepository articleRepository;

    @Override
    public Optional<Long> count(CommandQueryArticle command) {
        return articleRepository.count(new Document());
    }

    @Override
    public List<Article> getMany(CommandQueryArticle command) {
        Map<String, Object> sort = new HashMap<>();
        if (command.getIsDescCreatedDate() != null)
            sort.put("createdDate", command.getIsDescCreatedDate() ? -1 : 1);
        if (command.getIsDescPublicationDate() != null)
            sort.put("publicationDate", command.getIsDescPublicationDate() ? -1 : 1);
        return articleRepository.find(new Document(), sort, (command.getPage() - 1) * command.getSize(), command.getSize());
    }

    @Override
    public Optional<Boolean> add(Article article) {
        return articleRepository.insert(article);
    }

    @Override
    public Optional<Boolean> addMany(List<Article> articles) {
        return articleRepository.insertMany(articles);
    }
}
