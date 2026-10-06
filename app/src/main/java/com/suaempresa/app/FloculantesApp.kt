package com.suaempresa.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

class FloculantesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Sem google-services.json o Firebase não se configura sozinho: modo demonstração,
        // com os dados guardados só neste aparelho (cache local do Firestore, sem rede).
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
                .setProjectId("floculantes-demo")
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo")
                .build())
            FirebaseFirestore.getInstance().disableNetwork()
        }
    }
}
