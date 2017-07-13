package app

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import models.Todo
import models.TodoEntries
import models.TodoEntry
import models.Todos
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns
import org.jetbrains.exposed.sql.transactions.transaction
import spark.Spark.*

data class TodoEntryResponse(val id: Int, val done: Boolean, val text: String)
data class TodoEntryBody(val done: Boolean, val text: String)
data class TodoResponse(val id: Int, val name: String, val entries: List<TodoEntryResponse>)

fun main(args: Array<String>) {

    //obviously this isn't ideal
    Database.connect("jdbc:mysql://127.0.0.1:3306/kdizzle", user = "root", driver = "com.mysql.jdbc.Driver")
    transaction {
        createMissingTablesAndColumns(Todos, TodoEntries)
    }

    afterAfter({ _, response ->
        if (response.body()!=null) {
            response.type("application/json")
        }
        response.header("Cache-Control", "private, no-cache, no-store, max-age=0")
        response.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS,HEAD")
        // bad developer
        response.header("Access-Control-Allow-Origin", "*")
    })
    notFound { _, response ->
        response.type("application/json")
        ""
    }
    path("/todo") {
        get("") { req, res ->
            transaction {
                val offset = req.queryParamsValues("offset").firstOrNull()?.toInt() ?: 0
                val limit = req.queryParamsValues("limit").firstOrNull()?.toInt() ?: 10
                // this is kinda annoying, but it seems like the back reference in the DAO causes infinite recursion in
                // jackson
                val todos = Todo.all().limit(limit, offset).map { TodoResponse(it.id.value, it.name, it.entries.map{ TodoEntryResponse(it.id.value, it.done, it.text) }) }
                jacksonObjectMapper().writeValueAsString(todos)
            }
        }
        post("") { req, res ->
            val todo = jacksonObjectMapper().readValue<TodoResponse>(req.body())
            transaction {
                val todo_saved = Todo.new{
                    name = todo.name
                }
                res.status(200)
                todo_saved
            }
        }
        get("/:id") { req, _ ->
            transaction {
                val todoId = req.params("id").toInt()
                val todo = Todo.findById(todoId) ?: throw halt(404, "Todo: $todoId not found")
                // this is kinda annoying, but it seems like the back reference in the DAO causes infinite recursion in
                // jackson
                jacksonObjectMapper().writeValueAsString(TodoResponse(todo.id.value, todo.name, todo.entries.map{ TodoEntryResponse(it.id.value, it.done, it.text) }))
            }
        }
        put("/:id") { req, res ->
            transaction {
                val todoResp = jacksonObjectMapper().readValue<TodoResponse>(req.body())
                val todoId = req.params("id").toInt()
                val todo = Todo.findById(todoId) ?: throw halt(404, "Todo: $todoId not found")
                todo.name = todoResp.name

                res.status(202)
                todo
            }
        }
        delete("/:id") { req, res ->
            val todoId = req.params("id").toInt()
            transaction {
                Todo.findById(todoId)?.delete() ?: throw halt(404, "Todo: $todoId not found")
            }
            res.status(201)
        }
        post("/:id/entry") { req, res ->
            transaction {
                val todoId = req.params("id").toInt()
                val todoByID = Todo.findById(todoId) ?: throw halt(404, "Todo: $todoId not found")
                val entry = jacksonObjectMapper().readValue<TodoEntryBody>(req.body())
                TodoEntry.new {
                    done = entry.done
                    text = entry.text
                    todo = todoByID
                }
                res.status(202)
                jacksonObjectMapper().writeValueAsString(TodoResponse(todoByID.id.value, todoByID.name, todoByID.entries.map{ TodoEntryResponse(it.id.value, it.done, it.text) }))
            }
        }
        put("/:id/entry/:entry_id") { req, res ->
            val entryResp = jacksonObjectMapper().readValue<TodoEntryResponse>(req.body())
            transaction {
                val entryId = req.params("entry_id").toInt()
                val entry = TodoEntry.findById(entryId) ?: throw halt(404, "Entry: $entryId not found")
                if (entry.todo.id.value == req.params("id").toInt()) {
                    entry.text = entryResp.text
                    entry.done = entryResp.done
                    res.status(202)
                    entry
                } else {
                    res.status(404)
                }
            }
        }
        delete("/:id/entry/:entry_id") { req, res ->
            transaction {
                val entryId = req.params("entry_id").toInt()
                val entry = TodoEntry.findById(entryId) ?: throw halt(404, "Entry: $entryId not found")
                if (entry.todo.id.value == req.params("id").toInt()) {
                    entry.delete()
                    res.status(201)
                } else {
                    res.status(404)
                }
            }
        }
    }
}