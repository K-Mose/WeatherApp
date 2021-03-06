package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var _binding: ActivityMainBinding
    private val binding: ActivityMainBinding
        get() = _binding!!

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private lateinit var customDialog: Dialog

    private lateinit var mSharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)

        /* SharedPreferences -> ??? ????????? ?????? ?????? shared_prefs ????????? ???????????? key, value ???????????? ???????????? ????????????.
           ???????????? ????????? ????????? ????????? ???????????? ?????? Editor????????? ????????????, ????????? ?????? immutable??? ????????????.
        */
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE) // ????????? ???????????? ?????? ???????????? ???????????? ?????? ????????? ?????? ????????? ??????

        if(!isLocationEnable()) {
            Toast.makeText(
                    this@MainActivity,
                    "Your location provider is turned off. Please turn it on.",
                    Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this)
                    .withPermissions(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    .withListener(object : MultiplePermissionsListener {
                        override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                            if (report!!.areAllPermissionsGranted()) {
                                requestLocationData()
                            }

                            if (report.isAnyPermissionPermanentlyDenied) {
                                Toast.makeText(
                                        this@MainActivity,
                                        "You have denied location permission. P",
                                        Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onPermissionRationaleShouldBeShown(report: MutableList<PermissionRequest>?, token: PermissionToken?) {
                            showRationalDialogForPermissions()
                        }
                    }).onSameThread()
                    .check()
        }
    }

    // ProgressDialog
    private fun progressBar(){
        customDialog = Dialog(this@MainActivity)
        customDialog.setContentView(R.layout.dialog_custom_progress)
        customDialog.show()
    }
    private fun hideProgressBar(){
        customDialog.dismiss()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest.create()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude

            Log.e("LOCATION:", "$latitude, $longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    // ?????? ?????? ?????? ??????
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this@MainActivity)) {
            // retrofit ?????? // API - https://square.github.io/retrofit/
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()) // Gson?????? ?????? ??????????????? ????????? ??????. ???????????? ????????? ?????????
                .build()
            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            /* void enqueue(Callback<T> callback);
             * Asynchronously send the request and notify {@code callback} of its response or if an error
             * occurred talking to the server, creating the request, or processing the response.
             * ?????????????????? ???????????? ?????? ???, ????????? ?????????. execute ?????? ??????????????? ?????????
             * > ????????? ?????? ???????????? ??????????????? Main??????????????? ?????? ????????????,
             *   ????????? ????????????????????? ?????? ????????? ???.
             */
            progressBar()
            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Fail","FAIL, ${t.message.toString()}")
                }

                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse = response.body()!!

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit() // ????????? ?????? ????????? ????????? ??????. ????????? ???????????? ???????????? SharedPreferences ????????? ????????? ??????????????? commit ??????.
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString) // ?????? ????????? ?????? commit?????? apply??? ???????????? ???
                        editor.apply() // editor??? ?????? ???????????? ????????????
                        setupUI()
                        Log.i("ResponseResult", "$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> Log.e("Error 400", "BAD Connection")
                            404 -> Log.e("Error 404", "Not Found")
                            else ->  Log.e("Error $rc", "$rc ERROR")
                        }
                    }
                    hideProgressBar()
                }
            })
        } else {
            Toast.makeText(
                this@MainActivity,
                "No internet connected available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this@MainActivity)
                .setMessage("It looks like you have turned off permissions ...")
                .setPositiveButton("GO TO SETTINGS"){ _, _ ->
                    try {
                        // intent??? ????????? uri??? ?????? Deatils settings ???????????? ??? ??? ?????? ???
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri // ???????????? uri??? ????????? ??????????????? ????????? ?????? ?????????
                        startActivity(intent)
                    }catch (e: Exception){
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel") {dialog, _ ->
                    dialog.dismiss()
                }.show()
    }

    // ?????? ?????? ?????? https://developer.android.com/reference/android/location/LocationManager
    // GPS??? ??????????????? ?????? ?????? ?????? ?????? ??? ?????? (Fused, Passive??? ??????)
    private fun isLocationEnable(): Boolean {
        val locationManager: LocationManager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu )
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        val weatherList: WeatherResponse? =
                weatherResponseJsonString?.run {
                    Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
                } ?: null
        for (i in weatherList!!.weather.indices){
            Log.i("$i weather Name", weatherList.weather.toString())
            binding.apply {
                tvMain.text = weatherList.weather[i].main
                tvMainDescription.text = weatherList.weather[i].description
                tvTemp.text = weatherList.main.temp.toString() + getUnit(weatherList.sys.country) // getUnit(application.resources.configuration.locales.toString())
                tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                tvSunsetTime.text = unixTime(weatherList.sys.sunset)
                tvMax.text = weatherList.main.temp_max.toString() + getUnit(weatherList.sys.country)
                tvMin.text = weatherList.main.temp_min.toString() + getUnit(weatherList.sys.country)
                tvSpeed.text = weatherList.wind.speed.toString()
                tvSpeedUnit.text ="m/s"
                tvCountry.text = weatherList.sys.country
                tvHumidity.text = weatherList.main.humidity.toString() + "%"
                tvName.text = weatherList.name

                // weather icon set up // https://openweathermap.org/weather-conditions
                when(weatherList.weather[i].icon){
                    "01d" -> ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> ivMain.setImageResource(R.drawable.cloud)
                    "09d" -> ivMain.setImageResource(R.drawable.rain)
                    "10d" -> ivMain.setImageResource(R.drawable.rain)
                    "11d" -> ivMain.setImageResource(R.drawable.storm)
                    "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> ivMain.setImageResource(R.drawable.sunny)
                    "02n" -> ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> ivMain.setImageResource(R.drawable.cloud)
                    "09n" -> ivMain.setImageResource(R.drawable.rain)
                    "10n" -> ivMain.setImageResource(R.drawable.rain)
                    "11n" -> ivMain.setImageResource(R.drawable.storm)
                    "13n" -> ivMain.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }

    private fun getUnit(loc: String): String? {
        return when(loc){
            "US", "LR", "MM"-> "???"
            else -> "???"
        }
    }

    private fun unixTime(timex: Long): String{
        val date = Date(timex * 1000L)
        Log.e("date","$date")
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}