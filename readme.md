# Beacon sdk

## demo下载

[https://github.com/never615/location-sdk-demo/raw/refs/heads/master/apk/app-debug.apk](https://github.com/never615/location-sdk-demo/raw/refs/heads/master/apk/app-debug.apk)

## dependencies
```gradle
// copy beaconsdk-release.aar to app/libs/
implementation(files("./libs/beaconsdk-release.aar"))

implementation("org.altbeacon:android-beacon-library:2.20.6")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.google.code.gson:gson:2.11.0")
```
定位库: app/libs/beaconsdk-release.aar

## init

```java
List<String> uuidList = new ArrayList<>();
// 支持的beacon uuid
uuidList.add("FDA50693-A4E2-4FB1-AFCF-C6EB07647827");
//SERVER_DOMAIN 墨兔测试环境 https://test-easy.mall-to.com PROJECT_ID: hkt office 使用 1000283
BeaconSDK.init(new BeaconConfig.Builder(SERVER_DOMAIN, PROJECT_ID)
        .setDebug(true) //是否调试
        .setDeviceUUIDList(uuidList)
        .setUserName("001")
        .setNotification(notification) // target android 14+, 后台扫描需要传入通知
        .setScanInterval(1100L)  // 设置扫描频率，1100ms一次
        .setMode(BeaconConfig.Mode.AUTO) // 设置类型 AOA：仅广播；SCAN：仅扫描；AUTO：自动切换
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
// 有两种模式：
// 1.beacon扫描上报模式 
// 2.AOA广播模式
// 调用后会持续扫描beacon，如扫描到数据，则执行扫描上报模式的逻辑，停止AOA广播（如之前有因下面的原因自动切换到AOA模式）；
// 若连续超过10秒未扫描到beacon数据，切换AOA广播模式
BeaconSDK.start(new BeaconSDK.Callback() {
    @Override
    public void onRangingBeacons(List<MalltoBeacon> beacons) {
        // 上报的beacon信息
        // adapter.submitList(beacons);
    }

    @Override
    public void onAdvertising() {
        // 检测不到beacon时，发送AOA
        Toast.makeText(MainActivity.this, "advertising aoa...", Toast.LENGTH_SHORT).show();
    }
});
```

## 切换用户，更新username
```java
BeaconSDK.updateBLEInfo("004");
```

## 解绑用户,用户登出的时候需要调用
```java
BeaconSDK.updateBLEInfo("");
```

### 4.关闭服务
```java
BeaconSDK.stop();
```
## FAQ
1. 请求slug失败
   一般为网络连接原因，请确认系统未禁止app联网权限，确保网络通畅。如服务端使用 http 部署，需在 AndroidManifest.xml 中添加如下配置：
    ```
   <application
   android:usesCleartextTraffic="true"
    ...
   >
   ```