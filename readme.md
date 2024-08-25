# Beacon sdk 

## dependencies
```gradle
implementation("org.altbeacon:android-beacon-library:2.20.6")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.google.code.gson:gson:2.11.0")
```
## init

```java
List<String> uuidList = new ArrayList<>();
// 支持的beacon uuid
uuidList.add("FDA50693-A4E2-4FB1-AFCF-C6EB07647827");
BeaconSDK.init(new BeaconConfig.Builder()
        .setDebug(true) // 测试/正式环境
        .setUserId("001") // 用户id，imei在android新版本无法获取
        .setDeviceUUIDList(uuidList)
        .setNotification(notification) // target android 14+, 后台扫描需要传入通知
        .setScanInterval(1100L)  // 设置扫描频率，1100ms一次
        .build());


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
```

## 开启服务
```java
BeaconSDK.start(new BeaconSDK.Callback() {
    @Override
    public void onRangingBeacons(List<MalltoBeacon> beacons) {
        // 上报的beacon信息
        adapter.submitList(beacons);
    }

    @Override
    public void onAdvertising() {
        // 检测不到beacon时，发送AOA
        Toast.makeText(MainActivity.this, "advertising aoa...", Toast.LENGTH_SHORT).show();
    }
});
```