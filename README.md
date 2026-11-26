# NyieunCV — Android (APK)

Aplikasi pembuat CV 100% offline. Tampilan memakai `app/src/main/assets/index.html`
yang dibungkus WebView.

## Ciri keamanan
- **Nol izin** di manifest (tanpa INTERNET, kamera, penyimpanan, kontak, dll.).
- Konten dimuat dari aset lokal via `WebViewAssetLoader`; semua permintaan `http/https`
  lain diblokir, navigasi keluar aplikasi ditolak, akses `file://` dimatikan.
- Content-Security-Policy ketat di HTML (`connect-src 'none'`, tanpa skrip eksternal).
- `allowBackup=false`, cleartext traffic dimatikan.
- Jembatan JavaScript hanya menerima panggilan dari halaman lokal dan hanya menyediakan
  dua fungsi: cetak/simpan PDF dan simpan berkas JSON.

## Fitur yang disesuaikan untuk Android
- **Unduh PDF** → dialog cetak sistem (pilih *Simpan sebagai PDF*), ukuran A4 tanpa margin.
- **Ekspor** → dialog "Simpan sebagai" sistem (pilih lokasi sendiri).
- **Impor / Foto** → pemilih berkas sistem (tanpa izin penyimpanan).
- Tombol Kembali menutup dialog/galeri template; tekan dua kali untuk keluar.
- Data disimpan otomatis dan di-flush saat aplikasi masuk latar belakang.

## Memasang
Salin APK ke ponsel, buka, dan izinkan "Pasang dari sumber ini" untuk aplikasi
file manager/browser Anda bila diminta. Minimal Android 7.0 (API 24).

## Catatan
- Data CV tersimpan di penyimpanan internal aplikasi dan **tidak ikut cadangan otomatis**;
  gunakan tombol **Ekspor** untuk cadangan.
- Menghapus aplikasi/data aplikasi menghapus CV tersimpan.
