// Copyright 2019 Zeppelin Bend Pty Ltd
// This file is part of cimcap-meas.
//
// cimcap-meas is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// cimcap-meas is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with cimcap-meas.  If not, see <https://www.gnu.org/licenses/>.


package com.zepben.cimcap.meas

import com.mchange.v2.c3p0.PooledDataSource
import com.zepben.cimbend.cim.iec61970.base.meas.AccumulatorValue
import com.zepben.cimbend.cim.iec61970.base.meas.AnalogValue
import com.zepben.cimbend.cim.iec61970.base.meas.DiscreteValue
import com.zepben.cimbend.measurement.MeasurementProtoToCim
import com.zepben.cimbend.measurement.MeasurementService
import com.zepben.cimbend.measurement.toCim
import com.zepben.protobuf.mp.*
import io.grpc.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

class MeasurementProducerServer(private val connectionPool: PooledDataSource) : MeasurementProducerGrpcKt.MeasurementProducerCoroutineImplBase() {

    var measurementService = MeasurementService()
        private set
    private var measToCim = MeasurementProtoToCim(measurementService)
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val LIMIT: Int = 5000000

    @ExperimentalUnsignedTypes
    override suspend fun createAccumulatorValue(request: CreateAccumulatorValueRequest): CreateAccumulatorValueResponse {
        try {
            logger.info(
                "Received AccumulatorValue: mRID=${request.accumulatorValue.accumulatorMRID}, value=${request.accumulatorValue.value}, " +
                    "timestamp=${request.accumulatorValue.mv.timeStamp}"
            );
            if (measurementService.num() >= LIMIT) {
                val toRemove = measurementService.listOf(AccumulatorValue::class).first()
                measurementService.remove(toRemove);
            }
            val av = toCim(request.accumulatorValue)
            measurementService.add(av)

            connectionPool.connection.use {
                val preparedAccumulator =
                    it.prepareStatement("INSERT INTO accumulator_values(timestamp, write_time, accumulator_mrid, value) VALUES (?, ?, ?, ?)")
                preparedAccumulator.setTimestamp(1, Timestamp.from(av.timeStamp))
                preparedAccumulator.setTimestamp(2, Timestamp.from(Instant.now()))
                preparedAccumulator.setString(3, av.accumulatorMRID)
                preparedAccumulator.setInt(4, av.value.toInt())
                assert(preparedAccumulator.executeUpdate() == 1)
                preparedAccumulator.clearParameters()
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
            throw Status.fromCode(Status.Code.UNKNOWN).withDescription(e.message).asException()
        }
        return CreateAccumulatorValueResponse.getDefaultInstance()
    }

    override suspend fun createAnalogValue(request: CreateAnalogValueRequest): CreateAnalogValueResponse {
        try {
            logger.info(
                "Received AnalogValue: mRID=${request.analogValue.analogMRID}, value=${request.analogValue.value}, " +
                    "timestamp=${request.analogValue.mv.timeStamp}"
            );
            if (measurementService.num() >= LIMIT) {
                val toRemove = measurementService.listOf(AnalogValue::class).first()
                measurementService.remove(toRemove);
            }
            val av = toCim(request.analogValue)
            measurementService.add(av)
            connectionPool.connection.use {
                val preparedAnalog = it.prepareStatement("INSERT INTO analog_values(timestamp, write_time, analog_mrid, value) VALUES (?, ?, ?, ?)")
                preparedAnalog.setTimestamp(1, Timestamp.from(av.timeStamp))
                preparedAnalog.setTimestamp(2, Timestamp.from(Instant.now()))
                preparedAnalog.setString(3, av.analogMRID)
                preparedAnalog.setDouble(4, av.value)
                assert(preparedAnalog.executeUpdate() == 1)
                preparedAnalog.clearParameters()
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
            throw Status.fromCode(Status.Code.UNKNOWN).withDescription(e.message).asException()
        }
        return CreateAnalogValueResponse.getDefaultInstance()
    }

    override suspend fun createDiscreteValue(request: CreateDiscreteValueRequest): CreateDiscreteValueResponse {
        try {
            logger.info(
                "Received DiscreteValue: mRID=${request.discreteValue.discreteMRID}, value=${request.discreteValue.value}, " +
                    "timestamp=${request.discreteValue.mv.timeStamp}"
            );
            if (measurementService.num() >= LIMIT) {
                val toRemove = measurementService.listOf(DiscreteValue::class).first()
                measurementService.remove(toRemove);
            }
            val dv = toCim(request.discreteValue)
            measurementService.add(dv)

            connectionPool.connection.use {
                val preparedDiscrete = it.prepareStatement("INSERT INTO discrete_values(timestamp, write_time, discrete_mrid, value) VALUES (?, ?, ?, ?)")
                preparedDiscrete.setTimestamp(1, Timestamp.from(dv.timeStamp))
                preparedDiscrete.setTimestamp(2, Timestamp.from(Instant.now()))
                preparedDiscrete.setString(3, dv.discreteMRID)
                preparedDiscrete.setInt(4, dv.value)
                assert(preparedDiscrete.executeUpdate() == 1)
                preparedDiscrete.clearParameters()
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
            throw Status.fromCode(Status.Code.UNKNOWN).withDescription(e.message).asException()
        }
        return CreateDiscreteValueResponse.getDefaultInstance()
    }

    fun close() {
        connectionPool.close()
    }
}
