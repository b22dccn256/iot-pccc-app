package com.example.iot_pccc_app

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.example.iot_pccc_app.ui.theme.IoT_PCCC_AppTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

// =======================
// MODEL
// =======================
data class FireStatus(
    val trangThaiChay: Int = 0,   // 0: Safe, 1: Fire confirmed, 2: Suspected
    val imageUrl: String = "",
    val temperature: Double = 0.0,
    val gas: Double = 0.0
)

// =======================
// ACTIVITY ROOT
// =======================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IoT_PCCC_AppTheme {

                val prefs = getSharedPreferences("iot_pccc_config", Context.MODE_PRIVATE)
                var savedIp by remember {
                    mutableStateOf(prefs.getString("server_ip", "") ?: "")
                }
                var showIpScreen by remember { mutableStateOf(savedIp.isEmpty()) }
                var showCameraScreen by remember { mutableStateOf(false) }

                when {
                    showIpScreen -> {
                        IpConfigScreen(
                            onSave = { ip ->
                                savedIp = ip
                                prefs.edit().putString("server_ip", ip).apply()
                                ApiClient.initClient(ip)
                                showIpScreen = false
                            }
                        )
                    }

                    showCameraScreen -> {
                        MjpegCameraScreen(
                            serverIp = savedIp,
                            onBack = { showCameraScreen = false }
                        )
                    }

                    else -> {
                        // đảm bảo Retrofit đã trỏ đúng IP
                        LaunchedEffect(savedIp) {
                            if (savedIp.isNotEmpty()) {
                                ApiClient.initClient(savedIp)
                            }
                        }
                        FireMonitorScreen(
                            serverIp = savedIp,
                            onChangeIp = { showIpScreen = true },
                            onOpenLiveCamera = { showCameraScreen = true }
                        )
                    }
                }
            }
        }
    }
}

// =======================
// 1. MÀN HÌNH NHẬP IP SERVER
// =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpConfigScreen(onSave: (String) -> Unit) {
    var ipText by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cấu hình Server IP", fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Nhập địa chỉ IP máy chủ:",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = ipText,
                onValueChange = { ipText = it },
                placeholder = { Text("IP:PORT") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { if (ipText.text.isNotEmpty()) onSave(ipText.text) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Lưu IP", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =======================
// 2. UI MÀN HÌNH GIÁM SÁT
// =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireMonitorScreen(
    serverIp: String,
    onChangeIp: () -> Unit,
    onOpenLiveCamera: () -> Unit
) {
    val context = LocalContext.current

    var fireStatus by remember { mutableStateOf(FireStatus()) }
    var statusMessage by remember { mutableStateOf("Đang kết nối server...") }
    var lastUpdateText by remember { mutableStateOf("--:--") }

    // Âm báo cháy
    val alarmPlayer = remember {
        MediaPlayer.create(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ).apply { isLooping = true }
    }

    DisposableEffect(Unit) {
        onDispose { alarmPlayer.release() }
    }

    // Poll API /status 3s/lần
    // Poll API /sensor 3s/lần
    LaunchedEffect(serverIp) {
        // serverIp lúc này là "ip:port", ví dụ "192.168.1.16:8000"
        val baseUrl = "http://$serverIp"
        while (true) {
            try {
                val sensor = ApiClient.api.getSensor()

                val temp = sensor.temp ?: 0.0
                val gas = sensor.gas ?: 0.0
                val code = computeFireCode(temp, gas)

                // Nếu không cháy thì không cần load ảnh
                val imgUrl = if (code == 0) {
                    ""
                } else {
                    // Thêm ts để tránh cache
                    "$baseUrl/fire-image.jpg?ts=${System.currentTimeMillis()}"
                }

                fireStatus = FireStatus(
                    trangThaiChay = code,
                    imageUrl = imgUrl,
                    temperature = temp,
                    gas = gas
                )

                statusMessage = "Đã kết nối server."
                lastUpdateText = sensor.timestamp ?: SimpleDateFormat("HH:mm:ss").format(Date())

                Log.d("DEBUG_SENSOR", "temp=$temp, gas=$gas, code=$code")
            } catch (e: Exception) {
                statusMessage = "Lỗi: ${e.message}"
                Log.e("DEBUG_SENSOR", "Error", e)
            }
            delay(500)
        }
    }

    val isFireConfirmed = fireStatus.trangThaiChay == 1

    LaunchedEffect(isFireConfirmed) {
        if (isFireConfirmed && !alarmPlayer.isPlaying) {
            alarmPlayer.start()
        } else if (!isFireConfirmed && alarmPlayer.isPlaying) {
            alarmPlayer.pause()
            alarmPlayer.seekTo(0)
        }
    }

    // Nền động theo trạng thái
    val infinite = rememberInfiniteTransition()
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing),
            RepeatMode.Reverse
        )
    )

    val backgroundColor =
        when (fireStatus.trangThaiChay) {
            1 -> lerp(Color(0xFFFFF2F2), Color(0xFFFFE1E1), pulse)
            2 -> Color(0xFFFFF7E6)
            else -> Color(0xFFF0FFF4)
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("IoT PCCC Monitor") },
                actions = {
                    IconButton(onClick = onChangeIp) {
                        Icon(
                            painterResource(id = android.R.drawable.ic_menu_edit),
                            contentDescription = "Đổi IP"
                        )
                    }
                }
            )
        },
        containerColor = backgroundColor
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Thẻ trạng thái
            FireStatusHeader(
                code = fireStatus.trangThaiChay,
                statusMessage = statusMessage,
                lastUpdate = lastUpdateText,
                temperature = fireStatus.temperature,
                gas = fireStatus.gas
            )

            // Cụm chỉ số T° / Gas (UI cũ)
            MetricsSection(
                temperature = fireStatus.temperature,
                gas = fireStatus.gas
            )

            // Nút mở live MJPEG từ ESP32-CAM
            //LiveCameraButton(onOpenLiveCamera)

            // Ảnh snapshot hiện trường (từ /status.imageUrl)
            FireImageCard(
                code = fireStatus.trangThaiChay,
                url = fireStatus.imageUrl
            )

            // =====================
            // LIVE FRAME TỪ SERVER
            // =====================
            Text(
                text = "Live từ Server (Phân tích YOLO)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color(0xFF37474F),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            ServerLiveView(
                serverIp = serverIp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            )
        }
    }
}

