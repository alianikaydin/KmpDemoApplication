package com.anksoft.myapplication

import org.koin.core.module.Module

/** Bindings that exist only in this flavor. Prod has none: no test crash trigger is bound here. */
val flavorModules: List<Module> = emptyList()
