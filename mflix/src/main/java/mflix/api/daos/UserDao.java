package mflix.api.daos;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Session;
import mflix.api.models.User;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class UserDao extends AbstractMFlixDao {
	private final MongoCollection<User> usersCollection;
	private final MongoCollection<Session> sessionsCollection;
	private final Logger log;

	@Autowired
	public UserDao(
			MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
		super(mongoClient, databaseName);
		CodecRegistry pojoCodecRegistry =
				fromRegistries(
						MongoClientSettings.getDefaultCodecRegistry(),
						fromProviders(PojoCodecProvider.builder().automatic(true).build()));

		usersCollection = db.getCollection("users", User.class).withCodecRegistry(pojoCodecRegistry);
		log = LoggerFactory.getLogger(this.getClass());
		sessionsCollection = db.getCollection("sessions", Session.class).withCodecRegistry(pojoCodecRegistry);
	}

	/**
	 * Inserts the `user` object in the `users` collection.
	 *
	 * @param user - User object to be added
	 * @return True if successful, throw IncorrectDaoOperation otherwise
	 */
	public boolean addUser(User user) {
		try {
			usersCollection.withWriteConcern(WriteConcern.MAJORITY).insertOne(user);
		} catch (MongoWriteException e) {
			if (e.getError().getCategory().equals(ErrorCategory.DUPLICATE_KEY))
				throw new IncorrectDaoOperation("Throwed in addUser method", e);
		}

		return true;
		//TODO > Ticket: Handling Errors - make sure to only add new users
		// and not users that already exist.

	}

	/**
	 * Creates session using userId and jwt token.
	 *
	 * @param userId - user string identifier
	 * @param jwt    - jwt string token
	 * @return true if successful
	 */
	public boolean createUserSession(String userId, String jwt) {
		Bson updateFilter = new Document("user_id", userId);
		Bson setUpdate = set("jwt", jwt);
		UpdateOptions options = new UpdateOptions().upsert(true);
		sessionsCollection.updateOne(updateFilter, setUpdate, options);
		return true;
		//TODO > Ticket: Handling Errors - implement a safeguard against
		// creating a session with the same jwt token.
	}

	/**
	 * Returns the User object matching the an email string value.
	 *
	 * @param email - email string to be matched.
	 * @return User object or null.
	 */
	public User getUser(String email) {
		return usersCollection.find(eq("email", email)).first();
	}

	/**
	 * Given the userId, returns a Session object.
	 *
	 * @param userId - user string identifier.
	 * @return Session object or null.
	 */
	public Session getUserSession(String userId) {
		return sessionsCollection.find(eq("user_id", userId)).first();
	}

	public boolean deleteUserSessions(String userId) {
		DeleteResult res = sessionsCollection.deleteOne(eq("user_id", userId));
		if (res.getDeletedCount() < 1) {
			log.warn("User `{}` could not be found in sessions collection.", userId);
		}
		return res.wasAcknowledged();
	}

	/**
	 * Removes the user document that match the provided email.
	 *
	 * @param email - of the user to be deleted.
	 * @return true if user successfully removed
	 */
	public boolean deleteUser(String email) {
		// remove user sessions
		if (deleteUserSessions(email)) {
			DeleteResult res = usersCollection.deleteOne(eq("email", email));
			if (res.getDeletedCount() < 1) {
				log.warn("User with `email` {} not found. Potential concurrent operation?!", email);
			}
			return res.wasAcknowledged();
		}
		//TODO > Ticket: Handling Errors - make this method more robust by
		// handling potential exceptions.
		return false;
	}

	/**
	 * Updates the preferences of an user identified by `email` parameter.
	 *
	 * @param email           - user to be updated email
	 * @param userPreferences - set of preferences that should be stored and replace the existing
	 *                        ones. Cannot be set to null value
	 * @return User object that just been updated.
	 */
	public boolean updateUserPreferences(String email, Map<String, ?> userPreferences) {
		if (userPreferences == null) throw new IncorrectDaoOperation("UserPreferences cannot be set to null");
		UpdateResult res = usersCollection.updateOne(eq("email", email), set("preferences", userPreferences));
		if (res.getModifiedCount() < 1) {
			log.warn("User `{}` was not updated. Trying to re-write the same `preferences` field: `{}`",
					email, userPreferences);
			return false;
		}
		//TODO > Ticket: Handling Errors - make this method more robust by
		// handling potential exceptions when updating an entry.
		return true;
	}
}
