@file:Suppress("SameParameterValue")

package com.salat.carapi.data.repository

import android.content.Context
import com.ecarx.xui.adaptapi.FunctionStatus
import com.ecarx.xui.adaptapi.binder.IConnectable
import com.ecarx.xui.adaptapi.car.Car
import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.base.ICarFunction
import com.ecarx.xui.adaptapi.car.sensor.ISensor
import com.salat.carapi.data.entity.CarPropertyKey
import com.salat.carapi.data.entity.CarPropertyValue
import com.salat.carapi.data.entity.IdType
import com.salat.carapi.domain.repository.CarApiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class CarApiRepositoryImpl(
    private val context: Context,
    scope: CoroutineScope
) : CarApiRepository {
    private val _ignitionStateFlow = MutableStateFlow(false)
    override val ignitionStateFlow = _ignitionStateFlow.asStateFlow()

    private val sensorListenList = setOf(CarPropertyKey.SENSOR_TYPE_IGNITION_STATE)

    // Car watcher
    private var mICar: ICar? = null

    init {
        scope.launch {
            initCarWatcher()
            initStartupValues()
        }
    }

    private fun initCarWatcher() = runCatching {
        val sensorListener = object : ISensor.ISensorListener {
            override fun onSensorEventChanged(id: Int, value: Int) {
                // Ignition state flow
                if (id == CarPropertyKey.SENSOR_TYPE_IGNITION_STATE) {
                    _ignitionStateFlow.update { value.inDrivingIgnition }
                }
            }

            override fun onSensorValueChanged(id: Int, value: Float) {
                // No implementation needed here
            }

            override fun onSensorSupportChanged(i: Int, functionStatus: FunctionStatus?) {
                // No implementation needed here
            }
        }

        val functionListener = object : ICarFunction.IFunctionValueWatcher {
            override fun onCustomizeFunctionValueChanged(id: Int, area: Int, value: Float) {
                // No implementation needed here
            }

            override fun onFunctionChanged(id: Int) {
                // No implementation needed here
            }

            override fun onFunctionValueChanged(id: Int, area: Int, value: Int) {
                // No implementation needed here
            }

            override fun onSupportedFunctionStatusChanged(id: Int, area: Int, functionStatus: FunctionStatus?) {
                // No implementation needed here
            }

            override fun onSupportedFunctionValueChanged(id: Int, params: IntArray?) {
                // No implementation needed here
            }
        }

        mICar = Car.create(context)
        (if (mICar is IConnectable) mICar as IConnectable else null)
            ?.registerConnectWatcher(object : IConnectable.IConnectWatcher {
                @Throws(IllegalAccessException::class, IllegalArgumentException::class)
                override fun onConnected() {
                }

                override fun onDisConnected() {
                }
            })

        fun Int.setSensorListener() = runCatching {
            mICar?.sensorManager?.registerListener(sensorListener, this)
        }

        // startup sensor subscribing
        sensorListenList.forEach { sensorId -> sensorId.setSensorListener() }

        mICar?.iCarFunction?.registerFunctionValueWatcher(functionListener)
    }.onFailure { Timber.e(it) }

    private fun initStartupValues() {
        _ignitionStateFlow.update {
            getIntPropertyWithType(
                propertyId = CarPropertyKey.SENSOR_TYPE_IGNITION_STATE,
                type = IdType.ID_TYPE_SENSOR
            ).inDrivingIgnition
        }
    }

    private val Int.inDrivingIgnition
        get() = CarPropertyValue.IGNITION_STATE_DRIVING == this

    private fun getIntPropertyWithType(propertyId: Int, type: Int) = when (type) {
        IdType.ID_TYPE_FUNCTION ->
            mICar?.iCarFunction?.getFunctionValue(propertyId, Integer.MIN_VALUE) ?: 0

        IdType.ID_TYPE_SENSOR -> mICar?.sensorManager?.getSensorEvent(propertyId) ?: 0

        IdType.ID_TYPE_INFO -> mICar?.carInfoManager?.getCarInfoInt(propertyId) ?: 0

        else -> 0
    }
}
