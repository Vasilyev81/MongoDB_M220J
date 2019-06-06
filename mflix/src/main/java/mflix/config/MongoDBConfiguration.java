package mflix.config;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.connection.SslSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Configuration
@Service
public class MongoDBConfiguration {
	@Bean
	@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
	public MongoClient mongoClient(@Value("${spring.mongodb.uri}") String connectionString) {

		ConnectionString connString = new ConnectionString(connectionString);

		WriteConcern wc = WriteConcern.MAJORITY.withJournal(true).withWTimeout(2500, TimeUnit.MILLISECONDS);
		MongoClientSettings settings =
				MongoClientSettings.builder()
						.applyConnectionString(connString)
						.writeConcern(wc)
						.build();
		MongoClient mongoClient = MongoClients.create(settings);

		return mongoClient;
	}
}

