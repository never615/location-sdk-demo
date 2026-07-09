package com.mallto.beacon

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
            val scanResult = result.contents.trim()
            // 检查是否是有效的16进制字符串
            if (scanResult.matches(Regex("^[0-9a-fA-F]+$"))) {
                // 检查长度是否为3字节（6位16进制字符）
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
