package mflix.api.daos;

import com.mongodb.*;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Comment;
import mflix.api.models.Critic;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.*;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Component
public class CommentDao extends AbstractMFlixDao {
	static String COMMENT_COLLECTION = "comments";
	private MongoCollection<Comment> commentCollection;
	private CodecRegistry pojoCodecRegistry;
	private final Logger log;

	@Autowired
	public CommentDao(
			MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
		super(mongoClient, databaseName);
		log = LoggerFactory.getLogger(this.getClass());
		this.db = this.mongoClient.getDatabase(MFLIX_DATABASE);
		this.pojoCodecRegistry =
				fromRegistries(
						MongoClientSettings.getDefaultCodecRegistry(),
						fromProviders(PojoCodecProvider.builder().automatic(true).build()));
		this.commentCollection =
				db.getCollection(COMMENT_COLLECTION, Comment.class).withCodecRegistry(pojoCodecRegistry);
	}

	/**
	 * Returns a Comment object that matches the provided id string.
	 *
	 * @param id - comment identifier
	 * @return Comment object corresponding to the identifier value
	 */
	public Comment getComment(String id) {
		return commentCollection.find(new Document("_id", new ObjectId(id))).first();
	}

	/**
	 * Adds a new Comment to the collection. The equivalent instruction in the mongo shell would be:
	 *
	 * <p>db.comments.insertOne({comment})
	 *
	 * <p>
	 *
	 * @param comment - Comment object.
	 * @throw IncorrectDaoOperation if the insert fails, otherwise
	 * returns the resulting Comment object.
	 */
	public Comment addComment(Comment comment) {
		if (comment.getId() == null) throw new IncorrectDaoOperation("Fake comment - no id found!");
		commentCollection.withWriteConcern(WriteConcern.MAJORITY).insertOne(comment);
		// TODO> Ticket - Handling Errors: Implement a try catch block to handle a potential write exception when given a wrong commentId.
		return commentCollection.find(eq("_id", comment.getOid())).first();
	}

	/**
	 * Updates the comment text matching commentId and user email. This method would be equivalent to
	 * running the following mongo shell command:
	 *
	 * <p>db.comments.update({_id: commentId}, {$set: { "text": text, date: ISODate() }})
	 *
	 * <p>
	 *
	 * @param commentId - comment id string value.
	 * @param text      - comment text to be updated.
	 * @param email     - user email.
	 * @return true if successfully updates the comment text.
	 */
	public boolean updateComment(String commentId, String text, String email) {
		UpdateResult updateResult = commentCollection.withWriteConcern(WriteConcern.MAJORITY)
				.updateOne(and(eq("email", email), eq("_id", new ObjectId(commentId)))
						, and(set("text", text), set("date", new Date())));
		if (updateResult.getMatchedCount() > 0) {
			if (updateResult.getModifiedCount() != 1) {
				log.warn("Comment `{}` text was not updated. Is it the same text?", commentId);
			}
			return true;
		}
		log.error("Could not update comment `{}`. Make sure the comment is owned by `{}`",
				commentId, email);
		return false;
		// TODO> Ticket - Handling Errors: Implement a try catch block to handle a potential write exception when given a wrong commentId.+
	}

	/**
	 * Deletes comment that matches user email and commentId.
	 *
	 * @param commentId - commentId string value.
	 * @param email     - user email value.
	 * @return true if successful deletes the comment.
	 */
	public boolean deleteComment(String commentId, String email) {
		return commentCollection.deleteOne(and(eq("email", email), eq("_id", new ObjectId(commentId)))).getDeletedCount() > 0;
		// TODO> Ticket Handling Errors - Implement a try catch block to handle a potential write exception when given a wrong commentId.
	}

	/**
	 * Ticket: User Report - produce a list of users that comment the most in the website. Query the
	 * `comments` collection and group the users by number of comments. The list is limited to up most
	 * 20 commenter.
	 *
	 * @return List {@link Critic} objects.
	 */
	public List<Critic> mostActiveCommenters() {
		List<Critic> mostActive = new ArrayList<>();
		db.getCollection(COMMENT_COLLECTION, Critic.class).
				withCodecRegistry(pojoCodecRegistry).
				withReadConcern(ReadConcern.MAJORITY).aggregate(Arrays.asList(
						sortByCount("$email"),
						sort(descending("count")),
						limit(20))).into(mostActive);
		return mostActive;
	}
}
