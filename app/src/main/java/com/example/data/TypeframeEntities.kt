package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val canvasWidthPx: Int,
    val canvasHeightPx: Int,
    val layerCount: Int,
    val thumbnailPath: String?,
    val documentJson: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "imported_fonts")
data class ImportedFontEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val fileName: String,
    val filePath: String,
    val importedAtEpochMs: Long
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtEpochMs DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface FontDao {
    @Query("SELECT * FROM imported_fonts ORDER BY importedAtEpochMs DESC")
    fun observeImportedFonts(): Flow<List<ImportedFontEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFont(font: ImportedFontEntity)

    @Query("DELETE FROM imported_fonts WHERE id = :id")
    suspend fun deleteFontById(id: String)
}

@Database(
    entities = [ProjectEntity::class, ImportedFontEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TypeframeDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun fontDao(): FontDao

    companion object {
        @Volatile
        private var INSTANCE: TypeframeDatabase? = null

        fun getInstance(context: Context): TypeframeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TypeframeDatabase::class.java,
                    "typeframe_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
