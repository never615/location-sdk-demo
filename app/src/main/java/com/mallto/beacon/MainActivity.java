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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.mallto.beacon.databinding.ActivityMainBinding;
import com.mallto.sdk.BeaconConfig;
import com.mallto.sdk.BeaconSDK;
import com.mallto.sdk.bean.MalltoBeacon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    public static final boolean DEBUG = true;


    //---------- 测试环境 start--------------
    //hkt 办公室 扫描上报
    public static final String PROJECT_UUID = "1000283";
    //hkt 办公室 aoa广播
//	public static final String PROJECT_UUID = "1000239";
    public static final String SERVER_DOMAIN = "https://test-easy.mall-to.com";
    //---------- 测试环境 end--------------


    //---------- 开发环境 start--------------
    //hkt 办公室 扫描上报 项目uuid
//	public static final String PROJECT_UUID = "1000239";
    //墨兔办公室项目uuid
//	public static final String PROJECT_UUID = "4012";
//	public static final String SERVER_DOMAIN = "https://integration-easy.mall-to.com";
    //---------- 开发环境 end --------------

    //hkt 办公室ibeacon uuid
    public static final String IBEACON_UUID = "FDA50693-A4E2-4FB1-AFCF-C6EB07647827";
    //墨兔办公室ibeacon uuid
//	public static final String IBEACON_UUID = "FDA50693-A4E2-4FB1-AFCF-C6EB07647826";

    private BluetoothManager bm;
    private Button bleBtn;
    private EditText etScanInterval;
    private EditText etUserName;
    private ActivityMainBinding binding;


    private String domain = SERVER_DOMAIN;
    private String uuid = "1000002";
    private String username;

    private final Adapter adapter = new Adapter();
    private View domainBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        domain = getSharedPreferences("app", 0).getString("domain", SERVER_DOMAIN);
        binding.tvDomain.setText(domain);
        binding.btnDomain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SelectDomainActivity.class);
                startActivityForResult(intent, 0);
            }
        });
        binding.btnDeviceUuid.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, UuidListActivity.class);
                startActivity(intent);
            }
        });

        uuid = getSharedPreferences("app", 0).getString("uuid", "1000002");
        binding.etUUID.setText(uuid);

        username = getSharedPreferences("app", 0).getString("username", "HKT-test99");
        binding.etUserName.setText(username);

        bleBtn = findViewById(R.id.btn_ble);
        etScanInterval = findViewById(R.id.etScanInterval);
        etUserName = findViewById(R.id.etUserName);
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
                            bleBtn.setText("blueTooth Enabled:true");
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "open bluetooth...", Toast.LENGTH_SHORT).show();
                        bm.getAdapter().enable();
                        bleBtn.setText("blueTooth Enabled:true");
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(MainActivity.this, "close bluetooth...", Toast.LENGTH_SHORT).show();
                            bm.getAdapter().disable();
                            bleBtn.setText("blueTooth Enabled:false");
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "close bluetooth...", Toast.LENGTH_SHORT).show();
                        bm.getAdapter().disable();
                        bleBtn.setText("blueTooth Enabled:false");
                    }
                }
            }
        });

        Button startBtn = findViewById(R.id.btn_start);
        RecyclerView rv = findViewById(R.id.rv);
        rv.setAdapter(adapter);
        rv.setLayoutManager(new LinearLayoutManager((this)));
        startBtn.setText("click to start");
        startBtn.setOnClickListener(v -> {
            if (!BeaconSDK.isRunning()) {
                start();
                startBtn.setText("click to stop");
            } else {
                BeaconSDK.stop();
                startBtn.setText("click to start");
            }

        });
    }

    private void start() {
        long scanInterval = 1100L;
        try {
            scanInterval = Long.parseLong(etScanInterval.getText().toString().trim());
        } catch (NumberFormatException ignored) {}
        // android 29之后无法获取IMEI
        // target android 14+, 后台扫描需要传入通知
        Notification notification = createNotification();

        Set<String> uuidSet = getSharedPreferences("app", 0).getStringSet("uuid_list", new HashSet<>());
        List<String> uuidList = new ArrayList<>(uuidSet);
        // 支持的beacon uuid
//        uuidList.add("FDA50693-A4E2-4FB1-AFCF-C6EB07647827");
        String userName = etUserName.getText().toString().trim();
        String projectUUID = binding.etUUID.getText().toString().trim();
        BeaconSDK.init(new BeaconConfig.Builder(domain, projectUUID)
                .setDebug(DEBUG)
                .setUserName(userName)
                .setScanInterval(scanInterval)
                .setDeviceUUIDList(uuidList)
                .setNotification(notification)
                .build());
        // username
        BeaconSDK.start(new BeaconSDK.Callback() {
            @Override
            public void onRangingBeacons(List<MalltoBeacon> beacons) {
                adapter.submitList(beacons);
            }

            @Override
            public void onAdvertising() {
                Toast.makeText(MainActivity.this, "advertising aoa...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "error:" + s, Toast.LENGTH_SHORT).show();
                    }
                });
            }

        });
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
        if (!BeaconScanPermissionsActivity.Companion.allPermissionsGranted(this,
                true)) {
            Intent intent = new Intent(this, BeaconScanPermissionsActivity.class);
            intent.putExtra("backgroundAccessRequested", true);
            startActivity(intent);
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            boolean blueToothEnabled = bm.getAdapter().isEnabled();
            bleBtn.setText("blueTooth Enabled:" + blueToothEnabled);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences("app", 0).edit()
                .putString("username", username)
                .putString("uuid", uuid)
                .apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                String newDomain = data.getStringExtra("domain");
                domain = newDomain;
                binding.tvDomain.setText(newDomain);
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