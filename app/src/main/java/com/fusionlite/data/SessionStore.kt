package com.fusionlite.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.fusionlite.api.Session
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local SQLite store for the FusionSolar session and (encrypted) credentials.
 *
 * The access token / CSRF random / data-host are enough to restore the signed-in
 * experience. The password is only kept so an expired token can be renewed
 * silently instead of showing the login screen again; it is encrypted with an
 * Android Keystore AES/GCM key (the raw key never leaves the keystore).
 */
class SessionStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    data class Saved(
        val username: String?,
        val password: String?,
        val host: String,
        val port: String,
        val dataHost: String?,
        val dataPort: String?,
        val accessToken: String?,
        val roaRand: String?,
        val selectedDn: String?,
        val appClientId: String,
    ) {
        val hasSession: Boolean get() = !accessToken.isNullOrBlank() && !dataHost.isNullOrBlank()
        val canRelogin: Boolean get() = !username.isNullOrBlank() && !password.isNullOrBlank()

        fun toSession(): Session? {
            if (!hasSession) return null
            return Session(
                host = dataHost!!,
                port = dataPort ?: port,
                accessToken = accessToken!!,
                roaRand = roaRand.orEmpty(),
            )
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                username TEXT,
                password_enc TEXT,
                login_host TEXT NOT NULL,
                login_port TEXT NOT NULL,
                data_host TEXT,
                data_port TEXT,
                access_token TEXT,
                roa_rand TEXT,
                selected_dn TEXT,
                app_client_id TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Single-row cache: safe to recreate when the schema changes.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun load(): Saved? {
        readableDatabase.query(
            TABLE, null, "id = 1", null, null, null, null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            fun text(column: String): String? =
                cursor.getColumnIndex(column).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { cursor.getString(it) }
            val host = text("login_host") ?: return null
            val port = text("login_port") ?: "32800"
            return Saved(
                username = text("username"),
                password = SecretBox.decrypt(text("password_enc")),
                host = host,
                port = port,
                dataHost = text("data_host"),
                dataPort = text("data_port"),
                accessToken = text("access_token"),
                roaRand = text("roa_rand"),
                selectedDn = text("selected_dn"),
                appClientId = text("app_client_id") ?: newClientId(),
            )
        }
    }

    /** Stores a freshly authenticated session plus the credentials used to get it. */
    fun saveLogin(
        username: String,
        password: String,
        loginHost: String,
        loginPort: String,
        session: Session,
        appClientId: String,
        selectedDn: String? = load()?.selectedDn,
    ) = write(
        username = username,
        passwordEnc = SecretBox.encrypt(password),
        loginHost = loginHost,
        loginPort = loginPort,
        dataHost = session.host,
        dataPort = session.port,
        accessToken = session.accessToken,
        roaRand = session.roaRand,
        selectedDn = selectedDn,
        appClientId = appClientId,
    )

    fun saveSession(
        session: Session,
        appClientId: String,
        selectedDn: String? = load()?.selectedDn,
    ) {
        val current = load() ?: return
        write(
            username = current.username,
            passwordEnc = SecretBox.encrypt(current.password.orEmpty()),
            loginHost = current.host,
            loginPort = current.port,
            dataHost = session.host,
            dataPort = session.port,
            accessToken = session.accessToken,
            roaRand = session.roaRand,
            selectedDn = selectedDn,
            appClientId = appClientId,
        )
    }

    fun saveSelectedStation(dn: String?) {
        val current = load() ?: return
        writableDatabase.update(
            TABLE,
            ContentValues().apply { put("selected_dn", dn) },
            "id = 1",
            null,
        )
    }

    fun appClientId(): String =
        load()?.appClientId ?: newClientId().also {
            writableDatabase.insertWithOnConflict(
                TABLE,
                null,
                ContentValues().apply {
                    put("id", 1)
                    put("login_host", "intl.fusionsolar.huawei.com")
                    put("login_port", "32800")
                    put("app_client_id", it)
                    put("updated_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

    fun clear() {
        writableDatabase.delete(TABLE, null, null)
    }

    private fun write(
        username: String?,
        passwordEnc: String?,
        loginHost: String,
        loginPort: String,
        dataHost: String?,
        dataPort: String?,
        accessToken: String?,
        roaRand: String?,
        selectedDn: String?,
        appClientId: String,
    ) {
        writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            ContentValues().apply {
                put("id", 1)
                put("username", username)
                put("password_enc", passwordEnc)
                put("login_host", loginHost)
                put("login_port", loginPort)
                put("data_host", dataHost)
                put("data_port", dataPort)
                put("access_token", accessToken)
                put("roa_rand", roaRand)
                put("selected_dn", selectedDn)
                put("app_client_id", appClientId)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun newClientId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")

    private companion object {
        const val DB_NAME = "fusionlite.db"
        const val DB_VERSION = 1
        const val TABLE = "session"
    }
}

/** AES/GCM helper backed by a non-exportable Android Keystore key. */
private object SecretBox {
    private const val ALIAS = "fusionlite.session.key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String?): String? {
        if (plain.isNullOrEmpty()) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(payload, Base64.NO_WRAP)
        }.getOrNull()
    }

    fun decrypt(encoded: String?): String? {
        if (encoded.isNullOrEmpty()) return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, IV_BYTES)
            val body = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }
}
