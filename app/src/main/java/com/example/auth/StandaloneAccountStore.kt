package com.example.auth

import android.content.Context
import android.util.Base64
import com.example.model.AuthAccountProvider
import com.example.model.PerformanceCategory
import com.example.model.RegistrationDraft
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.security.UploadEncryptionManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Encrypted on-device credential vault for standalone **Creator** ([UserRole.CREATOR]) and
 * **Spectator** ([UserRole.SPECTATOR]) accounts.
 *
 * Security properties:
 * - Passwords are never persisted in plaintext; each account uses a unique 16-byte CSPRNG salt
 *   and PBKDF2-HMAC-SHA256 (65,536 iterations, 256-bit key) verified in constant time via
 *   [MessageDigest.isEqual].
 * - Account records and active standalone session pointers are stored inside
 *   [UploadEncryptionManager.putEncryptedPref] (AES-256-GCM envelope backed by Android Keystore).
 */
object StandaloneAccountStore {

    private const val PREF_KEY_ACCOUNTS_VAULT = "standalone_arena_accounts_v1"
    private const val PREF_KEY_ACTIVE_UID = "standalone_active_uid_v1"

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 65_536
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_BYTES = 16
    const val MIN_PASSWORD_LENGTH = 6
    private const val MIN_IDENTIFIER_LENGTH = 3

    private val vaultLock = Any()
    private val secureRandom = SecureRandom()

    data class StandaloneAccountSummary(
        val uid: String,
        val identifier: String,
        val displayName: String,
        val role: UserRole,
        val primaryCategory: PerformanceCategory
    )

    private data class StoredStandaloneRecord(
        val uid: String,
        val normalizedIdentifier: String,
        val rawIdentifier: String,
        val email: String?,
        val displayName: String,
        val role: UserRole,
        val stageName: String,
        val stageTitle: String,
        val bio: String,
        val primaryCategory: PerformanceCategory,
        val passwordSaltBase64: String,
        val passwordHashBase64: String,
        val createdAtMillis: Long,
        val updatedAtMillis: Long
    ) {
        fun toUserAccount(): UserAccount = UserAccount(
            uid = uid,
            email = email ?: rawIdentifier,
            displayName = displayName,
            photoUrl = null,
            role = role,
            stageName = stageName,
            stageTitle = stageTitle,
            bio = bio,
            primaryCategory = primaryCategory,
            registrationComplete = true,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            authProvider = AuthAccountProvider.STANDALONE
        )

        fun toSummary(): StandaloneAccountSummary = StandaloneAccountSummary(
            uid = uid,
            identifier = rawIdentifier,
            displayName = stageName.ifBlank { displayName },
            role = role,
            primaryCategory = primaryCategory
        )
    }

    /**
     * Registers a new standalone Creator or Spectator account encrypted at rest and optionally
     * activates the authenticated session immediately.
     */
    fun registerAccount(
        context: Context,
        identifier: String,
        password: String,
        draft: RegistrationDraft,
        activateSession: Boolean = true
    ): Result<UserAccount> {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)

