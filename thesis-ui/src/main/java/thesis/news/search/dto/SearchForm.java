package thesis.news.search.dto;

import lombok.*;
import thesis.news.article.dto.Article;
import thesis.utils.dto.CommonForm;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class SearchForm extends CommonForm {
    private String search;
    private String topic;
    private List<Article> articles;
    private String errorMsg;
}
