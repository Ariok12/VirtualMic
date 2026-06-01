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

## 🛠️ How It Works

1. **PC Receiver**: Ensure you have a UDP receiver app running on your PC listening on **Port 8765**.
2. **Connectivity**: Make sure your Phone and PC are on the same Wi-Fi network.
3. **Setup**:
   - Open the app and enter your PC's local IP address.
   - Grant the necessary permissions (Record Audio, Notification, and Media Projection if required).
4. **Stream**: Select your desired mode and hit the button!

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
