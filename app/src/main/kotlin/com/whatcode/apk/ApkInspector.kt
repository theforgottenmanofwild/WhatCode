package com.whatcode.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/** Reads full package metadata, hashes, certificates and installed-app comparison from an APK file. */
object ApkInspector {

    data class SignerInfo(
        val sha256: String,
        val subject: String,
        val issuer: String,
        val notBefore: Long,
        val notAfter: Long,
    )

    data class PermInfo(val name: String, val dangerous: Boolean)

    data class Report(
        val appName: String,
        val icon: Drawable?,
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
        val minSdk: Int,
        val targetSdk: Int,
        val compileSdk: Int,
        val permissions: List<PermInfo>,
        val activities: List<String>,
        val services: List<String>,
        val receivers: List<String>,
        val providers: List<String>,
        val abis: List<String>,
        val sizeBytes: Long,
        val sha256: String?,
        val md5: String?,
        val signers: List<SignerInfo>,
        val compare: ApkCompare.Result,
    )

    /** Reads [file] off the main thread. Returns null if cancelled or if Android cannot parse the APK. */
    fun inspect(
        context: Context,
        file: File,
        needSha256: Boolean,
        needMd5: Boolean,
        cancelled: () -> Boolean = { false },
    ): Report? {
        if (cancelled() || !file.isFile) return null
        val pm = context.packageManager
        val pkg = archiveInfo(pm, file.absolutePath, fullFlags()) ?: return null
        if (cancelled()) return null

        val appInfo = pkg.applicationInfo
        if (appInfo != null) {
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
        }
        val label = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg.packageName
        val icon = try {
            appInfo?.loadIcon(pm)
        } catch (_: Exception) {
            null
        }
        val verCode = versionCodeOf(pkg)
        val minSdk = appInfo?.minSdkVersion ?: 0
        val targetSdk = appInfo?.targetSdkVersion ?: 0
        val compileSdk = if (Build.VERSION.SDK_INT >= 31 && appInfo != null) appInfo.compileSdkVersion else 0

        val perms = (pkg.requestedPermissions ?: emptyArray()).map { name ->
            val dangerous = try {
                val pi = pm.getPermissionInfo(name, 0)
                @Suppress("DEPRECATION")
                val base = if (Build.VERSION.SDK_INT >= 28) pi.protection else pi.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
                base == PermissionInfo.PROTECTION_DANGEROUS
            } catch (_: Exception) {
                false
            }
            PermInfo(name, dangerous)
        }

        val abis = readAbis(file)
        if (cancelled()) return null

        val digests = if (needSha256 || needMd5) {
            try {
                file.inputStream().use { ApkHashes.compute(it, needSha256, needMd5, cancelled) }
            } catch (_: Exception) {
                ApkHashes.Digests(null, null)
            } ?: return null
        } else {
            ApkHashes.Digests(null, null)
        }

        val archiveSigners = extractSigners(pkg)
        val archiveCertSet = archiveSigners.map { it.sha256 }.toSet()

        val installed = installedInfo(pm, pkg.packageName)
        val installedCode = installed?.let(::versionCodeOf)
        val installedCerts = installed?.let(::extractSigners)?.map { it.sha256 }?.toSet()
        val compare = ApkCompare.compare(
            installedVersion = installed?.versionName,
            installedCode = installedCode,
            installedCerts = installedCerts,
            archiveCode = verCode,
            archiveCerts = archiveCertSet,
            sdkInt = Build.VERSION.SDK_INT,
        )

        return Report(
            appName = label,
            icon = icon,
            packageName = pkg.packageName,
            versionName = pkg.versionName,
            versionCode = verCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            permissions = perms,
            activities = (pkg.activities ?: emptyArray()).map { it.name },
            services = (pkg.services ?: emptyArray()).map { it.name },
            receivers = (pkg.receivers ?: emptyArray()).map { it.name },
            providers = (pkg.providers ?: emptyArray()).map { it.name },
            abis = abis,
            sizeBytes = file.length(),
            sha256 = digests.sha256,
            md5 = digests.md5,
            signers = archiveSigners,
            compare = compare,
        )
    }