        val trimmedIdentifier = identifier.trim()
        val normalizedId = normalizeIdentifier(trimmedIdentifier)
        if (normalizedId.length < MIN_IDENTIFIER_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Enter an email or username (at least $MIN_IDENTIFIER_LENGTH characters) for standalone sign-in.")
            )
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Standalone password must be at least $MIN_PASSWORD_LENGTH characters.")
            )
        }

        synchronized(vaultLock) {
            val existingRecords = readRecordsLocked(appCtx).toMutableList()
            val duplicate = existingRecords.any { record ->
                record.normalizedIdentifier == normalizedId ||
                    (draft.stageName.isNotBlank() &&
                        normalizeIdentifier(record.stageName) == normalizedId)
            }
            if (duplicate) {
                return Result.failure(
                    IllegalStateException("An account for '$trimmedIdentifier' is already registered. Switch to Sign In to enter with your password.")
                )
            }

            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val derivedHash = derivePasswordHash(password.toCharArray(), salt)

            val fallbackHandle = if (trimmedIdentifier.contains("@")) {
                trimmedIdentifier.substringBefore("@").ifBlank {
                    if (draft.role == UserRole.CREATOR) "Stage Performer" else "Stage Spectator"
                }
            } else {
                trimmedIdentifier.removePrefix("@").ifBlank {
                    if (draft.role == UserRole.CREATOR) "Stage Performer" else "Stage Spectator"
                }
            }

            val cleanStageName = draft.stageName.trim().ifBlank { fallbackHandle }
            val cleanStageTitle = draft.stageTitle.trim().ifBlank {
                if (draft.role == UserRole.CREATOR) {
                    "${draft.primaryCategory.displayName} Performer"
                } else {
                    "Stage Spectator"
                }
            }
            val cleanBio = draft.bio.trim().ifBlank {
                if (draft.role == UserRole.CREATOR) {
                    "Original ${draft.primaryCategory.displayName.lowercase()} creator performing on VersePulse."
                } else {
                    "Live music & spoken word spectator voting in VersePulse Arena."
                }
            }

            val now = System.currentTimeMillis()
            val roleSlug = if (draft.role == UserRole.CREATOR) "creator" else "spectator"
            val uidDigest = sha256ShortHex("$roleSlug:$normalizedId:$now")
            val generatedUid = "standalone_${roleSlug}_$uidDigest"

            val resolvedEmail = if (trimmedIdentifier.contains("@")) {
                trimmedIdentifier
            } else {
                "@${trimmedIdentifier.removePrefix("@")}"
            }

            val record = StoredStandaloneRecord(
                uid = generatedUid,
                normalizedIdentifier = normalizedId,
                rawIdentifier = trimmedIdentifier,
                email = resolvedEmail,
                displayName = cleanStageName,
                role = draft.role,
                stageName = cleanStageName,
                stageTitle = cleanStageTitle,
                bio = cleanBio,
                primaryCategory = draft.primaryCategory,
                passwordSaltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                passwordHashBase64 = Base64.encodeToString(derivedHash, Base64.NO_WRAP),
                createdAtMillis = now,
                updatedAtMillis = now
            )

            existingRecords.add(record)
            writeRecordsLocked(appCtx, existingRecords)
            if (activateSession) {
                UploadEncryptionManager.putEncryptedPref(appCtx, PREF_KEY_ACTIVE_UID, record.uid)
            }
            return Result.success(record.toUserAccount())
        }
    }

    /**
     * Verifies standalone Creator or Spectator credentials against the encrypted vault.
     * If [explicitRole] is provided, updates the session role to match the user's chosen arena mode;
     * otherwise restores the account's registered Creator or Spectator role.
     */
    fun signIn(
        context: Context,
        identifier: String,
        password: String,
        explicitRole: UserRole? = null
    ): Result<UserAccount> {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)

        val trimmedIdentifier = identifier.trim()
        val normalizedId = normalizeIdentifier(trimmedIdentifier)
        if (normalizedId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Enter your registered email or stage username to sign in.")
            )
        }
        if (password.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Enter your standalone account password to sign in.")
            )
        }

        synchronized(vaultLock) {
            val records = readRecordsLocked(appCtx).toMutableList()
            val index = records.indexOfFirst { record ->
                record.normalizedIdentifier == normalizedId ||
                    normalizeIdentifier(record.stageName) == normalizedId
            }
            if (index < 0) {
                return Result.failure(
                    IllegalArgumentException("No standalone account found for '$trimmedIdentifier'. Register a Creator or Spectator account first.")
                )
            }

            val matched = records[index]
            val salt = runCatching {
                Base64.decode(matched.passwordSaltBase64, Base64.NO_WRAP)
            }.getOrNull()
            val expectedHash = runCatching {
                Base64.decode(matched.passwordHashBase64, Base64.NO_WRAP)
            }.getOrNull()

            if (salt == null || expectedHash == null) {
                return Result.failure(
                    IllegalStateException("Stored standalone credential vault record could not be verified.")
                )
            }

            val candidateHash = derivePasswordHash(password.toCharArray(), salt)
            if (!MessageDigest.isEqual(expectedHash, candidateHash)) {
                return Result.failure(
                    IllegalArgumentException("Incorrect password for '$trimmedIdentifier'. Please try again.")
                )
            }

            val resolvedRole = explicitRole ?: matched.role
            val resolvedTitle = when {
                resolvedRole == UserRole.CREATOR &&
                    (matched.stageTitle.isBlank() || matched.stageTitle == "Stage Spectator") ->
                    "${matched.primaryCategory.displayName} Performer"
                resolvedRole == UserRole.SPECTATOR && matched.stageTitle.isBlank() ->
                    "Stage Spectator"
                else -> matched.stageTitle
            }

            val updatedRecord = matched.copy(
                role = resolvedRole,
                stageTitle = resolvedTitle,
                updatedAtMillis = System.currentTimeMillis()
            )
            records[index] = updatedRecord
            writeRecordsLocked(appCtx, records)
            UploadEncryptionManager.putEncryptedPref(appCtx, PREF_KEY_ACTIVE_UID, updatedRecord.uid)
            return Result.success(updatedRecord.toUserAccount())
        }
    }

    fun getActiveSessionAccount(context: Context): UserAccount? {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)
        synchronized(vaultLock) {
            val activeUid = UploadEncryptionManager.getEncryptedPref(appCtx, PREF_KEY_ACTIVE_UID)
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return readRecordsLocked(appCtx)
                .firstOrNull { it.uid == activeUid }
                ?.toUserAccount()
        }
    }

    fun updateAccount(context: Context, updated: UserAccount): Result<UserAccount> {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)
        synchronized(vaultLock) {
            val records = readRecordsLocked(appCtx).toMutableList()
            val idx = records.indexOfFirst { it.uid == updated.uid }
            if (idx < 0) {
                return Result.failure(IllegalArgumentException("Standalone account not found"))
            }
            val current = records[idx]
            val merged = current.copy(
                role = updated.role,
                displayName = updated.displayName.ifBlank { current.displayName },
                stageName = updated.stageName.ifBlank { current.stageName },
                stageTitle = updated.stageTitle.ifBlank { current.stageTitle },
                bio = updated.bio,
                primaryCategory = updated.primaryCategory,
                updatedAtMillis = System.currentTimeMillis()
            )
            records[idx] = merged
            writeRecordsLocked(appCtx, records)
            return Result.success(merged.toUserAccount())
        }
    }

    fun listRegisteredSummaries(context: Context): List<StandaloneAccountSummary> {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)
        synchronized(vaultLock) {
            return readRecordsLocked(appCtx)
                .sortedByDescending { it.updatedAtMillis }
                .map { it.toSummary() }
        }
    }

    fun clearActiveSession(context: Context) {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)
        synchronized(vaultLock) {
            UploadEncryptionManager.putEncryptedPref(appCtx, PREF_KEY_ACTIVE_UID, "")
        }
    }

    fun deleteAccount(context: Context, uid: String): Result<Unit> {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)

        val trimmedUid = uid.trim()
        if (trimmedUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot delete account without a valid user ID."))
        }

        synchronized(vaultLock) {
            val existingRecords = readRecordsLocked(appCtx)
            val remaining = existingRecords.filterNot { it.uid == trimmedUid }
            if (remaining.size == existingRecords.size) {
                return Result.failure(IllegalArgumentException("Standalone account not found for deletion."))
            }
            writeRecordsLocked(appCtx, remaining)
            val activeUid = UploadEncryptionManager.getEncryptedPref(appCtx, PREF_KEY_ACTIVE_UID)
            if (activeUid == trimmedUid) {
                UploadEncryptionManager.putEncryptedPref(appCtx, PREF_KEY_ACTIVE_UID, "")
            }
            return Result.success(Unit)
        }
    }

    fun clearAllAccounts(context: Context) {
        val appCtx = context.applicationContext ?: context
        UploadEncryptionManager.bindContext(appCtx)
        synchronized(vaultLock) {
            UploadEncryptionManager.putEncryptedPref(appCtx, PREF_KEY_ACTIVE_UID, "")
            UploadEncryptionManager.putEncryptedPref(appCtx, PREF_KEY_ACCOUNTS_VAULT, "[]")
        }
    }

    private fun normalizeIdentifier(raw: String): String =
        raw.trim().removePrefix("@").lowercase(Locale.US)

    private fun derivePasswordHash(passwordChars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            passwordChars.fill('\u0000')
        }
    }

    private fun sha256ShortHex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun readRecordsLocked(context: Context): List<StoredStandaloneRecord> {
        val rawJson = UploadEncryptionManager.getEncryptedPref(context, PREF_KEY_ACCOUNTS_VAULT)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uid = obj.optString("uid")
                    val normalizedIdentifier = obj.optString("normalizedIdentifier")
                    val saltB64 = obj.optString("passwordSaltBase64")
                    val hashB64 = obj.optString("passwordHashBase64")
                    if (uid.isBlank() || normalizedIdentifier.isBlank() || saltB64.isBlank() || hashB64.isBlank()) {
                        continue
                    }
                    val role = runCatching {
                        UserRole.valueOf(obj.optString("role", UserRole.SPECTATOR.name))
                    }.getOrDefault(UserRole.SPECTATOR)
                    val category = runCatching {
                        PerformanceCategory.valueOf(obj.optString("primaryCategory", PerformanceCategory.MUSIC.name))
                    }.getOrDefault(PerformanceCategory.MUSIC)

                    add(
                        StoredStandaloneRecord(
                            uid = uid,
                            normalizedIdentifier = normalizedIdentifier,
                            rawIdentifier = obj.optString("rawIdentifier", normalizedIdentifier),
                            email = obj.optString("email").takeIf { it.isNotBlank() },
                            displayName = obj.optString("displayName", "Stage Member"),
                            role = role,
                            stageName = obj.optString("stageName", ""),
                            stageTitle = obj.optString("stageTitle", ""),
                            bio = obj.optString("bio", ""),
                            primaryCategory = category,
                            passwordSaltBase64 = saltB64,
                            passwordHashBase64 = hashB64,
                            createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                            updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRecordsLocked(context: Context, records: List<StoredStandaloneRecord>) {
        val arr = JSONArray()
        records.forEach { rec ->
            val obj = JSONObject().apply {
                put("uid", rec.uid)
                put("normalizedIdentifier", rec.normalizedIdentifier)
                put("rawIdentifier", rec.rawIdentifier)
                put("email", rec.email ?: "")
                put("displayName", rec.displayName)
                put("role", rec.role.name)
                put("stageName", rec.stageName)
                put("stageTitle", rec.stageTitle)
                put("bio", rec.bio)
                put("primaryCategory", rec.primaryCategory.name)
                put("passwordSaltBase64", rec.passwordSaltBase64)
                put("passwordHashBase64", rec.passwordHashBase64)
                put("createdAtMillis", rec.createdAtMillis)
                put("updatedAtMillis", rec.updatedAtMillis)
            }
            arr.put(obj)
        }
        UploadEncryptionManager.putEncryptedPref(context, PREF_KEY_ACCOUNTS_VAULT, arr.toString())
    }
}
