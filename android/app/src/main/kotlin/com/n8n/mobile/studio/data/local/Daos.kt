package com.n8n.mobile.studio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceDao {
    @Query("SELECT * FROM instances ORDER BY name") fun observeAll(): Flow<List<InstanceEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(instance: InstanceEntity)
    @Query("DELETE FROM instances WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflows WHERE instanceId = :instanceId ORDER BY name") fun observe(instanceId: String): Flow<List<WorkflowEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<WorkflowEntity>)
    @Query("DELETE FROM workflows WHERE instanceId = :instanceId") suspend fun deleteForInstance(instanceId: String)
}

@Dao
interface ExecutionDao {
    @Query("SELECT * FROM executions WHERE instanceId = :instanceId ORDER BY startedAt DESC") fun observe(instanceId: String): Flow<List<ExecutionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<ExecutionEntity>)
}
