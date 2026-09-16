package com.n8n.mobile.studio.data.local
import androidx.room.*
@Dao interface WorkflowDao{ @Query("SELECT * FROM workflows") suspend fun all():List<WorkflowEntity>; @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insert(e:WorkflowEntity); @Query("DELETE FROM workflows WHERE id=:id") suspend fun delete(id:String)}
