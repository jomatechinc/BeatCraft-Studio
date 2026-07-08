package com.example.data.repository

import com.example.data.database.ProjectDao
import com.example.data.database.ProjectEntity
import com.example.data.database.RecordingDao
import com.example.data.database.RecordingEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

class BeatRepository(
    private val projectDao: ProjectDao,
    private val recordingDao: RecordingDao
) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
    val allRecordings: Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()

    suspend fun getProjectById(id: Long): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun saveProject(project: ProjectEntity): Long {
        return projectDao.insertProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(id: Long) = projectDao.deleteProjectById(id)

    suspend fun saveRecording(recording: RecordingEntity): Long {
        return recordingDao.insertRecording(recording)
    }

    suspend fun deleteRecording(id: Long) = recordingDao.deleteRecordingById(id)

    // Simulates cloud sync with a delay, making a real database update
    suspend fun syncProjectToCloud(id: Long): Boolean {
        val project = projectDao.getProjectById(id) ?: return false
        delay(1500) // Simulate network latency
        val updatedProject = project.copy(
            isCloudSynced = true,
            isOfflineDraft = false,
            updatedAt = System.currentTimeMillis()
        )
        projectDao.updateProject(updatedProject)
        return true
    }
}
