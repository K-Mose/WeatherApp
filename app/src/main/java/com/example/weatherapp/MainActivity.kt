package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)


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
        Log.e("프로그레스다이얼로그  ", "첵")
        customDialog = Dialog(this@MainActivity)
        customDialog.setContentView(R.layout.dialog_custom_progress)
        customDialog.show()
    }
    private fun hideProgressBar(){
        Log.e("프로그레스다이얼로그  ", "아웃")
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

    // 네트 워크 연결 확인
    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this@MainActivity)) {
            // retrofit 사용 // API - https://square.github.io/retrofit/
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()) // Gson으로 자동 컨버젼하는 팩토리 생성. 컨버터의 종류는 다양함
                .build()
            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            /* void enqueue(Callback<T> callback);
             * Asynchronously send the request and notify {@code callback} of its response or if an error
             * occurred talking to the server, creating the request, or processing the response.
             * 비동기식으로 리퀘스트 전송 후, 응답을 받아옴. execute 하면 동기식으로 사용함
             * > 그러고 보니 네트워크 리퀘스트는 Main스레드에서 실행 안되는데,
             *   콜백이 메인스레드에서 실행 된다고 함.
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
                        setupUI(weatherList)
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
                        // intent에 패키지 uri를 줘서 Deatils settings 패이지를 열 수 있게 함
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri // 데이터에 uri를 바인드 안시켜주면 시스템 에러 발생함
                        startActivity(intent)
                    }catch (e: Exception){
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel") {dialog, _ ->
                    dialog.dismiss()
                }.show()
    }

    // 위치 설정 확인 https://developer.android.com/reference/android/location/LocationManager
    // GPS와 네트워크를 통해 위치 정보 받을 수 있음 (Fused, Passive도 있음)
    private fun isLocationEnable(): Boolean {
        val locationManager: LocationManager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI(weatherList: WeatherResponse){
        for (i in weatherList.weather.indices){
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

                // weather icon set up
                when(weatherList.weather[i].icon){
                    "01d" -> ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> ivMain.setImageResource(R.drawable.cloud)
                    "09d" -> ivMain.setImageResource(R.drawable.rain)
                    "10d" -> ivMain.setImageResource(R.drawable.storm)
                    "11d" -> ivMain.setImageResource(R.drawable.storm)
                    "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> ivMain.setImageResource(R.drawable.sunny)
                    "02n" -> ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> ivMain.setImageResource(R.drawable.cloud)
                    "09n" -> ivMain.setImageResource(R.drawable.rain)
                    "10n" -> ivMain.setImageResource(R.drawable.storm)
                    "11n" -> ivMain.setImageResource(R.drawable.storm)
                    "13n" -> ivMain.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }

    private fun getUnit(loc: String): String? {
        Log.e("LOCALES",loc)
        return when(loc){
            "US", "LR", "MM"-> "℉"
            else -> "℃"
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