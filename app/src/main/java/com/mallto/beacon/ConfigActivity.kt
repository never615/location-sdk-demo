package com.mallto.beacon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
        private const val RESET_PASSWORD = "mallto2026"

        private const val PREF_ANDROID_ID_OFFSET = "android_id_offset"
        // android_id 为 8 字节，截取 3 字节，偏移范围 0..5
        private const val ANDROID_ID_EXTRACT_SIZE = 3
        private const val ANDROID_ID_BYTES = 8
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
                displayIdentifiers(scanResult)
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

        binding.btnResetAndroidIdOffset.setOnClickListener {
            showResetPasswordDialog()
        }

        binding.btnSave.setOnClickListener {
            saveConfig()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun showResetPasswordDialog() {
        val passwordInput = EditText(this).apply {
            hint = "请输入密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }
        val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
        val inputContainer = FrameLayout(this).apply {
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(
                passwordInput,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("重置验证")
            .setView(inputContainer)
            .setNegativeButton("取消", null)
            .setPositiveButton("确认", null)
            .create()

        dialog.setOnShowListener {
            val submitPassword = {
                if (passwordInput.text.toString() == RESET_PASSWORD) {
                    dialog.dismiss()
                    generateFromAndroidId(advance = true)
                    Toast.makeText(this, "已重置密码", Toast.LENGTH_SHORT).show()
                } else {
                    passwordInput.error = "密码错误"
                    passwordInput.selectAll()
                }
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                submitPassword()
            }
            passwordInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submitPassword()
                    true
                } else {
                    false
                }
            }
            passwordInput.requestFocus()
        }
        dialog.show()
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

    /**
     * 从 android_id 截取 3 字节生成用户标识。
     * @param advance true 表示点击「重置截取」，偏移 +1（到达上限后循环回到 0）后重新生成；
     *                false 表示使用当前已保存的偏移生成。
     */
    private fun generateFromAndroidId(advance: Boolean) {
        val rawId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        if (rawId.isNullOrBlank()) {
            Toast.makeText(this, "无法获取 Android ID", Toast.LENGTH_SHORT).show()
            return
        }
        // 规范化为 16 位 hex（8 字节），不足则左侧补 0
        val hex = rawId.trim().lowercase().replace(Regex("[^0-9a-f]"), "")
            .padStart(ANDROID_ID_BYTES * 2, '0')
        if (hex.length < ANDROID_ID_BYTES * 2) {
            Toast.makeText(this, "Android ID 格式异常：$rawId", Toast.LENGTH_LONG).show()
            return
        }

        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        var offset = prefs.getInt(PREF_ANDROID_ID_OFFSET, 0)
        if (advance) {
            offset = (offset + 1) % (ANDROID_ID_BYTES - ANDROID_ID_EXTRACT_SIZE + 1)
        }
        // 截取 offset 处开始的 3 字节（6 hex 字符）
        val extracted = hex.substring(offset * 2, (offset + ANDROID_ID_EXTRACT_SIZE) * 2)

        prefs.edit().putInt(PREF_ANDROID_ID_OFFSET, offset).apply()

        displayIdentifiers(extracted)
    }

    private fun displayIdentifiers(broadcastIdentifier: String) {
        binding.tvBroadcastIdentifier.setText(broadcastIdentifier)
        binding.tvBroadcastIdentifier.setTextColor(getColor(android.R.color.black))
        displayDecimalIdentifier(broadcastIdentifier)
    }

    private fun displayDecimalIdentifier(broadcastIdentifier: String) {
        val decimalIdentifier = broadcastIdentifier
            .takeIf { it.matches(Regex("^[0-9a-fA-F]{6}$")) }
            ?.toLong(16)

        binding.tvUserIdentifier.text = decimalIdentifier?.toString().orEmpty()
        binding.tvUserIdentifier.setTextColor(
            getColor(
                if (decimalIdentifier == null) {
                    android.R.color.darker_gray
                } else {
                    android.R.color.black
                }
            )
        )
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
            displayIdentifiers(userIdentifier)
        } else {
            // 未配置时，自动从 Android ID 截取生成
            generateFromAndroidId(advance = false)
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
        val userIdentifier = binding.tvBroadcastIdentifier.text.toString().trim()

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
