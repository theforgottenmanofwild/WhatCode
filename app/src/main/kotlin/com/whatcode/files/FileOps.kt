package com.whatcode.files

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** A file operation that could not be done. Its message is written to be shown to the person as it is. */
class FileOpException(message: String) : IOException(message)

/** The rules for names typed in the file explorer, and the free name for a copy. No Android here (unit tested). */
object FileNames {

    /** What is wrong with [name] as a file or folder name, or null when it can be used. */
    fun problem(name: String): String? = when {
        name.isBlank() -> "Type a name"
        name == "." || name == ".." -> "That name can't be used"
        name.contains('/') -> "A name can't contain /"
        name.contains('\u0000') -> "That name can't be used"
        name.toByteArray(Charsets.UTF_8).size > 255 -> "That name is too long"
        else -> null
    }

    /**
     * [name] if it is free, otherwise the first free one of `name (2).ext`, `name (3).ext` and so on. A name that
     * already ends in a number in brackets counts on from it. The extension is what follows the last dot, unless
     * the name starts with that dot (`.gitignore` has none).
     */
    fun unique(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.')
        val hasExt = dot > 0
        val stem = if (hasExt) name.substring(0, dot) else name
        val ext = if (hasExt) name.substring(dot) else ""
        val numbered = Regex("""^(.*) \((\d{1,9})\)$""").find(stem)
        val base = numbered?.groupValues?.get(1) ?: stem
        var n = (numbered?.groupValues?.get(2)?.toIntOrNull() ?: 1) + 1
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!taken(candidate)) return candidate
            n++
        }
    }
}

/**
 * The file explorer's file operations on the phone's storage: new file, new folder, rename, delete, copy and move.
 * Each one returns the file it made, or throws a [FileOpException] that says what went wrong. They read and write
 * storage, so call them off the main thread. No Android here (unit tested).
 */
object FileOps {

    fun createFile(dir: File, name: String): File {
        val target = target(dir, name)
        try {
            if (!target.createNewFile()) throw FileOpException("Couldn't create ${target.name}")
        } catch (e: FileOpException) {
            throw e
        } catch (e: Exception) {
            throw FileOpException("Couldn't create ${target.name}: ${e.message ?: e.javaClass.simpleName}")
        }
        return target
    }

    fun createFolder(dir: File, name: String): File {
        val target = target(dir, name)
        if (!target.mkdir()) throw FileOpException("Couldn't create ${target.name}")
        return target
    }

    /** Gives [file] the name [newName] in the same folder. The same name again changes nothing. */
    fun rename(file: File, newName: String): File {
        if (newName == file.name) return file
        if (!file.exists()) throw FileOpException("${file.name} is no longer there")
        val parent = file.parentFile ?: throw FileOpException("${file.name} can't be renamed")
        // Only the case changes (readme to README): some storage treats both as one name, so go through a free one.
        if (newName.equals(file.name, ignoreCase = true)) {
            FileNames.problem(newName)?.let { throw FileOpException(it) }
            val target = File(parent, newName)
            if (target.exists() && !Files.isSameFile(target.toPath(), file.toPath())) {
                throw FileOpException("$newName already exists")
            }
            val step = File(parent, FileNames.unique(".${file.name}.rename") { File(parent, it).exists() })
            if (!file.renameTo(step) || !step.renameTo(target)) throw FileOpException("Couldn't rename ${file.name}")
            return target
        }
        val target = target(parent, newName)
        if (!file.renameTo(target)) throw FileOpException("Couldn't rename ${file.name}")
        return target
    }

    /** Deletes [file], and everything inside it if it is a folder. A link is removed, never what it points to. */
    fun delete(file: File) {
        if (Files.isSymbolicLink(file.toPath())) {
            if (!file.delete()) throw FileOpException("Couldn't delete ${file.name}")
            return
        }
        if (!file.exists()) return
        if (!file.deleteRecursively()) throw FileOpException("Couldn't delete everything in ${file.name}")
    }

    /** Copies [source] (a file, or a folder with all it holds) into [destDir]. A taken name gets a number. */
    fun copy(source: File, destDir: File): File {
        checkInto(source, destDir, "copied")
        val target = File(destDir, FileNames.unique(source.name) { File(destDir, it).exists() })
        try {
            if (!source.copyRecursively(target, overwrite = false)) throw FileOpException("Couldn't copy ${source.name}")
        } catch (e: FileOpException) {
            throw e
        } catch (e: Exception) {
            target.deleteRecursively()
            throw FileOpException("Couldn't copy ${source.name}: ${e.message ?: e.javaClass.simpleName}")
        }
        return target
    }

    /**
     * Moves [source] into [destDir]. Moving it to the folder it is already in changes nothing. A taken name gets a
     * number. When the two places are on different storage, it is copied and the original deleted.
     */
    fun move(source: File, destDir: File): File {
        if (source.parentFile?.absolutePath == destDir.absolutePath) return source
        checkInto(source, destDir, "moved")
        val target = File(destDir, FileNames.unique(source.name) { File(destDir, it).exists() })
        if (source.renameTo(target)) return target
        val copied = copy(source, destDir)
        if (!source.deleteRecursively()) {
            throw FileOpException("${source.name} was copied, but the original couldn't be deleted")
        }
        return copied
    }

    private fun target(dir: File, name: String): File {
        FileNames.problem(name)?.let { throw FileOpException(it) }
        if (!dir.isDirectory) throw FileOpException("${dir.name} is not a folder")
        val target = File(dir, name)
        if (target.exists()) throw FileOpException("$name already exists")
        return target
    }

    private fun checkInto(source: File, destDir: File, verb: String) {
        if (!source.exists()) throw FileOpException("${source.name} is no longer there")
        if (!destDir.isDirectory) throw FileOpException("${destDir.name} is not a folder")
        if (source.isDirectory && ExplorerPaths.isInside(destDir.absolutePath, source.absolutePath)) {
            throw FileOpException("A folder can't be $verb into itself")
        }
    }
}
