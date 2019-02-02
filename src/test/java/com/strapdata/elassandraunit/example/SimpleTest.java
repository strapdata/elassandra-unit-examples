package com.strapdata.elassandraunit.example;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import org.apache.http.HttpHost;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.dataset.cql.SimpleCQLDataSet;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SimpleTest {

    static final String KEYSPACE = "ks";

    @ClassRule
    public static CassandraCQLUnit cassandraCQLUnit =
        new CassandraCQLUnit(new SimpleCQLDataSet(
            "CREATE TABLE users (email text PRIMARY KEY, firstname text, lastname text, es_query text, es_options text);", KEYSPACE));

    private static Mapper<User> userMapper;
    private static RestHighLevelClient client;

    @Before
    public void setup() throws IOException {
        userMapper = new MappingManager(cassandraCQLUnit.session).mapper(User.class);
        client = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));

        // create an elasticsearch index on table users
        CreateIndexRequest request = new CreateIndexRequest("users");
        request.mapping("users", XContentFactory.jsonBuilder()
            .startObject()
                .startObject("users")
                    .field("discover", ".*")
                .endObject()
            .endObject());
        request.settings(Settings.builder()
            .put("keyspace", KEYSPACE)                          // map index users to our keyspace.
            .put("index.synchronous_refresh",true)// synchronous elasticsearch refresh
            .build());
        CreateIndexResponse createIndexResponse = client.indices().create(request);
    }

    @Test
    public void testMapper() throws Exception {
        User user1 = new User().withEmail("user1@test.com").withFirstname("Bob").withLastname("Smith");
        User user2 = new User().withEmail("user2@test.com").withFirstname("Alice").withLastname("Smith");
        User user3 = new User().withEmail("user3@test.com").withFirstname("Paul").withLastname("Dupont");
        userMapper.save(user1);
        userMapper.save(user2);
        userMapper.save(user3);

        User user = userMapper.get("user1@test.com");
        assertThat(user, is(user1));

        // Elasticsearch search through the REST API
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(QueryBuilders.termQuery("lastname", "Smith"));
        SearchRequest searchRequest = new SearchRequest().indices("users").source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest);
        assertThat(searchResponse.getHits().totalHits, is(2L));

        // Elasticsearch search through CQL
        String esQuery = new SearchSourceBuilder().query(QueryBuilders.termQuery("lastname", "Smith")).toString(ToXContent.EMPTY_PARAMS);
        ResultSet results = cassandraCQLUnit.session.execute(
            "SELECT * FROM users WHERE es_options = ? AND es_query = ? ALLOW FILTERING", "indices=users", esQuery);
        Result<User> users = userMapper.map(results);
        assertThat(users.all().size(), is(2));
    }
}
