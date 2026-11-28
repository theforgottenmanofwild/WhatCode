package com.whatcode.files

import com.whatcode.explorer.logic.ExplorerPaths
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
     * the name starts with that dot (`.gitignore` has none). A [folder] has no extension at all (`v1.2` becomes
     * `v1.2 (2)`, not `v1 (2).2`).
     */
    fun unique(name: String, folder: Boolean = false, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.')
        val hasExt = dot > 0 && !folder
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

    /**
     * What a copy or a move does when the name is already taken in the folder it goes to: give the new one a number
     * ([KEEP_BOTH]), put it in place of the one that is there ([REPLACE]), or leave it out ([SKIP]).
     */
    enum class Clash { KEEP_BOTH, REPLACE, SKIP }

    /** True when copying or moving [source] into [destDir] would meet a name that is already taken there. */
    fun clashes(source: File, destDir: File): Boolean {
        if (source.parentFile?.absolutePath == destDir.absolutePath) return false
        return exists(File(destDir, source.name))
    }

    private fun exists(file: File): Boolean = file.exists() || Files.isSymbolicLink(file.toPath())

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
            if (!file.renameTo(step)) throw FileOpException("Couldn't rename ${file.name}")
            if (!step.renameTo(target)) {
                step.renameTo(file) // put it back, so it is never left under the hidden in-between name
                throw FileOpException("Couldn't rename ${file.name}")
            }
            return target
        }
        val target = target(parent, newName)
        if (!file.renameTo(target)) throw FileOpException("Couldn't rename ${file.name}")
        return target
    }

    /**
     * Deletes [file], and everything inside it if it is a folder. A link is removed, never what it points to, and
     * that holds for a link inside a folder as much as for [file] itself.
     */
    fun delete(file: File, cancelled: () -> Boolean = { false }) {
        if (cancelled()) throw FileOpException("Cancelled")
        if (!Files.isSymbolicLink(file.toPath()) && !file.exists()) return
        if (!deleteTree(file, cancelled)) {
            if (cancelled()) throw FileOpException("Cancelled")
            throw FileOpException(if (file.isDirectory) "Couldn't delete everything in ${file.name}" else "Couldn't delete ${file.name}")
        }
    }

    /**
     * Copies [source] (a file, or a folder with all it holds) into [destDir]. A taken name gets a number, unless
     * [clash] says to replace what is there or to skip it. Returns the copy, or null when it was skipped.
     */
    fun copy(
        source: File,
        destDir: File,
        clash: Clash = Clash.KEEP_BOTH,
        cancelled: () -> Boolean = { false },
    ): File? {
        if (cancelled()) throw FileOpException("Cancelled")
        checkInto(source, destDir, "copied", followLink = true)
        val taken = File(destDir, source.name)
        if (exists(taken) && clash != Clash.KEEP_BOTH && !sameFile(taken, source)) {
            if (clash == Clash.SKIP) return null
            replaceGuard(source, destDir)
            return replace(taken) { copy(source, destDir, Clash.KEEP_BOTH, cancelled)!! }
        }
        val target = File(destDir, FileNames.unique(source.name, source.isDirectory) { File(destDir, it).exists() })
        try {
            copyTree(source, target, top = true, cancelled = cancelled)
        } catch (e: FileOpException) {
            deleteTree(target)
            throw e
        } catch (e: Exception) {
            deleteTree(target)
            throw FileOpException("Couldn't copy ${source.name}: ${e.message ?: e.javaClass.simpleName}")
        }
        return target
    }

    /**
     * Moves [source] into [destDir]. Moving it to the folder it is already in changes nothing. A taken name gets a
     * number. When the two places are on different storage, it is copied and the original deleted.
     */
    fun move(
        source: File,
        destDir: File,
        clash: Clash = Clash.KEEP_BOTH,
        cancelled: () -> Boolean = { false },
    ): File? {
        if (cancelled()) throw FileOpException("Cancelled")
        if (source.parentFile?.absolutePath == destDir.absolutePath) return source
        checkInto(source, destDir, "moved", followLink = false)
        val taken = File(destDir, source.name)
        if (exists(taken) && clash != Clash.KEEP_BOTH && !sameFile(taken, source)) {
            if (clash == Clash.SKIP) return null
            replaceGuard(source, destDir)
            return replace(taken) { move(source, destDir, Clash.KEEP_BOTH, cancelled)!! }
        }
        val target = File(destDir, FileNames.unique(source.name, source.isDirectory) { File(destDir, it).exists() })
        if (source.renameTo(target)) return target
        val copied = copy(source, destDir, Clash.KEEP_BOTH, cancelled)!!
        if (!deleteTree(source)) {
            throw FileOpException("${source.name} was copied, but the original couldn't be deleted")
        }
        return copied
    }

    private fun sameFile(a: File, b: File): Boolean = try {
        Files.isSameFile(a.toPath(), b.toPath())
    } catch (_: Exception) {
        false
    }

    /**
     * Puts what [make] brings into the place of [taken]. [make] runs first, so the new one lands under a numbered
     * name while the old one is still there; only then is the old one deleted and the new one renamed to its name.
     * A copy that fails leaves the old one as it was. What [make] brings must not be inside [taken], which is checked
     * before it is called ([checkInto] refuses a folder going into itself, and [replaceGuard] the other way round).
     */
    private fun replace(taken: File, make: () -> File): File {
        val made = make()
        if (made.absolutePath == taken.absolutePath) return made
        if (!deleteTree(taken)) {
            throw FileOpException("${taken.name} couldn't be replaced, so the new one is ${made.name}")
        }
        if (!made.renameTo(taken)) throw FileOpException("${taken.name} was replaced, but the new one is still called ${made.name}")
        return taken
    }

    /** Refuses to replace a folder that holds [source]: deleting it would delete what is being pasted. */
    private fun replaceGuard(source: File, destDir: File) {
        val taken = File(destDir, source.name)
        if (!exists(taken) || Files.isSymbolicLink(taken.toPath()) || !taken.isDirectory) return
        if (isPathInside(source, taken)) {
            throw FileOpException("${source.name} can't replace the folder it is in")
        }
    }

    /** What [measure] counted: bytes in files, and how many files and folders, links counted as files and not followed. */
    class Size(val bytes: Long, val files: Int, val folders: Int)

    /**
     * Adds up the sizes of everything in [folder] (or the size of a file), without following links. [cancelled] is
     * asked as it goes, and returns null as soon as it says true. [progress] gets the totals so far now and then.
     */
    fun measure(folder: File, cancelled: () -> Boolean = { false }, progress: (Size) -> Unit = {}): Size? {
        var bytes = 0L
        var files = 0
        var folders = 0
        val todo = ArrayDeque<File>()
        todo.add(folder)
        var steps = 0
        while (todo.isNotEmpty()) {
            if (cancelled()) return null
            val f = todo.removeLast()
            val link = Files.isSymbolicLink(f.toPath())
            if (!link && f.isDirectory) {
                if (f != folder) folders++
                f.listFiles()?.let { todo.addAll(it) }
            } else {
                files++
                if (!link) bytes += f.length()
            }
            if (++steps % 500 == 0) progress(Size(bytes, files, folders))
        }
        return Size(bytes, files, folders)
    }

    private fun target(dir: File, name: String): File {
        FileNames.problem(name)?.let { throw FileOpException(it) }
        if (!dir.isDirectory) throw FileOpException("${dir.name} is not a folder")
        val target = File(dir, name)
        if (target.exists()) throw FileOpException("$name already exists")
        return target
    }

    /**
     * Refuses a source that is gone, a destination that is not a folder, and a folder going into itself. Places are
     * compared by where they really are, so a link can't hide that the destination is inside the source. A [followLink]
     * source that is a link counts as what it points to (a copy reads that), otherwise as the link itself (a move
     * takes the link).
     */
    private fun checkInto(source: File, destDir: File, verb: String, followLink: Boolean) {
        if (!source.exists()) throw FileOpException("${source.name} is no longer there")
        if (!destDir.isDirectory) throw FileOpException("${destDir.name} is not a folder")
        val isLink = Files.isSymbolicLink(source.toPath())
        if (isLink && !followLink) return
        if (source.isDirectory && isPathInside(destDir, source)) {
            throw FileOpException("A folder can't be $verb into itself")
        }
    }

    private fun isPathInside(path: File, folder: File): Boolean =
        path.canonicalFile.toPath().startsWith(folder.canonicalFile.toPath())

    /** Deletes [file] and what is inside it, leaving what a link points to alone. False if something is left. */
    private fun deleteTree(file: File, cancelled: () -> Boolean = { false }): Boolean {
        if (cancelled()) return false
        var all = true
        if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory) {
            val kids = file.listFiles()
            if (kids == null) all = false else for (kid in kids) {
                if (cancelled()) return false
                if (!deleteTree(kid, cancelled)) all = false
            }
        }
        return (file.delete() || !file.exists()) && all
    }

    /**
     * Copies [source] to [target], which is new. A link inside a folder is copied as a link, never followed, so a
     * loop can't make the copy go on and what it points to is not copied by accident. The [top] source is read as
     * what it points to when it is a link. Files and folders keep their date.
     */
    private fun copyTree(source: File, target: File, top: Boolean, cancelled: () -> Boolean = { false }) {
        if (cancelled()) throw FileOpException("Cancelled")
        val path = source.toPath()
        if (!top && Files.isSymbolicLink(path)) {
            Files.copy(path, target.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
            return
        }
        if (source.isDirectory) {
            if (!target.mkdir()) throw FileOpException("Couldn't create ${target.name}")
            val kids = source.listFiles() ?: throw FileOpException("Couldn't read ${source.name}")
            for (kid in kids) copyTree(kid, File(target, kid.name), top = false, cancelled = cancelled)
        } else {
            val buf = ByteArray(32 * 1024)
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        if (cancelled()) throw FileOpException("Cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                    }
                }
            }
        }
        target.setLastModified(source.lastModified())
    }
}
