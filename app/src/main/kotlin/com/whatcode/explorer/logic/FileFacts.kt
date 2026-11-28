package com.whatcode.explorer.logic

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission

/**
 * What the Properties of a file or folder show, read from storage without following a link (so a link is shown as
 * the link, with where it points). Read it off the main thread. No Android here (unit tested).
 */
class FileFacts(
    val path: String,
    val isDir: Boolean,
    val isLink: Boolean,
    /** Where a link points, as written in the link; null for anything else or a link that can't be read. */
    val linkTarget: String?,
    /** A file's size in bytes; a folder's own entry, which is not what it holds (that is measured separately). */
    val size: Long,
    val modified: Long,
    /** When it was made, or null when the storage does not keep it (it then reports the change date). */
    val created: Long?,
    val accessed: Long?,
    /** `rwxr-x---` for owner, group and others, or null when the storage has no such permissions. */
    val permissions: String?,
    val canRead: Boolean,
    val canWrite: Boolean,
) {
    companion object {
        fun read(file: File): FileFacts {
            val path = file.toPath()
            val link = Files.isSymbolicLink(path)
            val attrs = try {
                Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            } catch (_: Exception) {
                null
            }
            val target = if (link) {
                try {
                    Files.readSymbolicLink(path).toString()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            val perms = try {
                permissionText(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))
            } catch (_: Exception) {
                null
            }
            val modified = attrs?.lastModifiedTime()?.toMillis() ?: file.lastModified()
            val created = attrs?.creationTime()?.toMillis()?.takeIf { it > 0 && it != modified }
            return FileFacts(
                path = file.absolutePath,
                isDir = attrs?.isDirectory ?: (!link && file.isDirectory),
                isLink = link,
                linkTarget = target,
                size = attrs?.size() ?: file.length(),
                modified = modified,
                created = created,
                accessed = attrs?.lastAccessTime()?.toMillis()?.takeIf { it > 0 },
                permissions = perms,
                canRead = file.canRead(),
                canWrite = file.canWrite(),
            )
        }

        /** `rwxr-x---` for a set of POSIX permissions: owner, group and others, each read, write and run. */
        fun permissionText(set: Set<PosixFilePermission>): String {
            val order = listOf(
                PosixFilePermission.OWNER_READ to 'r', PosixFilePermission.OWNER_WRITE to 'w', PosixFilePermission.OWNER_EXECUTE to 'x',
                PosixFilePermission.GROUP_READ to 'r', PosixFilePermission.GROUP_WRITE to 'w', PosixFilePermission.GROUP_EXECUTE to 'x',
                PosixFilePermission.OTHERS_READ to 'r', PosixFilePermission.OTHERS_WRITE to 'w', PosixFilePermission.OTHERS_EXECUTE to 'x',
            )
            return order.joinToString("") { (p, c) -> if (p in set) c.toString() else "-" }
        }
    }
}
