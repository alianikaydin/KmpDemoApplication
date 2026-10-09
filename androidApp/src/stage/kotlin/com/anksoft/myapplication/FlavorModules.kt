package com.anksoft.myapplication

import com.anksoft.myapplication.core.crash.TestCrashTrigger
import com.anksoft.myapplication.core.crash.ThrowingTestCrashTrigger
import org.koin.core.module.Module
import org.koin.dsl.module

/** Bindings that exist only in this flavor: the test crash action of Settings. */
val flavorModules: List<Module> = listOf(
    module { single<TestCrashTrigger> { ThrowingTestCrashTrigger } }
)
