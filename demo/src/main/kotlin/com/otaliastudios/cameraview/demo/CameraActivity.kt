package com.otaliastudios.cameraview.demo

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.location.AMapLocationQualityReport
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.otaliastudios.cameraview.*
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Mode
import com.otaliastudios.cameraview.controls.Preview
import com.otaliastudios.cameraview.filter.Filters
import com.otaliastudios.cameraview.frame.Frame
import com.otaliastudios.cameraview.frame.FrameProcessor
import java.io.*
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity(), View.OnClickListener, OptionView.Callback {

    // mNote: 2022/7/24 新增变量
    public var latitude: Double? = null
    public var longitude: Double? = null
    var TakePhotoState = java.lang.Boolean.FALSE;
    var timer = Timer()
    var imageCountI = 0;
    var folderPath = "" //存储目录
    public var locationClient: AMapLocationClient? = null
    public var locationOption: AMapLocationClientOption? = null
    // public var tempTxt: TextView? = null
    var tempTxt: TextView? = null
    var fText: TextView? = null
    var posText: TextView? = null
    private var BatteryN: Int? = null;		//目前电量
    private var BatteryV: Int? = null;		//电池电压
    private var BatteryT: Int? = null;		//电池温度
    private var BatteryStatus: String? = null;	//电池状态
    private var BatteryTemp: String? = null;		//电池使用情况

    companion object {
        private val LOG = CameraLogger.create("DemoApp")
        private const val USE_FRAME_PROCESSOR = false
        private const val DECODE_BITMAP = false
    }

    private val camera: CameraView by lazy { findViewById(R.id.camera) }
    private val controlPanel: ViewGroup by lazy { findViewById(R.id.controls) }
    private var captureTime: Long = 0

    private var currentFilter = 0
    private val allFilters = Filters.values()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // mNote: 2022/7/24 请求权限执行
        request_permissions()

        // mNote: 2022/7/25 注册电池监听器
        registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // mNote: 2022/7/24 建立文件目录
        // 建立输出文件目录
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            val sdcardDir = getExternalFilesDir(null) // 在Android/data/包名/files
            folderPath = sdcardDir!!.path
            val path1: File = File(folderPath)
            if (!path1.exists()) {
                path1.mkdirs()
            }
        }

        // mNote: 2022/7/24 定位用
        var mLocationClient: AMapLocationClient? = null
        val mLocationListener = AMapLocationListener(){}
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);
        mLocationClient = AMapLocationClient(applicationContext)
        mLocationClient.setLocationListener(mLocationListener)
        AMapLocationClient.setApiKey("107a568ed4143f3317a32d457ef6814d");
        initLocation()

        CameraLogger.setLogLevel(CameraLogger.LEVEL_VERBOSE)
        camera.setLifecycleOwner(this)
        camera.addCameraListener(Listener())
        if (USE_FRAME_PROCESSOR) {
            camera.addFrameProcessor(object : FrameProcessor {
                private var lastTime = System.currentTimeMillis()
                override fun process(frame: Frame) {
                    val newTime = frame.time
                    val delay = newTime - lastTime
                    lastTime = newTime
                    LOG.v("Frame delayMillis:", delay, "FPS:", 1000 / delay)
                    if (DECODE_BITMAP) {
                        if (frame.format == ImageFormat.NV21
                                && frame.dataClass == ByteArray::class.java) {
                            val data = frame.getData<ByteArray>()
                            val yuvImage = YuvImage(data,
                                    frame.format,
                                    frame.size.width,
                                    frame.size.height,
                                    null)
                            val jpegStream = ByteArrayOutputStream()
                            yuvImage.compressToJpeg(Rect(0, 0,
                                    frame.size.width,
                                    frame.size.height), 100, jpegStream)
                            val jpegByteArray = jpegStream.toByteArray()
                            val bitmap = BitmapFactory.decodeByteArray(jpegByteArray,
                                    0, jpegByteArray.size)
                            bitmap.toString()
                        }
                    }
                }
            })
        }
        // findViewById<View>(R.id.edit).setOnClickListener(this)
        findViewById<View>(R.id.capturePicture).setOnClickListener(this)
        findViewById<View>(R.id.capturePictureSnapshot).setOnClickListener(this)
        // findViewById<View>(R.id.captureVideo).setOnClickListener(this)