// =======================
// 3. THẺ TRẠNG THÁI
// =======================
@Composable
fun FireStatusHeader(
    code: Int,
    statusMessage: String,
    lastUpdate: String,
    temperature: Double,
    gas: Double
) {
    val (title, chipText, gradient) = when (code) {
        1 -> Triple(
            "CẢNH BÁO CHÁY!",
            "FIRE CONFIRMED",
            Brush.linearGradient(listOf(Color(0xFFFF8A80), Color(0xFFFF5252)))
        )
        2 -> Triple(
            "NGHI NGỜ CHÁY",
            "SUSPECTED",
            Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFFB74D)))
        )
        else -> Triple(
            "AN TOÀN",
            "SAFE",
            Brush.linearGradient(listOf(Color(0xFFB9F6CA), Color(0xFF69F0AE)))
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            Box(
                modifier = Modifier
                    .background(gradient, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(chipText, color = Color.White, fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = when (code) {
                    1 -> Color(0xFFD32F2F)
                    2 -> Color(0xFFF57C00)
                    else -> Color(0xFF1B5E20)
                }
            )

            Spacer(Modifier.height(6.dp))

            Text(statusMessage, fontSize = 13.sp, color = Color.Gray)

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Cập nhật", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        lastUpdate,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("T° / Gas", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        String.format("%.1f °C • %.0f", temperature, gas),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// =======================
// 4. CỤM THẺ CHỈ SỐ 2 CỘT (UI cũ)
// =======================
@Composable
fun MetricsSection(temperature: Double, gas: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Thông số cảm biến",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF37474F)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Nhiệt độ",
                value = String.format("%.1f °C", temperature),
                accentColor = Color(0xFFD84315),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Gas",
                value = String.format("%.0f", gas),
                accentColor = Color(0xFF1A237E),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }
    }
}


// =======================
// 6. THẺ ẢNH HIỆN TRƯỜNG (snapshot từ /status.imageUrl)
// =======================
@Composable
fun FireImageCard(code: Int, url: String) {
    val show = (code == 1 || code == 2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        if (show && url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = "Ảnh hiện trường",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 450.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Hiện chưa có ảnh.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// =======================
// 7. MÀN HÌNH MJPEG CAMERA (ESP32-CAM trực tiếp)
// =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MjpegCameraScreen(
    serverIp: String,
    onBack: () -> Unit
) {
    // URL MJPEG: nếu có ESP32Cam dùng IP thật, nếu không thì dùng URL demo
    val streamUrl = remember(serverIp) {
        if (serverIp.isNotBlank())
            "http://$serverIp:81/stream" // ESP32Cam mặc định
        else
            "http://213.226.254.135:91/mjpg/video.mjpg"    // demo MJPEG public
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Live Camera (MJPEG)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(id = android.R.drawable.ic_media_previous),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { pad ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.loadsImagesAutomatically = true
                    settings.javaScriptEnabled = false
                    webViewClient = WebViewClient()
                    loadUrl(streamUrl)
                }
            },
            update = { webView ->
                webView.loadUrl(streamUrl)
            }
        )
    }
}

// =======================
// 8. LIVE FRAME TỪ SERVER (/live_frame)
// =======================
@Composable
fun ServerLiveView(
serverIp: String,           // "ip:port"
modifier: Modifier = Modifier
) {
    val baseUrl = remember(serverIp) { "http://$serverIp" }

    var liveUrl by remember { mutableStateOf<String?>(null) }

    // Poll /fire-image.jpg 400ms/lần
    LaunchedEffect(baseUrl) {
        while (true) {
            liveUrl = "$baseUrl/fire-image.jpg?ts=${System.currentTimeMillis()}"
            delay(200) // 0.1 giây 1 lần -> 10 lần / giây
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (liveUrl == null) {
            Text(
                text = "Đang chờ khung hình...",
                color = Color.White,
                fontSize = 14.sp
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(liveUrl)
                    .crossfade(false)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = "Live from server",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// TÍNH TRẠNG THÁI CHÁY TỪ SENSOR
fun computeFireCode(temp: Double, gas: Double): Int {
    // Ngưỡng giống phần server Flask trước đây
    val tempLevel = when {
        temp >= 60.0 -> 2  // nguy hiểm
        temp >= 45.0 -> 1  // nghi ngờ
        else -> 0
    }

    val gasLevel = when {
        gas >= 600.0 -> 2
        gas >= 300.0 -> 1
        else -> 0
    }

    val danger = (tempLevel == 2) || (gasLevel == 2)
    val suspect = (tempLevel == 1) || (gasLevel == 1)

    return when {
        danger -> 1            // FIRE_STATE_CONFIRMED
        suspect -> 2           // FIRE_STATE_SUSPECTED
        else -> 0              // SAFE
    }
}
