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

        /* SharedPreferences -> 앱 데이터 폴더 내에 shared_prefs 폴더를 만들어서 key, value 형식으로 데이터를 저장한다.
           데이터는 일관된 상태와 흐름을 저장하기 위해 Editor객체를 이용하며, 가져올 때는 immutable로 가져온다.
        */
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE) // 생성된 데이터가 현재 앱에서만 사용되고 다른 앱에서 접근 못하게 설정

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

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit() // 참조를 위한 데이터 에디터 생성. 참조의 데이터를 수정하고 SharedPreferences 객체의 변화를 자동적으로 commit 한다.
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString) // 작성 완료를 위해 commit이나 apply를 호출해야 함
                        editor.apply() // editor에 넣은 데이터를 적용시킴
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