    /** Formats a [Report] into readable plain text for display and clipboard copy. */
    fun formatDetails(r: Report): String {
        val sb = StringBuilder()
        val df = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        sb.appendLine("App name: ${r.appName}")
        sb.appendLine("Package: ${r.packageName}")
        sb.appendLine("Version: ${r.versionName ?: "?"} (${r.versionCode})")
        sb.appendLine("Min SDK: ${ApkNames.sdkLabel(r.minSdk)}")
        sb.appendLine("Target SDK: ${ApkNames.sdkLabel(r.targetSdk)}")
        if (r.compileSdk > 0) sb.appendLine("Compile SDK: ${ApkNames.sdkLabel(r.compileSdk)}")
        sb.appendLine("Native ABIs: ${if (r.abis.isEmpty()) "None (pure Java/Kotlin)" else r.abis.joinToString(", ")}")
        sb.appendLine("File size: ${String.format(Locale.US, "%,d bytes", r.sizeBytes)}")
        r.sha256?.let { sb.appendLine("SHA-256: $it") }
        r.md5?.let { sb.appendLine("MD5: $it") }

        sb.appendLine()
        sb.appendLine("SIGNING CERTIFICATES (${r.signers.size})")
        if (r.signers.isEmpty()) {
            sb.appendLine("Unsigned (no signing certificate)")
        } else {
            r.signers.forEachIndexed { idx, s ->
                if (idx > 0) sb.appendLine()
                sb.appendLine("SHA-256: ${s.sha256}")
                sb.appendLine("Subject: ${s.subject}")
                sb.appendLine("Issuer: ${s.issuer}")
                sb.appendLine("Valid: ${df.format(Date(s.notBefore))} — ${df.format(Date(s.notAfter))}")
            }
        }

        appendList(sb, "PERMISSIONS", r.permissions.map {
            val short = it.name.removePrefix("android.permission.")
            if (it.dangerous) "$short [dangerous]" else short
        })
        appendList(sb, "ACTIVITIES", r.activities.map { ApkNames.shortComponent(r.packageName, it) })
        appendList(sb, "SERVICES", r.services.map { ApkNames.shortComponent(r.packageName, it) })
        appendList(sb, "RECEIVERS", r.receivers.map { ApkNames.shortComponent(r.packageName, it) })
        appendList(sb, "PROVIDERS", r.providers.map { ApkNames.shortComponent(r.packageName, it) })
        return sb.toString().trimEnd()
    }

    private fun appendList(sb: StringBuilder, title: String, items: List<String>) {
        sb.appendLine()
        sb.appendLine("$title (${items.size})")
        if (items.isEmpty()) sb.appendLine("None")
        else items.forEach { sb.appendLine("• $it") }
    }

    private fun readAbis(file: File): List<String> = try {
        ZipFile(file).use { z ->
            val names = ArrayList<String>()
            val entries = z.entries()
            while (entries.hasMoreElements()) {
                val n = entries.nextElement().name
                if (n.startsWith("lib/")) names.add(n)
            }
            ApkAbis.fromEntryNames(names)
        }
    } catch (_: Exception) {
        emptyList()
    }

    @Suppress("DEPRECATION")
    private fun signingFlag(): Int =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun fullFlags(): Int = signingFlag() or
        PackageManager.GET_PERMISSIONS or
        PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or
        PackageManager.GET_PROVIDERS

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String, flags: Int): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageArchiveInfo(path, flags)
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo? = try {
        val flags = signingFlag()
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageInfo(pkg, flags)
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(pkg: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else pkg.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun extractSigners(pkg: PackageInfo): List<SignerInfo> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val info = pkg.signingInfo ?: return emptyList()
            info.apkContentsSigners ?: info.signingCertificateHistory ?: emptyArray()
        } else {
            pkg.signatures ?: emptyArray()
        }
        if (sigs.isEmpty()) return emptyList()
        val cf = CertificateFactory.getInstance("X.509")
        val out = ArrayList<SignerInfo>(sigs.size)
        for (s in sigs) {
            val raw = s.toByteArray()
            val fp = ApkHashes.certSha256Colon(raw)
            val cert = try {
                cf.generateCertificate(ByteArrayInputStream(raw)) as? X509Certificate
            } catch (_: Exception) {
                null
            }
            out.add(
                SignerInfo(
                    sha256 = fp,
                    subject = cert?.subjectX500Principal?.name ?: "Unknown",
                    issuer = cert?.issuerX500Principal?.name ?: "Unknown",
                    notBefore = cert?.notBefore?.time ?: 0L,
                    notAfter = cert?.notAfter?.time ?: 0L,
                )
            )
        }
        return out
    }
}
