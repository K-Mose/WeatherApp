package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

// deprecated 참고
// https://stackoverflow.com/questions/53532406/activenetworkinfo-type-is-deprecated-in-api-level-28
object Constants {

    const val APP_ID: String = "b928209d594fe893d9682c21ccece141"
    const val BASE_URL: String = "https://api.openweathermap.org/data/"
    // https로 연결, not permitted by network security policy
    // 또는 https://stackoverflow.com/questions/45940861/android-8-cleartext-http-traffic-not-permitted
    const val METRIC_UNIT: String = "metric"

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){ // SDK 23 이상 / api 28 이상
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }else{ // 23 미만
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }
}