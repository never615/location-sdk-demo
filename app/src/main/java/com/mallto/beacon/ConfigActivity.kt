package com.mallto.beacon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mallto.beacon.databinding.ActivityConfigBinding

class ConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConfigBinding

    companion object {
        /**
         * 用户标识是否允许手动输入；false 时仅可通过扫码获取。
         */
        private const val MANUAL_INPUT_ENABLED = false
    }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScanResult(result.contents)
        }
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                decodeQrFromUri(uri)
            }
        }

    /**
     * 处理扫码/解码结果：仅接受 6 位 16 进制字符串作为用户标识。
     */
    private fun handleScanResult(rawResult: String?) {
        val scanResult = rawResult?.trim() ?: return
        if (scanResult.matches(Regex("^[0-9a-fA-F]+$"))) {
            if (scanResult.length == 6) {
                binding.tvUserIdentifier.setText(scanResult)
                binding.tvUserIdentifier.setTextColor(getColor(android.R.color.black))
                Toast.makeText(this, "扫描成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "用户标识不符合规则：$scanResult", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "用户标识不符合规则：$scanResult", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 加载保存的配置
        loadConfig()

        // 控制用户标识是否可手动输入
        binding.tvUserIdentifier.isEnabled = MANUAL_INPUT_ENABLED
        if (MANUAL_INPUT_ENABLED) {
            binding.tvUserIdentifier.hint = "未设置（请扫码获取或手动输入）"
        } else {
            binding.tvUserIdentifier.hint = "未设置（请扫码获取）"
        }

        // 设置点击事件
        binding.btnManageUuids.setOnClickListener {
            // 跳转到 UUID 列表管理页面
            startActivity(android.content.Intent(this, UuidListActivity::class.java))
        }

        binding.btnScanQrCode.setOnClickListener {
            startQrCodeScan()
        }

        binding.btnScanFromAlbum.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnSave.setOnClickListener {
            saveConfig()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // 刷新 UUID 列表显示
        updateUuidDisplay()
    }

    private fun startQrCodeScan() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("扫描用户标识二维码")
        options.setCameraId(0)
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(false)
        options.setOrientationLocked(true)
        barcodeLauncher.launch(options)
    }

    private fun decodeQrFromUri(uri: Uri) {
        // 缩采样加载，避免大尺寸相机原图 OOM / 过慢
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
            return
        }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 2000) sampleSize *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        val bitmap: Bitmap = try {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: run {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
            return
        }

        val result = decodeQrFromBitmap(bitmap)
        bitmap.recycle()
        if (result != null) {
            handleScanResult(result)
        } else {
            Toast.makeText(this, "未识别到二维码", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return null

        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf("QR_CODE"),
            DecodeHintType.TRY_HARDER to true
        )

        // 1. 整图尝试 4 个方向
        decodeWithRotations(bitmap, hints)?.let { return it }

        // 2. 滑动窗口多尺度裁剪：针对照片中只占一小块的二维码
        val minDim = minOf(w, h)
        val cropSizes = listOf(
            (minDim * 0.8f).toInt(),
            (minDim * 0.6f).toInt(),
            (minDim * 0.45f).toInt(),
            (minDim * 0.35f).toInt(),
            (minDim * 0.25f).toInt()
        )
        for (cropSize in cropSizes) {
            if (cropSize < 120) continue
            val step = (cropSize / 2).coerceAtLeast(1)
            var y = 0
            while (y + cropSize <= h) {
                var x = 0
                while (x + cropSize <= w) {
                    val crop = Bitmap.createBitmap(bitmap, x, y, cropSize, cropSize)
                    val r = decodeWithRotations(crop, hints)
                    crop.recycle()
                    if (r != null) return r
                    x += step
                }
                y += step
            }
        }
        return null
    }

    private fun decodeWithRotations(bitmap: Bitmap, hints: Map<DecodeHintType, *>): String? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var source: LuminanceSource = RGBLuminanceSource(w, h, pixels)
        repeat(4) { i ->
            val binary = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply { setHints(hints) }
            try {
                return reader.decodeWithState(binary).text
            } catch (e: Exception) {
                // 当前方向未识别，继续旋转
            }
            if (i < 3) source = source.rotateCounterClockwise()
        }
        return null
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val userIdentifier = prefs.getString("user_identifier", "") ?: ""
        if (userIdentifier.isNotEmpty()) {
            binding.tvUserIdentifier.setText(userIdentifier)
            binding.tvUserIdentifier.setTextColor(getColor(android.R.color.black))
        } else {
//            binding.tvUserIdentifier.setText()
            binding.tvUserIdentifier.setTextColor(getColor(android.R.color.darker_gray))
        }
        updateUuidDisplay()
    }

    private fun updateUuidDisplay() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val uuidSet = prefs.getStringSet("uuid_list", emptySet()) ?: emptySet()

        if (uuidSet.isEmpty()) {
            binding.tvUuidList.text = "未设置（将扫描所有 Beacon 设备）"
            binding.tvUuidList.setTextColor(getColor(android.R.color.darker_gray))
        } else {
            binding.tvUuidList.text = uuidSet.joinToString("\n")
            binding.tvUuidList.setTextColor(getColor(android.R.color.black))
        }
    }

    private fun saveConfig() {
        val userIdentifier = binding.tvUserIdentifier.text.toString().trim()

        // 验证用户标识（必须通过扫码获取）
        if (userIdentifier.isEmpty() || userIdentifier == "未设置（请扫码获取）") {
            Toast.makeText(this, "请扫码获取/输入用户唯一标识", Toast.LENGTH_SHORT).show()
            return
        }

        // 保存配置
        getSharedPreferences("app", MODE_PRIVATE).edit()
            .putString("user_identifier", userIdentifier)
            .apply()

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}
