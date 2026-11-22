package gui.pgcr.storyteller

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Substituto vazio: o AndroidManifest declara o PushService, mas sem Firebase ele nunca é acionado.
 * A versão real (com Firebase) está em src/firebase.
 */
class PushService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
