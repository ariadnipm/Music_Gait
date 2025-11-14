
package com.example.accelerometer

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    @ServiceScoped
    fun provideServiceScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @ServiceScoped
    fun provideSlidingWindow(): SlidingWindow =
        SlidingWindow(windowlength = 30_000L, step = 15_000L, minEmit = 15_000L, maxHz = 100.0)

    @Provides
    @ServiceScoped
    fun provideMeasurableSensor(@ApplicationContext ctx: Context): MeasurableSensor =
        AccelerometerSensor(ctx) //
}
