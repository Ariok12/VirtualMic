# Virtual Mic Android

![Virtual Mic Icon](app/src/main/ic_launcher-playstore.png)

**Virtual Mic** is a high-performance Android application designed to stream your device's audio (Microphone, System Media, or Both) directly to your PC over Wi-Fi with minimal latency.

## 🚀 Features

- **Triple Streaming Modes**:
  - 🎤 **Mic Only**: Use your phone as a high-quality wireless microphone for your PC.
  - 🎵 **Media Only**: Stream system audio (YouTube, Music, Games) directly to your PC.
  - 🔄 **Both**: Stream both your voice and system audio simultaneously.
- **Low Latency**: Optimized UDP-based streaming with `WIFI_MODE_FULL_LOW_LATENCY` support.
- **Background Reliable**: Uses Foreground Services and WakeLocks to ensure streaming doesn't cut out when the screen is off.
- **Modern UI**: Built with Jetpack Compose, featuring Material 3 design and Dark Mode support.
- **Battery Optimization Aware**: Built-in prompts to help you exclude the app from battery restrictions for stable long-term streaming.

## 📦 Installation & Setup

### 1. PC Receiver (Server)
You must have the receiver app running on your Windows PC to hear the audio.
- Download **`Virtualmic_server.exe.zip`** from the [Latest Releases](https://github.com/Ariok12/VirtualMic/releases/latest).
- Extract and run the `.exe` file.
- It will listen on **UDP Port 8765**. Ensure your Windows Firewall allows this traffic.

### 2. Android App (Client)
- Download the latest **`.apk`** from the [Releases](https://github.com/Ariok12/VirtualMic/releases) page.
- Install it on your Android device (ensure "Install from unknown sources" is enabled).

## 🛠️ How To Use

1. **Connectivity**: Ensure your Phone and PC are on the same Wi-Fi network.
2. **IP Address**: Open the PC server to see your local IP (e.g., `192.168.1.5`).
3. **App Setup**:
   - Open the Android app and enter that IP address.
   - Grant necessary permissions (Audio, Notifications, Media Projection).
4. **Stream**: Select your mode and click Start!

## 📡 Technical Details

- **Protocol**: UDP (User Datagram Protocol)
- **Port**: 8765
- **Audio Format**: PCM 16-bit
- **Sample Rate**: 44.1kHz / 48kHz (Automatic/Selectable)
- **Minimum SDK**: Android 10 (API 29) - Required for System Audio Capture.

## 📸 Permissions Required

- `RECORD_AUDIO`: To capture microphone input.
- `FOREGROUND_SERVICE`: To maintain the connection in the background.
- `MEDIA_PROJECTION`: Required for capturing system/media audio.
- `POST_NOTIFICATIONS`: To show the persistent control notification.

## 🤝 Contributing

Feel free to fork this project and submit pull requests. For major changes, please open an issue first to discuss what you would like to change.

---

*Developed by Ariok12*
