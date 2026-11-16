package com.libremobile.core.model.undo

interface Command<T> {
    val description: String
    fun execute(target: T): T
    fun undo(target: T): T
}

class UndoRedoManager<T>(
    private val maxHistory: Int = 100
) {
    private val undoStack = ArrayDeque<Command<T>>()
    private val redoStack = ArrayDeque<Command<T>>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    fun execute(command: Command<T>, current: T): T {
        val next = command.execute(current)
        undoStack.addLast(command)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        return next
    }

    fun undo(current: T): T {
        if (undoStack.isEmpty()) return current
        val command = undoStack.removeLast()
        val reverted = command.undo(current)
        redoStack.addLast(command)
        return reverted
    }

    fun redo(current: T): T {
        if (redoStack.isEmpty()) return current
        val command = redoStack.removeLast()
        val reapplied = command.execute(current)
        undoStack.addLast(command)
        return reapplied
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
