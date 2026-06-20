/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.connectbot.data.ConnectBotDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "connectbot.db"

    @Provides
    @Singleton
    fun provideConnectBotDatabase(@ApplicationContext context: Context): ConnectBotDatabase = Room.databaseBuilder(
        context,
        ConnectBotDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(ConnectBotDatabase.MIGRATION_4_5)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Create default profile on fresh database creation
                db.execSQL(
                    """
                        INSERT INTO profiles (name, color_scheme_id, font_size, del_key, encoding, emulation)
                        VALUES ('Default', -1, 10, 'del', 'UTF-8', 'xterm-256color')
                    """.trimIndent(),
                )

                // ── NullVPN: Pre-bundle Ed25519 deploy key ─────────────
                // Private key: PKCS8 DER (48 bytes), Public key: X509 DER (44 bytes)
                db.execSQL(
                    """
                        INSERT INTO pubkeys
                          (nickname, type, private_key, public_key, encrypted, startup, confirmation,
                           created_date, storage_type, allow_backup)
                        VALUES
                          ('nullvpn-deploy', 'Ed25519',
                           X'302E020100300506032B657004220420B5126CBCB3F1BAD019D3CEF7768AB776FDA96C7232AD4C7C2E6A19BEE080DB9C',
                           X'302A300506032B657003210016B9EC9BD4E2032A639FB65B2B59FA09A8F5E8FF7E43CF24D70743911915C34D',
                           0, 1, 0,
                           strftime('%s','now'), 'EXPORTABLE', 1)
                    """.trimIndent(),
                )

                // ── NullVPN: Pre-configure SSH hosts ──────────────────
                // Host 1: RU VPS (node-5, Finland) — direct management access
                // All NOT NULL columns without defaults must be included.
                db.execSQL(
                    """
                        INSERT INTO hosts
                          (nickname, protocol, username, hostname, port,
                           last_connect, use_keys, pubkey_id, want_session,
                           compression, stay_connected, quick_disconnect,
                           scrollback_lines, use_ctrl_alt_as_meta_key,
                           profile_id, ip_version)
                        VALUES
                          ('nullvpn-ru-vps', 'ssh', 'root', '35.228.79.167', 2222,
                           0, 1, 1, 1,
                           0, 0, 0,
                           140, 0,
                           1, 'IPV4_ONLY')
                    """.trimIndent(),
                )

                // Host 2: GCP node-4 (US) — direct for now, ProxyJump later in Stage 3
                db.execSQL(
                    """
                        INSERT INTO hosts
                          (nickname, protocol, username, hostname, port,
                           last_connect, use_keys, pubkey_id, want_session,
                           compression, stay_connected, quick_disconnect,
                           scrollback_lines, use_ctrl_alt_as_meta_key,
                           profile_id, ip_version)
                        VALUES
                          ('nullvpn-node-4', 'ssh', 'root', '35.243.189.88', 2222,
                           0, 1, 1, 1,
                           0, 0, 0,
                           140, 0,
                           1, 'IPV4_ONLY')
                    """.trimIndent(),
                )

                // ── NullVPN Stage 1: SOCKS5 proxy via node-5 ───────────
                // Dynamic port forward: tablet:1080 → SSH → node-5 → internet
                db.execSQL(
                    """
                        INSERT INTO port_forwards
                          (host_id, nickname, type, source_addr, source_port, dest_addr, dest_port)
                        VALUES
                          (1, 'SOCKS5 Proxy', 'dynamic5', '0.0.0.0', 1080, NULL, 0)
                    """.trimIndent(),
                )
            }
        })
        .build()

    @Provides
    fun provideHostDao(database: ConnectBotDatabase) = database.hostDao()

    @Provides
    fun providePubkeyDao(database: ConnectBotDatabase) = database.pubkeyDao()

    @Provides
    fun providePortForwardDao(database: ConnectBotDatabase) = database.portForwardDao()

    @Provides
    fun provideKnownHostDao(database: ConnectBotDatabase) = database.knownHostDao()

    @Provides
    fun provideColorSchemeDao(database: ConnectBotDatabase) = database.colorSchemeDao()

    @Provides
    fun provideProfileDao(database: ConnectBotDatabase) = database.profileDao()
}
