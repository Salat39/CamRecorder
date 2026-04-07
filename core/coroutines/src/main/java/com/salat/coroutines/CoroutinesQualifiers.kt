package com.salat.coroutines

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppCoroutineScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoCoroutineScope
