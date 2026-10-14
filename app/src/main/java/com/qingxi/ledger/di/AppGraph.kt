package com.qingxi.ledger.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingxi.ledger.data.category.CategoryRepository
import com.qingxi.ledger.data.db.AppDatabase
import com.qingxi.ledger.data.recurring.RecurringPendingStore
import com.qingxi.ledger.data.recurring.RecurringRepository
import com.qingxi.ledger.data.settings.SettingsRepository
import com.qingxi.ledger.data.tx.TransactionRepository
import com.qingxi.ledger.data.update.UpdateController
import com.qingxi.ledger.data.update.UpdateRepository
import java.io.File

class AppGraph private constructor(private val context: Context) {

    /** 小组件刷新等场景需要 Application Context。 */
    val appContext: Context get() = context

    private val database: AppDatabase get() = AppDatabase.instance

    val transactions: TransactionRepository by lazy { TransactionRepository(database.transactionDao()) }

    val recurrences: RecurringRepository by lazy {
        RecurringRepository(database.recurringRuleDao(), database.recurringDismissalDao())
    }

    /** 冷启动算出来的到期项，进程内有效，确认后才会变成流水。 */
    val pending: RecurringPendingStore by lazy { RecurringPendingStore(this) }

    val categories: CategoryRepository by lazy {
        CategoryRepository(database.categoryDao(), database.transactionDao(), database.recurringRuleDao())
    }

    val settings: SettingsRepository by lazy { SettingsRepository(context) }

    val backups: com.qingxi.ledger.data.backup.BackupRepository by lazy {
        com.qingxi.ledger.data.backup.BackupRepository(this)
    }

    val updates: UpdateRepository by lazy { UpdateRepository(this) }

    val updateController: UpdateController by lazy { UpdateController(this) }

    val attachmentDir: File by lazy {
        File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }
    }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}

val LocalAppGraph = compositionLocalOf<AppGraph> { error("AppGraph 未提供") }

@Composable
fun ProvideAppGraph(content: @Composable () -> Unit) {
    val graph = AppGraph.get(LocalContext.current)
    androidx.compose.runtime.CompositionLocalProvider(LocalAppGraph provides graph, content = content)
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null): VM {
    val graph = LocalAppGraph.current
    val factory = remember(graph) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val constructor = modelClass.constructors.first {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == AppGraph::class.java
                }
                @Suppress("UNCHECKED_CAST")
                return constructor.newInstance(graph) as T
            }
        }
    }
    return viewModel(key = key, factory = factory)
}
