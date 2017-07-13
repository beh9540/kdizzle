package models

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object Todos : IntIdTable() {
    val name = varchar("name", 256)
}

object TodoEntries : IntIdTable() {
    val done = bool("done")
    val text = varchar("text", 256)
    val todo = reference("todo", Todos)
}

class Todo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Todo>(Todos)

    var name by Todos.name
    val entries by TodoEntry referrersOn TodoEntries.todo
}

class TodoEntry(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TodoEntry>(TodoEntries)

    var done by TodoEntries.done
    var text by TodoEntries.text
    var todo by Todo referencedOn TodoEntries.todo
}
