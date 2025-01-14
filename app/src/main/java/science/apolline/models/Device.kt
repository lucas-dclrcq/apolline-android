package science.apolline.models

/**
 * Created by sparow on 10/20/17.
 */

import android.arch.persistence.room.*

import com.google.gson.JsonObject
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import science.apolline.utils.CheckUtility
import science.apolline.utils.GeoHashHelper
import science.apolline.utils.GeoHashHelper.decode


@Entity
data class Device(
        @SerializedName("uuid")
        @Expose
        var uuid: String,
        @SerializedName("device")
        @Expose
        var device: String,
        @SerializedName("date")
        @Expose
        var date: Long,
        @SerializedName("position")
        @Expose
        @Embedded
        var position: Position?,
        @SerializedName("data")
        @Expose
        var data: JsonObject?,
        /**
         *  0 if not sync, 1 if sync
         */
        @SerializedName("isSync")
        @Expose
        var isSync: Int
) {
    @SerializedName("device_id")
    @Expose
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    private var longitude: Double = -1.0
        get() {
            if (position?.geohash != "no") {
                return  GeoHashHelper.decode(position!!.geohash).longitude
            }
            return -1.0
        }


    @Ignore
    private var latitude: Double = -1.0
        get() {
            if (position?.geohash != "no") {
                return  GeoHashHelper.decode(position!!.geohash).latitude
            }
            return -1.0
        }


    @Ignore
    constructor() : this("", "", 0, null, null, 0)


    override fun toString(): String {
        return """
        |AndroidId = $uuid
        |Device = $device
        |Date = $date
        |Position = $position
        |Data = $data
        """.trimMargin()
    }

    fun toArray(): Array<String> {
        val objectArray = mutableListOf<String>()
        objectArray.add(uuid)
        objectArray.add(device)
        objectArray.add(CheckUtility.dateParser(date))
//        objectArray.add(position?.geohash.orEmpty())
        objectArray.add(longitude.toString())
        objectArray.add(latitude.toString())
        objectArray.add(position?.provider.orEmpty())
        objectArray.add(position?.transport.orEmpty())

        data!!.entrySet().iterator().forEach {
            val value = it.value.asJsonArray[0].toString()
            objectArray.add(value)
        }

        return objectArray.toTypedArray()
    }
}