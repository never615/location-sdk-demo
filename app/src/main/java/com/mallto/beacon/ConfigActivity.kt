package com.mallto.beacon

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mallto.beacon.databinding.ActivityConfigBinding

class ConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConfigBinding

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scanResult = result.contents.trim()
            // 检查是否是有效的16进制字符串
            if (scanResult.matches(Regex("^[0-9a-fA-F]+$"))) {
                // 检查长度是否为3字节（6位16进制字符）
                if (scanResult.length == 6) {
                    binding.etUserIdentifier.setText(scanResult)
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
        val userIdentifier = prefs.getString("user_identifier", "01c1c9") ?: "01c1c9"
        binding.etUserIdentifier.setText(userIdentifier)
        updateUuidDisplay()
    }

    private fun updateUuidDisplay() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val uuidSet = prefs.getStringSet("uuid_list", emptySet()) ?: emptySet()

        if (uuidSet.isEmpty()) {
            binding.tvUuidList.text = "未设置（点击下方按钮添加）"
            binding.tvUuidList.setTextColor(getColor(android.R.color.darker_gray))
        } else {
            binding.tvUuidList.text = uuidSet.joinToString("\n")
            binding.tvUuidList.setTextColor(getColor(android.R.color.black))
        }
    }

    private fun saveConfig() {
        val userIdentifier = binding.etUserIdentifier.text.toString().trim()

        // 验证用户标识格式
        if (userIdentifier.isEmpty()) {
            Toast.makeText(this, "请输入用户唯一标识", Toast.LENGTH_SHORT).show()
            return
        }

        if (userIdentifier.length < 6) {
            Toast.makeText(this, "用户标识至少需要6位16进制字符", Toast.LENGTH_SHORT).show()
            return
        }

        if (!userIdentifier.matches(Regex("^[0-9a-fA-F]+$"))) {
            Toast.makeText(this, "用户标识只能包含16进制字符（0-9, a-f）", Toast.LENGTH_SHORT).show()
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
