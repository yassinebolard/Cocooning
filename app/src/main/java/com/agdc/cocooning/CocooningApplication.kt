package com.agdc.cocooning

import android.app.Application
import com.agdc.cocooning.di.ConteneurApp

class CocooningApplication : Application() {
    val conteneur: ConteneurApp by lazy { ConteneurApp() }
}
