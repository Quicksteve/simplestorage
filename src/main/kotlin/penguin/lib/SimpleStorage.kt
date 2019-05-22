package penguin.lib

import com.google.gson.Gson
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * SimpleStorage provides an easy way to store an object as JSON in an sqlite database.
 */
class SimpleStorage<T>(dbFile: File, private val clazz: Class<T>) {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    /**
     * The Gson instance to use for serialization/deserialization
     */
    var gson = Gson()

    /**
     * The amount of historic values to save
     */
    var limit = 10
        set(value) {
            if (limit <= 0) {
                throw IllegalArgumentException("Limit must be at least 1: $value")
            }
            field = value
        }

    init {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS data
                (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    data TEXT NOT NULL
                );
            """.trimIndent()
            )
        }
    }

    /**
     * Loads the last record from the db, or null if it doesn't exist
     */
    fun load(): T? {
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT data.data as json FROM data ORDER BY id DESC LIMIT 1").use { resultSet ->
                return if (resultSet.next()) {
                    gson.fromJson(resultSet.getString("json"), clazz)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Saves the record to the database and clears records that are too old
     */
    fun save(data: T) {
        connection.prepareStatement("INSERT INTO data(data) values (?)").use { statement ->
            statement.setString(1, gson.toJson(data))
            statement.execute()
        }

        connection.createStatement().use { statement ->
            statement.execute(
                """
                DELETE FROM data WHERE id NOT IN (
                    SELECT id FROM data ORDER BY id DESC LIMIT $limit
                )
            """.trimIndent()
            )
        }
    }
}