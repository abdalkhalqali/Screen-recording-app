# Android Media Projection Sample with Video Recording

Proyek ini telah diupgrade untuk menambahkan kemampuan **menyimpan video recording** yang bisa
di-download oleh user!

## Fitur Utama

### 🎥 Screen Recording
- **Preview Screen**: Menampilkan screen capture di SurfaceView
- **Video Recording**: Merekam layar dengan audio ke file MP4
- **Auto File Management**: File disimpan otomatis di direktori app
- **Recording Status**: Indikator real-time saat merekam

### 📱 Video Management
- **Video List**: Menampilkan semua video yang sudah direkam
- **File Info**: Nama file, ukuran, dan tanggal pembuatan
- **Play Video**: Buka video dengan aplikasi video player
- **Share Video**: Bagikan video ke aplikasi lain
- **Auto Refresh**: Update list video otomatis
- **Empty State**: Pesan ketika belum ada video

### 🔧 Technical Features
- **Modern UI**: Material Design 3 dengan CardView
- **File Provider**: Aman berbagi file dengan aplikasi lain
- **Permissions**: Otomatis request permission yang diperlukan
- **Android 14 Support**: Kompatibel dengan semua versi Android
- **Error Handling**: Comprehensive error handling dan user feedback

## Cara Penggunaan

1. **Start Preview**: Tekan "Start Preview" untuk mulai screen capture
2. **Grant Permission**: Izinkan screen recording permission
3. **Start Recording**: Tekan "Start Recording" untuk mulai merekam
4. **Stop Recording**: Tekan "Stop Recording" untuk berhenti
5. **Manage Videos**: Lihat, play, dan share video dari list di bawah

## Direktori Penyimpanan

Video disimpan di: `/Android/data/com.jgeraldo.mediaprojectionsample/files/Movies/ScreenRecordings/`

Format file: `ScreenRecord_YYYYMMDD_HHMMSS.mp4`

## Android 14+ Compatibility

Aplikasi ini telah dioptimalkan untuk Android 14+ dengan:

### ✅ Permission Handling

- Auto-request `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission
- Fallback mechanism untuk device yang tidak mendukung
- Graceful error handling untuk permission issues

### ✅ Service Management

- Smart foreground service handling
- Alternative direct MediaProjection approach untuk Android 14+
- Proper notification channel setup

### ✅ Error Recovery

- Multiple fallback approaches untuk berbagai Android versions
- User-friendly error messages
- Automatic service cleanup pada failure

## Permissions

Aplikasi membutuhkan permission berikut:
- `FOREGROUND_SERVICE` - Untuk media projection service
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - Media projection di Android 14+
- `RECORD_AUDIO` - Untuk merekam audio
- `WRITE_EXTERNAL_STORAGE` - Menulis file (Android ≤ 9)
- `READ_EXTERNAL_STORAGE` - Membaca file (Android ≤ 12)
- `READ_MEDIA_VIDEO` - Akses video (Android 13+)

## Troubleshooting

### Error: "Starting FGS with type mediaProjection requires permissions"

**Solusi**:

1. Aplikasi akan otomatis request permission yang diperlukan
2. Jika masih error, coba restart aplikasi setelah memberikan permission
3. Pada beberapa device Android 14+, gunakan approach direct tanpa service

### Error: "Unable to start screen recording"

**Solusi**:

1. Pastikan permission screen recording sudah diberikan
2. Restart aplikasi dan coba lagi
3. Reboot device jika masalah persist

### Video tidak bisa diplay

**Solusi**:

1. Install video player yang mendukung H.264/MP4
2. Check apakah file video tidak corrupt (size > 0)
3. Coba share ke aplikasi lain untuk memverifikasi

## Technical Implementation

### Komponen Utama:
1. **MainActivity** - UI controller dengan recording management
2. **VideoRecorder** - Handles MediaRecorder dan VirtualDisplay
3. **VideoAdapter** - RecyclerView adapter untuk video list
4. **VideoItem** - Model class untuk video file info
5. **MyMediaProjectionService** - Foreground service untuk Android 14+

### Arsitektur:
- **Media Projection API** untuk screen capture
- **MediaRecorder** untuk video recording
- **FileProvider** untuk secure file sharing
- **RecyclerView** untuk video list
- **BroadcastReceiver** untuk komunikasi service-activity

### Android Version Support:

- **Android 5.0 (API 21)** - Minimum supported version
- **Android 10-13 (API 29-33)** - Foreground service approach
- **Android 14+ (API 34+)** - Direct approach dengan fallback ke service

## Build Information

- **Target SDK**: 33 (untuk maksimum compatibility)
- **Compile SDK**: 34
- **Min SDK**: 21
- **Build Status**: ✅ SUCCESS

## Original Documentation

The purpose of this project is to do the Google's job when it comes to update their samples accordingly with their docs/releases.

We have an official MediaProjection sample available through the AndroidStudio indexer, but it doesn't show how to use it in the newer Android 14 (and its security mechanisms changes).

**Here is a resume of what you need to do:**

1. Call the ```MediaProjectionManager::createScreenCaptureIntent()``` method to start the process.
2. Launch the retrieved intent and capture its result (code, data, etc).
3. If the build version is prior **Android 14**: just get the ```MediaProjection``` instance from the ```MediaProjectionManager``` service using the intent data and create the virtual display to start everything.
3.1 Otherwise, you need to **start a foreground service** to be able to use the feature and only after it started, you can get the ```MediaProjection``` instance (if you try it before, a *IllegalStateException* will be thrown).
4. (optional) In this sample, I show the result in the only activity available, so since I need access to its views, I used a ```BroadcastReceiver``` to communicate the activity the exact moment that the service started, to avoid race conditions.