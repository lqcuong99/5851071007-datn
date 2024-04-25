package thesis.common.mongo.repo;

import org.bson.Document;
import thesis.common.mongo.operator.MongoDBOperatorImp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MongoDBRepository<Clazz> {
    MongoDBOperatorImp<Clazz> getMongoDBOperation();

    List<Clazz> find(Map<String, Object> query, Map<String, Object> sort, int skip, int limit);

    List<Clazz> findAll(Map<String, Object> query, Map<String, Object> sort, Map<String, Object> projection);

    Optional<Clazz> findOne(Map<String, Object> query, Map<String, Object> sort);

    Optional<Long> count(Document query);

    Optional<Boolean> insert(Clazz object);

    Optional<Boolean> insertMany(List<Clazz> objects);

    Optional<Boolean> update(Map<String, Object> query, Map<String, Object> data);
}