//        findViewById<View>(R.id.captureVideoSnapshot).setOnClickListener(this)
//        findViewById<View>(R.id.toggleCamera).setOnClickListener(this)
//        findViewById<View>(R.id.changeFilter).setOnClickListener(this)
        val group = controlPanel.getChildAt(0) as ViewGroup
//        val watermark = findViewById<View>(R.id.watermark)
        tempTxt = findViewById<TextView>(R.id.tempText) // mNote: 2022/7/25 绑定温度显示
        fText = findViewById<TextView>(R.id.fileText)
        posText = findViewById<TextView>(R.id.LocText)
        val options: List<Option<*>> = listOf(
                // Layout
                Option.Width(), Option.Height(),
                // Engine and preview
                Option.Mode(), Option.Engine(), Option.Preview(),
                // Some controls
                Option.Flash(), Option.WhiteBalance(), Option.Hdr(),
                Option.PictureMetering(), Option.PictureSnapshotMetering(),
                Option.PictureFormat(),
                // Video recording
                Option.PreviewFrameRate(), Option.VideoCodec(), Option.Audio(), Option.AudioCodec(),
                // Gestures
                Option.Pinch(), Option.HorizontalScroll(), Option.VerticalScroll(),
                Option.Tap(), Option.LongTap(),
                // Watermarks
//                Option.OverlayInPreview(watermark),
//                Option.OverlayInPictureSnapshot(watermark),
//                Option.OverlayInVideoSnapshot(watermark),
                // Frame Processing
                Option.FrameProcessingFormat(),
                // Other
                Option.Grid(), Option.GridColor(), Option.UseDeviceOrientation()
        )
        val dividers = listOf(
                // Layout
                false, true,
                // Engine and preview
                false, false, true,
                // Some controls
                false, false, false, false, false, true,
                // Video recording
                false, false, false, true,
                // Gestures
                false, false, false, false, true,
                // Watermarks
                false, false, true,
                // Frame Processing
                true,
                // Other
                false, false, true
        )
        for (i in options.indices) {
            val view = OptionView<Any>(this)
            view.setOption(options[i] as Option<Any>, this)
            view.setHasDivider(dividers[i])
            group.addView(view, MATCH_PARENT, WRAP_CONTENT)
        }
        controlPanel.viewTreeObserver.addOnGlobalLayoutListener {
            BottomSheetBehavior.from(controlPanel).state = BottomSheetBehavior.STATE_HIDDEN
        }

        // Animate the watermark just to show we record the animation in video snapshots
        val animator = ValueAnimator.ofFloat(1f, 0.8f)
        animator.duration = 300
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
//        animator.addUpdateListener { animation ->
//            val scale = animation.animatedValue as Float
//            watermark.scaleX = scale
//            watermark.scaleY = scale
//            watermark.rotation = watermark.rotation + 2
//        }
        animator.start()
    }

    override fun onResume() {
        super.onResume()
        startLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyLocation()
    }

    private fun message(content: String, important: Boolean) {
        if (important) {
            LOG.w(content)
            Toast.makeText(this, content, Toast.LENGTH_LONG).show()
        } else {
            LOG.i(content)
            Toast.makeText(this, content, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 初始化定位
     *
     * @since 2.8.0
     * @author hongming.wang
     */
    private fun initLocation() {
        //初始化client
        try {
            locationClient = AMapLocationClient(this.applicationContext)
            locationOption = getDefaultOption()
            //设置定位参数
            locationClient!!.setLocationOption(locationOption)
            // 设置定位监听
            locationClient!!.setLocationListener(locationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * 定位监听
     */
    @SuppressLint("SetTextI18n")
    var locationListener =
            AMapLocationListener { location ->
                if (null != location) {
                    val sb = StringBuffer()
                    //errCode等于0代表定位成功，其他的为定位失败，具体的可以参照官网定位错误码说明
                    Log.i("LocInfoAmap", location.longitude.toString() + " " + System.currentTimeMillis())
                    latitude = location.latitude
                    longitude = location.longitude
                    // tvResult?.setText(location.longitude.toString() + " " + System.currentTimeMillis())
                    if (location.errorCode == 0) {
                        sb.append("""定位成功""".trimIndent()).append("\n")
                        sb.append("""定位类型: ${location.locationType}""".trimIndent()).append("\n")
                        sb.append("""经    度: ${location.longitude}""".trimIndent()).append("\n")
                        sb.append("""纬    度    : ${location.latitude}""".trimIndent()).append("\n")
                        sb.append("""精    度    : ${location.accuracy}米""".trimIndent()).append("\n")
                        sb.append("""提供者    : ${location.provider}""".trimIndent()).append("\n")
                        sb.append( """ 速    度    : ${location.speed}米/秒 """.trimIndent()).append("\n")
                        sb.append("""角    度    : ${location.bearing}""".trimIndent() ).append("\n")
                        // 获取当前提供定位服务的卫星个数
                        sb.append("""星    数    : ${location.satellites} """.trimIndent()).append("\n")
                        sb.append("""国    家    : ${location.country}""".trimIndent()).append("\n")
                        sb.append("""省            : ${location.province}""".trimIndent()).append("\n")
                        sb.append("""市            : ${location.city} """.trimIndent()    ).append("\n")
                        sb.append("""城市编码 : ${location.cityCode}""".trimIndent()    ).append("\n")
                        sb.append("""区            : ${location.district}""".trimIndent()     ).append("\n")
                        sb.append("""区域 码   : ${location.adCode}""".trimIndent()   ).append("\n")
                        sb.append(   """地    址    : ${location.address}""".trimIndent()     ).append("\n")
                        sb.append("""兴趣点    : ${location.poiName} """.trimIndent() ).append("\n")
                        //定位完成的时间
                        // sb.append("""定位时间: ${Utils.formatUTC(location.time, "yyyy-MM-dd HH:mm:ss").toString() }""".trimIndent())
                    } else {
                        //定位失败
                        sb.append( """定位失败""".trimIndent()     ).append("\n")
                        sb.append("""错误码:${location.errorCode}""".trimIndent()).append("\n")
                        sb.append("""错误信息:${location.errorInfo} """.trimIndent()).append("\n")
                        sb.append("""错误描述:${location.locationDetail}""".trimIndent()).append("\n")
                    }
                    sb.append("***定位质量报告***").append("\n")
                    sb.append("* WIFI开关：")
                            .append(if (location.locationQualityReport.isWifiAble) "开启" else "关闭")
                            .append("\n")
                    sb.append("* GPS状态：").append(getGPSStatusString(location.locationQualityReport.gpsStatus))
                            .append("\n")
                    sb.append("* GPS星数：").append(location.locationQualityReport.gpsSatellites)
                            .append("\n")
                    sb.append("* 网络类型：" + location.locationQualityReport.networkType).append("\n")
                    sb.append("* 网络耗时：" + location.locationQualityReport.netUseTime).append("\n")
                    sb.append("****************").append("\n")
                    //定位之后的回调时间
                    // sb.append("""回调时间: ${Utils.formatUTC(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss").toString()} """.trimIndent()     )

                    //解析定位结果，
                    val result = sb.toString()
                    posText?.text = result
                } else {
                    posText?.text = "定位失败，loc is null"
                }
            }


    /**
     * 开始定位
     *
     * @since 2.8.0
     * @author hongming.wang
     */
    private fun startLocation() {
        try {
            //根据控件的选择，重新设置定位参数
            // resetOption()
            // 设置定位参数
            locationClient!!.setLocationOption(locationOption)
            // 启动定位
            locationClient!!.startLocation()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 停止定位
     *
     * @since 2.8.0
     * @author hongming.wang
     */
    private fun stopLocation() {
        try {
            // 停止定位
            locationClient!!.stopLocation()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 销毁定位
     *
     * @since 2.8.0
     * @author hongming.wang
     */
    private fun destroyLocation() {
        if (null != locationClient) {
            /**
             * 如果AMapLocationClient是在当前Activity实例化的，
             * 在Activity的onDestroy中一定要执行AMapLocationClient的onDestroy
             */
            locationClient!!.onDestroy()
            locationClient = null
            locationOption = null
        }
    }

    /**
     * 默认的定位参数
     * @since 2.8.0
     * @author hongming.wang
     */
    private fun getDefaultOption(): AMapLocationClientOption? {
        val mOption = AMapLocationClientOption()
        mOption.locationMode =
                AMapLocationClientOption.AMapLocationMode.Hight_Accuracy //可选，设置定位模式，可选的模式有高精度、仅设备、仅网络。默认为高精度模式
        mOption.isGpsFirst = false //可选，设置是否gps优先，只在高精度模式下有效。默认关闭
        mOption.httpTimeOut = 30000 //可选，设置网络请求超时时间。默认为30秒。在仅设备模式下无效
        mOption.interval = 2000 //可选，设置定位间隔。默认为2秒
        mOption.isNeedAddress = true //可选，设置是否返回逆地理地址信息。默认是true
        mOption.isOnceLocation = false //可选，设置是否单次定位。默认是false
        mOption.isOnceLocationLatest =
                false //可选，设置是否等待wifi刷新，默认为false.如果设置为true,会自动变为单次定位，持续定位时不要使用
        AMapLocationClientOption.setLocationProtocol(AMapLocationClientOption.AMapLocationProtocol.HTTP) //可选， 设置网络请求的协议。可选HTTP或者HTTPS。默认为HTTP
        mOption.isSensorEnable = false //可选，设置是否使用传感器。默认是false
        mOption.isWifiScan =
                true //可选，设置是否开启wifi扫描。默认为true，如果设置为false会同时停止主动刷新，停止以后完全依赖于系统刷新，定位位置可能存在误差
        mOption.isLocationCacheEnable = true //可选，设置是否使用缓存定位，默认为true
        mOption.geoLanguage =
                AMapLocationClientOption.GeoLanguage.DEFAULT //可选，设置逆地理信息的语言，默认值为默认语言（根据所在地区选择语言）
        return mOption
    }

    /**
     * 获取GPS状态的字符串
     * @param statusCode GPS状态码
     * @return
     */
    private fun getGPSStatusString(statusCode: Int): String? {
        var str = ""
        when (statusCode) {
            AMapLocationQualityReport.GPS_STATUS_OK -> str = "GPS状态正常"
            AMapLocationQualityReport.GPS_STATUS_NOGPSPROVIDER -> str =
                    "手机中没有GPS Provider，无法进行GPS定位"
            AMapLocationQualityReport.GPS_STATUS_OFF -> str = "GPS关闭，建议开启GPS，提高定位质量"
            AMapLocationQualityReport.GPS_STATUS_MODE_SAVING -> str =
                    "选择的定位模式中不包含GPS定位，建议选择包含GPS定位的模式，提高定位质量"
            AMapLocationQualityReport.GPS_STATUS_NOGPSPERMISSION -> str = "没有GPS定位权限，建议开启gps定位权限"
        }
        return str
    }


    private inner class Listener : CameraListener() {
        override fun onCameraOpened(options: CameraOptions) {
            val group = controlPanel.getChildAt(0) as ViewGroup
            for (i in 0 until group.childCount) {
                val view = group.getChildAt(i) as OptionView<*>
                view.onCameraOpened(camera, options)
            }
        }

        override fun onCameraError(exception: CameraException) {
            super.onCameraError(exception)
            message("Got CameraException #" + exception.reason, true)
        }

        override fun onPictureTaken(result: PictureResult) {
            super.onPictureTaken(result)
            if (camera.isTakingVideo) {
                message("Captured while taking video. Size=" + result.size, false)
                return
            }

            // This can happen if picture was taken with a gesture.
            val callbackTime = System.currentTimeMillis()
            if (captureTime == 0L) captureTime = callbackTime - 300
            LOG.w("onPictureTaken called! Launching activity. Delay:", callbackTime - captureTime)
            PicturePreviewActivity.pictureResult = result
            // mNote: 2022/7/24 在此处保存文件 result
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            val absoluteLoc = "/IMG_${sdf.format(Date())}.jpg"
            getFile(result.data, folderPath, absoluteLoc)
            fText?.text = absoluteLoc.substring(16,28) // 为了让用户感知正在采集中

            // exif信息保存
            val fullLoc = folderPath + absoluteLoc
            Log.i("成功保存", fullLoc)
            val exif = ExifInterface(fullLoc)
            exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION, result.rotation.toString())
            val lat = latitude?.let { getExifGPSString(it) }
            val lon = longitude?.let { getExifGPSString(it) }
            if (lat != null) {
                exif.setAttribute(
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        if (lat.split("/")[0].toDouble() > 0.0f) "E" else "W"
                )
            }
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat.toString())
            if (lon != null) {
                exif.setAttribute(
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        if (lon.split("/")[0].toDouble() > 0.0f) "N" else "S"
                )
            }
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lon.toString())
            exif.saveAttributes()

            // val intent = Intent(this@CameraActivity, PicturePreviewActivity::class.java)
            // intent.putExtra("delay", callbackTime - captureTime)
            // startActivity(intent) mNote: mao 注释掉了这里，取消拍照后的跳转
            captureTime = 0
            LOG.w("onPictureTaken called! Launched activity.")
        }

        override fun onVideoTaken(result: VideoResult) {
            super.onVideoTaken(result)
            LOG.w("onVideoTaken called! Launching activity.")
            VideoPreviewActivity.videoResult = result
            val intent = Intent(this@CameraActivity, VideoPreviewActivity::class.java)
            startActivity(intent)
            LOG.w("onVideoTaken called! Launched activity.")
        }

        override fun onVideoRecordingStart() {
            super.onVideoRecordingStart()
            LOG.w("onVideoRecordingStart!")
        }

        override fun onVideoRecordingEnd() {
            super.onVideoRecordingEnd()
            message("Video taken. Processing...", false)
            LOG.w("onVideoRecordingEnd!")
        }

        override fun onExposureCorrectionChanged(newValue: Float, bounds: FloatArray, fingers: Array<PointF>?) {
            super.onExposureCorrectionChanged(newValue, bounds, fingers)
            message("Exposure correction:$newValue", false)
        }

        override fun onZoomChanged(newValue: Float, bounds: FloatArray, fingers: Array<PointF>?) {
            super.onZoomChanged(newValue, bounds, fingers)
            message("Zoom:$newValue", false)
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            // R.id.edit -> edit()
            R.id.capturePicture -> capturePicture()
            R.id.capturePictureSnapshot -> capturePictureSnapshot()
//            R.id.captureVideo -> captureVideo()
//            R.id.captureVideoSnapshot -> captureVideoSnapshot()
//            R.id.toggleCamera -> toggleCamera()
//            R.id.changeFilter -> changeCurrentFilter()
        }
    }

    override fun onBackPressed() {
        val b = BottomSheetBehavior.from(controlPanel)
        if (b.state != BottomSheetBehavior.STATE_HIDDEN) {
            b.state = BottomSheetBehavior.STATE_HIDDEN
            return
        }
        super.onBackPressed()
    }

    private fun edit() {
        BottomSheetBehavior.from(controlPanel).state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun capturePicture() {
        if (camera.mode == Mode.VIDEO) return run {
            message("Can't take HQ pictures while in VIDEO mode.", false)
        }
        if (camera.isTakingPicture) return
        // captureTime = System.currentTimeMillis()
        // message("Capturing picture...", false)
        // camera.takePicture()

        TakePhotoState = !TakePhotoState
        // 定时任务
        if(TakePhotoState){
            Toast.makeText(this, "开始高清采集 ^。^", Toast.LENGTH_LONG).show()
            timer = Timer()
            timer.schedule(object:TimerTask(){
                override fun run() {
                    camera.takePicture()
                    Log.i("高清拍照次数", imageCountI.toString())
                }
            }, Date(), 1000)
        } else {
            Toast.makeText(this, "高清采集完毕 *-*", Toast.LENGTH_LONG).show()
            timer.cancel()
            timer.purge()
        }
    }

    private fun capturePictureSnapshot() {
//        if (camera.isTakingPicture) return
//        if (camera.preview != Preview.GL_SURFACE) return run {
//            message("Picture snapshots are only allowed with the GL_SURFACE preview.", true)
//        }
//        captureTime = System.currentTimeMillis()
//        message("Capturing picture snapshot...", false)
//        camera.takePictureSnapshot()

        /**
         * 以下为新增部分
         * */
        // TODO：修改这部分
        camera.takePictureSnapshot();
        sleep(10)
        camera.takePictureSnapshot(); //TODO：为啥调用两次，需要确认原因，应该可以删除一个

        TakePhotoState = !TakePhotoState

        // 定时任务
        if(TakePhotoState){
            Toast.makeText(this, "开始采集 ^。^", Toast.LENGTH_LONG).show()
            timer = Timer()
            timer.schedule(object:TimerTask(){
                override fun run() {
                    camera.takePictureSnapshot();
                    Log.i("拍照次数", imageCountI.toString())
                }
            }, Date(), 1000)
        } else {
            Toast.makeText(this, "采集完毕 *-*", Toast.LENGTH_LONG).show()
            timer.cancel()
            timer.purge()
        }


    }

    private fun captureVideo() {
        if (camera.mode == Mode.PICTURE) return run {
            message("Can't record HQ videos while in PICTURE mode.", false)
        }
        if (camera.isTakingPicture || camera.isTakingVideo) return
        message("Recording for 5 seconds...", true)
        camera.takeVideo(File(filesDir, "video.mp4"), 5000)
    }

    private fun captureVideoSnapshot() {
        if (camera.isTakingVideo) return run {
            message("Already taking video.", false)
        }
        if (camera.preview != Preview.GL_SURFACE) return run {
            message("Video snapshots are only allowed with the GL_SURFACE preview.", true)
        }
        message("Recording snapshot for 5 seconds...", true)
        camera.takeVideoSnapshot(File(filesDir, "video.mp4"), 5000)
    }

    private fun toggleCamera() {
        if (camera.isTakingPicture || camera.isTakingVideo) return
        when (camera.toggleFacing()) {
            Facing.BACK -> message("Switched to back camera!", false)
            Facing.FRONT -> message("Switched to front camera!", false)
        }
    }

    /**
     * bfile 需要转换成文件的byte数组
     * filePath  生成的文件保存路径
     * fileName  生成文件后保存的名称如test.pdf，test.jpg等
     */
    open fun getFile(bfile: ByteArray?, filePath: String, fileName: String) {
        var bos: BufferedOutputStream? = null
        var fos: FileOutputStream? = null
        var file: File? = null
        try {
            val dir = File(filePath)
            val isDir = dir.isDirectory
            if (!isDir) { // 目录不存在则先建目录
                try {
                    dir.mkdirs()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            file = File(filePath + File.separator + fileName)
            fos = FileOutputStream(file)
            bos = BufferedOutputStream(fos)
            bos.write(bfile)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (bos != null) {
                try {
                    bos.close()
                } catch (e1: IOException) {
                    e1.printStackTrace()
                }
            }
            if (fos != null) {
                try {
                    fos.close()
                } catch (e1: IOException) {
                    e1.printStackTrace()
                }
            }
        }
    }

    private fun changeCurrentFilter() {
        if (camera.preview != Preview.GL_SURFACE) return run {
            message("Filters are supported only when preview is Preview.GL_SURFACE.", true)
        }
        if (currentFilter < allFilters.size - 1) {
            currentFilter++
        } else {
            currentFilter = 0
        }
        val filter = allFilters[currentFilter]
        message(filter.toString(), false)

        // Normal behavior:
        camera.filter = filter.newInstance()

        // To test MultiFilter:
        // DuotoneFilter duotone = new DuotoneFilter();
        // duotone.setFirstColor(Color.RED);
        // duotone.setSecondColor(Color.GREEN);
        // camera.setFilter(new MultiFilter(duotone, filter.newInstance()));
    }

    override fun <T : Any> onValueChanged(option: Option<T>, value: T, name: String): Boolean {
        if (option is Option.Width || option is Option.Height) {
            val preview = camera.preview
            val wrapContent = value as Int == WRAP_CONTENT
            if (preview == Preview.SURFACE && !wrapContent) {
                message("The SurfaceView preview does not support width or height changes. " +
                        "The view will act as WRAP_CONTENT by default.", true)
                return false
            }
        }
        option.set(camera, value)
        BottomSheetBehavior.from(controlPanel).state = BottomSheetBehavior.STATE_HIDDEN
        message("Changed " + option.name + " to " + name, false)
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val valid = grantResults.all { it == PERMISSION_GRANTED }
        if (valid && !camera.isOpened) {
            camera.open()
        }
    }


    // mNote: 2022/7/24 函数定义
    // 请求多个权限
    private fun request_permissions() {
        // 创建一个权限列表，把需要使用而没用授权的的权限存放在这里
        val permissionList: MutableList<String> = ArrayList()

        // 判断权限是否已经授予，没有就把该权限添加到列表中
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this,  //need
                        Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add( //need
                    Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }


        // 如果列表为空，就是全部权限都获取了，不用再次获取了。不为空就去申请权限
        if (!permissionList.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionList.toTypedArray(), 1002
            )
        } else {
            Toast.makeText(this, "权限已有，本次未更新", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Degree to Degree minute second. 十进制度转特定格式写入Exif(xxx° ==> xxx/1,xxx/1,xxx/100″)
     */
    fun getExifGPSString(radian: Double): String? {
        var degree = 0
        var minute = 0.0
        var second = 0.0
        try {
            degree = radian.toInt()
            minute = (radian - degree) * 60
            second = (minute - minute.toInt()) * 60 * 100 // *100试试
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return String.format("%1\$s/1,%2\$s/1,%3$.2f/100", degree, minute.toInt(), second)
    }

    // mNote: 2022/7/25 电池信息监听器
    /* 电池温度展示 */
    private val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            /*
             * 如果捕捉到的action是ACTION_BATTERY_CHANGED， 就运行onBatteryInfoReceiver()
             */if (Intent.ACTION_BATTERY_CHANGED == action) {
                BatteryN = intent.getIntExtra("level", 0) //目前电量
                BatteryV = intent.getIntExtra("voltage", 0) //电池电压
                BatteryT = intent.getIntExtra("temperature", 0) //电池温度
                when (intent.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN)) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus = "充电中"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus = "放电中"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus = "未充电"
                    BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus = "充满电"
                    BatteryManager.BATTERY_STATUS_UNKNOWN -> BatteryStatus = "未知道状态"
                }
                when (intent.getIntExtra("health", BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                    BatteryManager.BATTERY_HEALTH_UNKNOWN -> BatteryTemp = "未知错误"
                    BatteryManager.BATTERY_HEALTH_GOOD -> BatteryTemp = "状态良好"
                    BatteryManager.BATTERY_HEALTH_DEAD -> BatteryTemp = "电池没有电"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryTemp = "电池电压过高"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryTemp = "电池过热"
                }
                tempTxt?.text  =
                        """
                    电量$BatteryN%
                    温度${BatteryT!! / 10}
                    """.trimIndent() + "." + BatteryT!! %10+ "℃"
//                tempTxt?.text  =
//                    """
//                    电量$BatteryN% - $BatteryStatus
//                    温度${BatteryT!! / 10}
//                    """.trimIndent() + "." + BatteryT!! %10+ "℃"

            }
        }
    }
}
