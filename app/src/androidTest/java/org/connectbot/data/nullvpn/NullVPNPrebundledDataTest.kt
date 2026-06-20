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

package org.connectbot.data.nullvpn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.KeyStorageType
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for the NullVPN pre-bundled data in DatabaseModule.
 *
 * These tests verify that the Room database onCreate callback correctly
 * seeds the Ed25519 deploy key, SSH hosts, and SOCKS5 port forward.
 * Uses Room.databaseBuilder with fallbackToDestructiveMigration to ensure
 * onCreate fires on every test run against the actual Room-generated schema.
 */
@RunWith(AndroidJUnit4::class)
class NullVPNPrebundledDataTest {

    private lateinit var database: ConnectBotDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.databaseBuilder(
            context,
            ConnectBotDatabase::class.java,
            "test_connectbot.db",
        )
            .addMigrations(ConnectBotDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
        // Touch the database to trigger onCreate
        database.hostDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Helper: get a host by nickname from the database. */
    private suspend fun hostByNickname(name: String) =
        database.hostDao().getAll().find { it.nickname == name }

    // ── Pubkey tests ──────────────────────────────────────────────────

    @Test
    fun nullvpnDeployKey_isCreated_onFirstLaunch() = runTest {
        val key = database.pubkeyDao().getByNickname("nullvpn-deploy")

        assertThat(key).isNotNull()
        assertThat(key!!.type).isEqualTo("Ed25519")
        assertThat(key.encrypted).isFalse()
        assertThat(key.startup).isTrue()
        assertThat(key.confirmation).isFalse()
        assertThat(key.storageType).isEqualTo(KeyStorageType.EXPORTABLE)
        assertThat(key.allowBackup).isTrue()
        assertThat(key.keystoreAlias).isNull()
    }

    @Test
    fun nullvpnDeployKey_hasCorrectKeySizes() = runTest {
        val key = database.pubkeyDao().getByNickname("nullvpn-deploy")!!

        assertThat(key.privateKey).isNotNull()
        assertThat(key.privateKey!!.size).isEqualTo(48)
        assertThat(key.publicKey.size).isEqualTo(44)
    }

    @Test
    fun nullvpnDeployKey_privateKeyIsPkcs8Der() = runTest {
        val key = database.pubkeyDao().getByNickname("nullvpn-deploy")!!

        // PKCS8 DER Ed25519: 30 2E 02 01 00 30 05 06 03 2B 65 70 ...
        assertThat(key.privateKey).isNotNull()
        val pk = key.privateKey!!
        assertThat(pk[0]).isEqualTo(0x30.toByte())
        assertThat(pk[1]).isEqualTo(0x2E.toByte())
        assertThat(pk[2]).isEqualTo(0x02.toByte())
        // OID 1.3.101.112 (Ed25519) at offset 8-11
        assertThat(pk[8]).isEqualTo(0x2B.toByte())
        assertThat(pk[9]).isEqualTo(0x65.toByte())
        assertThat(pk[10]).isEqualTo(0x70.toByte())
    }

    // ── Host tests ───────────────────────────────────────────────────

    @Test
    fun hostNullvpnRuVps_hasCorrectConfig() = runTest {
        val host = hostByNickname("nullvpn-ru-vps")

        assertThat(host).isNotNull()
        assertThat(host!!.protocol).isEqualTo("ssh")
        assertThat(host.username).isEqualTo("root")
        assertThat(host.hostname).isEqualTo("35.228.79.167")
        assertThat(host.port).isEqualTo(2222)
        assertThat(host.useKeys).isTrue()
        assertThat(host.wantSession).isTrue()
        assertThat(host.stayConnected).isFalse()
        assertThat(host.compression).isFalse()
        assertThat(host.scrollbackLines).isEqualTo(140)
        assertThat(host.ipVersion).isEqualTo("IPV4_ONLY")
        assertThat(host.profileId).isEqualTo(1L)
    }

    @Test
    fun hostNullvpnNode4_hasCorrectConfig() = runTest {
        val host = hostByNickname("nullvpn-node-4")

        assertThat(host).isNotNull()
        assertThat(host!!.protocol).isEqualTo("ssh")
        assertThat(host.username).isEqualTo("root")
        assertThat(host.hostname).isEqualTo("35.243.189.88")
        assertThat(host.port).isEqualTo(2222)
        assertThat(host.ipVersion).isEqualTo("IPV4_ONLY")
    }

    @Test
    fun hosts_allRequiredNotNullColumns_populated() = runTest {
        val host = hostByNickname("nullvpn-ru-vps")!!

        // These three columns previously caused SQLite constraint violation crash
        assertThat(host.lastConnect).isEqualTo(0L)
        assertThat(host.quickDisconnect).isFalse()
        assertThat(host.useCtrlAltAsMetaKey).isFalse()
    }

    @Test
    fun hosts_pubkeyId_referencesNullvpnKey() = runTest {
        val key = database.pubkeyDao().getByNickname("nullvpn-deploy")!!
        val host1 = hostByNickname("nullvpn-ru-vps")!!
        val host2 = hostByNickname("nullvpn-node-4")!!

        assertThat(host1.pubkeyId).isEqualTo(key.id)
        assertThat(host2.pubkeyId).isEqualTo(key.id)
    }

    @Test
    fun exactlyTwoPrebundledHosts_exist() = runTest {
        val allHosts = database.hostDao().observeAll().first()

        assertThat(allHosts).hasSize(2)
        assertThat(allHosts.map { it.nickname })
            .containsExactlyInAnyOrder("nullvpn-ru-vps", "nullvpn-node-4")
    }

    // ── Port Forward tests ───────────────────────────────────────────

    @Test
    fun socks5Proxy_exists_onNode5Host() = runTest {
        val node5Host = hostByNickname("nullvpn-ru-vps")!!
        val forwards = database.portForwardDao().getByHost(node5Host.id)

        assertThat(forwards).hasSize(1)

        val proxy = forwards[0]
        assertThat(proxy.nickname).isEqualTo("SOCKS5 Proxy")
        assertThat(proxy.type).isEqualTo("dynamic5")
        assertThat(proxy.sourceAddr).isEqualTo("0.0.0.0")
        assertThat(proxy.sourcePort).isEqualTo(1080)
        assertThat(proxy.destAddr).isNull()
        assertThat(proxy.destPort).isEqualTo(0)
    }

    @Test
    fun socks5Proxy_fk_referencesNode5Host() = runTest {
        val node5Host = hostByNickname("nullvpn-ru-vps")!!
        val forwards = database.portForwardDao().getByHost(node5Host.id)

        assertThat(forwards).hasSize(1)
        assertThat(forwards[0].hostId).isEqualTo(node5Host.id)
    }

    @Test
    fun node4Host_hasNoPortForwards() = runTest {
        val node4Host = hostByNickname("nullvpn-node-4")!!
        val forwards = database.portForwardDao().getByHost(node4Host.id)

        assertThat(forwards).isEmpty()
    }
}
