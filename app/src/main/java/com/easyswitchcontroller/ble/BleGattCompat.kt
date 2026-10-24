package com.easyswitchcontroller.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build

/**
 * API 33(TIRAMISU)에서 writeCharacteristic/writeDescriptor 시그니처가 바뀌었다.
 * minSdk 31이므로 31·32(구식 setValue+write)와 33+(신규 3-arg, int 반환)를 분기한다.
 */

@SuppressLint("MissingPermission")
fun BluetoothGatt.writeCharacteristicCompat(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: Int,
): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
    } else {
        @Suppress("DEPRECATION")
        run {
            characteristic.writeType = writeType
            characteristic.value = value
            writeCharacteristic(characteristic)
        }
    }

@SuppressLint("MissingPermission")
fun BluetoothGatt.writeDescriptorCompat(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray,
): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        @Suppress("DEPRECATION")
        run {
            descriptor.value = value
            writeDescriptor(descriptor)
        }
    }

/** logcat 진단용 헥스 문자열. */
internal fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

/** write type 결정: characteristic이 WRITE_NO_RESPONSE를 지원하면 (명령 종류와 무관하게) 그것을 우선한다. */
fun BluetoothGattCharacteristic.preferredWriteType(): Int {
    val props = properties
    return when {
        props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }
}
