# TV Receiver

Android TV 电视投屏接收端：
- 局域网发布可发现服务（NSD/mDNS）
- 接收手机端发送的视频地址并播放
- 支持遥控器：暂停/继续、快进快退、倍速菜单
- 支持全屏播放（返回键退出全屏）
- 支持手机端远程控制（`/control`）

## 远程控制接口
- `POST /cast`
  - body: `{ "url": "..." }`
  - 作用：开始播放指定视频链接

- `POST /control`
  - body: `{ "action": "play|pause|stop" }`
  - 作用：播放控制（继续/暂停/退出播放）

## 环境要求
- Android Studio
- JDK 17
- Android SDK 34

## 运行
```bash
./gradlew assembleDebug
```

## Release APK
路径：
- `app/build/outputs/apk/release/app-release.apk`

## 配套项目
手机发送端仓库：
- `https://github.com/qq244901796/mobile-sender`
