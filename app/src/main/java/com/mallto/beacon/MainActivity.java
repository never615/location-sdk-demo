package com.mallto.beacon;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.mallto.beacon.databinding.ActivityMainBinding;
import com.mallto.sdk.BeaconConfig;
import com.mallto.sdk.BeaconSDK;
import com.mallto.sdk.bean.MalltoBeacon;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    public static final boolean DEBUG = true;

    //hkt 办公室ibeacon uuid
    public static final String IBEACON_UUID = "FDA50693-A4E2-4FB1-AFCF-C6EB07647827";
    //墨兔办公室ibeacon uuid
//	public static final String IBEACON_UUID = "FDA50693-A4E2-4FB1-AFCF-C6EB07647826";

    private BluetoothManager bm;
    private Button bleBtn;
    private ActivityMainBinding binding;

    private final Adapter adapter = new Adapter();
    private static final int REQUEST_CONFIG = 100;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private boolean autoStartAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UserIdentifierStore.ensureGenerated(this);
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), new OnApplyWindowInsetsListener(){

            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                Insets inset = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(inset.left, inset.top, inset.right, inset.bottom);
                return WindowInsetsCompat.CONSUMED;
            }

        });
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ConfigActivity.class);
                startActivityForResult(intent, REQUEST_CONFIG);
            }
        });

        bleBtn = findViewById(R.id.btn_ble);
        bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "need BlueTooth Permission", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean enabled = bm.getAdapter().isEnabled();
                if (!enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(MainActivity.this, "open bluetooth...", Toast.LENGTH_SHORT).show();
                            bm.getAdapter().enable();
                            bleBtn.setText("蓝牙已开启");
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "open bluetooth...", Toast.LENGTH_SHORT).show();
                        bm.getAdapter().enable();
                        bleBtn.setText("蓝牙已开启");
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(MainActivity.this, "close bluetooth...", Toast.LENGTH_SHORT).show();
                            bm.getAdapter().disable();
                            bleBtn.setText("蓝牙已关闭");
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "close bluetooth...", Toast.LENGTH_SHORT).show();
                        bm.getAdapter().disable();
                        bleBtn.setText("蓝牙已关闭");
                    }
                }
            }
        });

        RecyclerView rv = findViewById(R.id.rv);
        rv.setAdapter(adapter);
        rv.setLayoutManager(new LinearLayoutManager((this)));
    }

    private boolean startScanning() {
        String userIdentifier = getSharedPreferences("app", 0).getString("user_identifier", "");
        if (userIdentifier.isEmpty() || userIdentifier.length() < 6) {
            Toast.makeText(this, "请先配置用户唯一标识", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, ConfigActivity.class);
            startActivityForResult(intent, REQUEST_CONFIG);
            return false;
        }

        // target android 14+, 后台扫描需要传入通知
        Notification notification = createNotification();

        Set<String> uuidSet = getSharedPreferences("app", 0)
                .getStringSet("uuid_list", UuidListActivity.DEFAULT_UUIDS);
        List<String> uuidList = new ArrayList<>(uuidSet);
        // 支持的beacon uuid
//        uuidList.add("FDA50693-A4E2-4FB1-AFCF-C6EB07647827");
        BeaconSDK.init(new BeaconConfig.Builder()
                .setDebug(DEBUG)
                .setUserIdentifier(userIdentifier)
                .setDeviceUUIDList(uuidList)
                .setNotification(notification)
                .build());
        BeaconSDK.start(new BeaconSDK.Callback() {
            @Override
            public void onRangingBeacons(List<MalltoBeacon> beacons) {
                adapter.submitList(beacons);
            }

            @Override
            public void onAdvertising(int type, byte[] rawData) {
                Log.d("MainActivity", "onAdvertising:" + type);
                runOnUiThread(() -> {
                    binding.cardAdvertising.setVisibility(View.VISIBLE);
                    String time = sdf.format(new Date());
                    String hex = bytesToHex(rawData);
                    if (type == BeaconSDK.AdvertisingType.AOA) {
                        binding.tvAoaTime.setText(time);
                        binding.tvAoaRaw.setText(hex);
                    } else if (type == BeaconSDK.AdvertisingType.BEACON_FORWARD) {
                        binding.tvForwardTime.setText(time);
                        binding.tvForwardRaw.setText(hex);
                    }
                });
            }


            @Override
            public void onError(String s) {
                Toast.makeText(MainActivity.this, "error:" + s, Toast.LENGTH_LONG).show();
            }

        });
        return true;
    }

    private void autoStartScanning() {
        if (BeaconSDK.isRunning()) {
            autoStartAttempted = true;
            return;
        }
        if (!autoStartAttempted) {
            autoStartAttempted = true;
            startScanning();
        }
    }

    private boolean restartScanning() {
        if (BeaconSDK.isRunning()) {
            BeaconSDK.stop();
        }
        autoStartAttempted = true;
        return startScanning();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "--";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString().trim();
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel("beaconSDK",
                    "beaconSDK", NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            service.createNotificationChannel(chan);
        }
        return new NotificationCompat.Builder(this, "beaconSDK")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("scanning for beacons")
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean permissionsGranted = BeaconScanPermissionsActivity.Companion
                .allPermissionsGranted(this, true);
        if (!permissionsGranted) {
            Intent intent = new Intent(this, BeaconScanPermissionsActivity.class);
            intent.putExtra("backgroundAccessRequested", true);
            startActivity(intent);
            return;
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            boolean blueToothEnabled = bm.getAdapter().isEnabled();
            bleBtn.setText("蓝牙已" + (blueToothEnabled?"开启":"关闭"));
        }
        autoStartScanning();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIG && resultCode == RESULT_OK) {
            if (restartScanning()) {
                Toast.makeText(this, "配置已更新，扫描已重启", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "配置已更新", Toast.LENGTH_SHORT).show();
            }
        }
    }

    static class Adapter extends ListAdapter<MalltoBeacon, Holder> {

        protected Adapter() {
            super(new DiffUtil.ItemCallback<MalltoBeacon>() {
                @Override
                public boolean areItemsTheSame(@NonNull MalltoBeacon oldItem, @NonNull MalltoBeacon newItem) {
                    return false;
                }

                @Override
                public boolean areContentsTheSame(@NonNull MalltoBeacon oldItem, @NonNull MalltoBeacon newItem) {
                    return false;
                }
            });
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MalltoBeacon item = getItem(position);
            holder.tv1.setText(item.getUuid());
            holder.tv2.setText("major:" + item.getMajor() + ",minor=" + item.getMinor() + ",rssi:" + item.getRssi());
        }
    }

    static class Holder extends RecyclerView.ViewHolder {

        TextView tv1;
        TextView tv2;

        public Holder(@NonNull View itemView) {
            super(itemView);
            tv1 = itemView.findViewById(android.R.id.text1);
            tv2 = itemView.findViewById(android.R.id.text2);
        }
    }

}
