package thesis.core.article.model.topic;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.*;
import org.bson.types.ObjectId;
import thesis.utils.dto.CommonDTO;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class Topic extends CommonDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId id;
    private String topic;
    private Set<String> articleIds;
}
