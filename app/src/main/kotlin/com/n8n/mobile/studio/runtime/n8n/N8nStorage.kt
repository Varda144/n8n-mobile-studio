package com.n8n.mobile.studio.runtime.n8n
import android.content.Context
import com.n8n.mobile.studio.runtime.RuntimePaths
import java.io.File
class N8nStorage(ctx:Context){ private val p=RuntimePaths(ctx); val dataDir:File get()=p.n8nData; fun ensure(){ dataDir.mkdirs(); File(dataDir,".n8n").mkdirs() } }
