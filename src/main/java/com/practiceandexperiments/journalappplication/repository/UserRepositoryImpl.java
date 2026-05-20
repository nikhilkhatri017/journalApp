package com.practiceandexperiments.journalappplication.repository;

import com.practiceandexperiments.journalappplication.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserRepositoryImpl {

    @Autowired
    MongoTemplate mongoTemplate;

    public List<User> getUsersForSA(){
        Query query = new Query();
        query.addCriteria(Criteria.where("email").exists(true));
        query.addCriteria(Criteria.where("email").ne(null).ne(""));
        query.addCriteria(Criteria.where("email").regex("^(?=.{1,64}@)[\\p{L}0-9!#$%&'*+/=?^_`{|}~-]+"
                + "(\\.[\\p{L}0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                + "[^-][\\p{L}0-9-]+(\\.[\\p{L}0-9-]+)*(\\.[\\p{L}]{2,})$")); // used to validate email address

        query.addCriteria(Criteria.where("sentimentAnalysis").is(true));

        // is used to blacklist anyone you want who seems to be misusing or wasting your resources
//        query.addCriteria(Criteria.where("email").nin("xyz", "xyzwewe", "kjnsdfkj"));

        // filtering out people based on roles
//        query.addCriteria(Criteria.where("roles").in("USER", "ADMIN"));

        // below given is an example of filtering based on data type
//        query.addCriteria(Criteria.where("sentimentAnalysis").type(JsonSchemaObject.Type.BsonType.BOOLEAN));
        List<User> users = mongoTemplate.find(query, User.class);
        return users;
    }
}
