package com.qingxi.ledger

import android.app.Application
import com.qingxi.ledger.data.db.AppDatabase

class QingJiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppDatabase.init(this)
    }
}
