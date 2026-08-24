package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.dao.ModelHistoryDao
import com.example.data.dao.UserPreferencesDao
import com.example.data.db.AppDatabase
import com.example.data.entity.ModelHistoryEntity
import com.example.data.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var preferencesDao: UserPreferencesDao
    private lateinit var historyDao: ModelHistoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesDao = database.userPreferencesDao()
        historyDao = database.modelHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSaveAndRetrieveUserPreferences() = runBlocking {
        val prefs = UserPreferenceEntity(
            id = 1,
            lastMeshId = "drone",
            lastDisplayMode = "MODE_AR",
            renderStyle = "HOLOGRAM",
            environmentType = "CYBER_GRID",
            autoRotate = true,
            shadowsEnabled = true,
            shadowIntensity = 0.8f,
            sceneIntensity = 1.4f
        )
        preferencesDao.insertOrUpdate(prefs)

        val retrieved = preferencesDao.getPreferences()
        assertNotNull(retrieved)
        assertEquals("drone", retrieved?.lastMeshId)
        assertEquals("MODE_AR", retrieved?.lastDisplayMode)
        assertEquals("HOLOGRAM", retrieved?.renderStyle)
        assertEquals("CYBER_GRID", retrieved?.environmentType)
        assertEquals(true, retrieved?.autoRotate)
        assertEquals(true, retrieved?.shadowsEnabled)
        assertEquals(0.8f, retrieved?.shadowIntensity ?: 0f, 0.01f)
        assertEquals(1.4f, retrieved?.sceneIntensity ?: 0f, 0.01f)
    }

    @Test
    fun testInsertAndRetrieveRecentModelHistory() = runBlocking {
        val item1 = ModelHistoryEntity(
            meshId = "pavilion",
            meshName = "Architectural Pavilion",
            category = "Architecture",
            vertexCount = 100,
            faceCount = 80,
            displayMode = "MODE_3D",
            timestamp = 1000L
        )
        val item2 = ModelHistoryEntity(
            meshId = "rover",
            meshName = "Planetary Mars Rover",
            category = "Robotics",
            vertexCount = 250,
            faceCount = 200,
            displayMode = "MODE_AR",
            timestamp = 2000L
        )

        historyDao.insertHistory(item1)
        historyDao.insertHistory(item2)

        val historyList = historyDao.getRecentHistory().first()
        assertEquals(2, historyList.size)
        assertEquals("rover", historyList[0].meshId) // Latest timestamp first
        assertEquals("pavilion", historyList[1].meshId)

        historyDao.deleteById(historyList[0].id)
        val afterDelete = historyDao.getRecentHistory().first()
        assertEquals(1, afterDelete.size)
        assertEquals("pavilion", afterDelete[0].meshId)

        historyDao.clearAll()
        val afterClear = historyDao.getRecentHistory().first()
        assertEquals(0, afterClear.size)
    }
}
