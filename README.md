# ⚡ VelocityFetch - Advanced Download Manager

VelocityFetch adalah aplikasi antarmuka grafis (GUI) berbasis Java Desktop yang dirancang untuk mengunduh file dari internet secara efisien menggunakan antarmuka modern. Aplikasi ini dikembangkan untuk mengimplementasikan materi Stream I/O dan URLConnection pada mata kuliah Pemrograman Internet.

## ✨ Fitur Utama
* **Smart URL Input:** Dilengkapi tombol "Tempel" yang otomatis mengambil teks tautan (URL) dari *clipboard* sistem.
* **Dynamic Progress Bar:** Menampilkan persentase perkembangan unduhan dan kalkulasi ukuran file (dalam satuan MB) secara *real-time*.
* **Tabel Riwayat Interaktif:** Mencatat informasi detail setiap unduhan (URL, Nama File, Ukuran, Jenis File, dan Status). Baris yang diklik akan otomatis mengisi ulang kolom URL.
* **Aksi Cepat (Quick Actions):** Integrasi sistem untuk langsung membuka file hasil unduhan, membuka folder penyimpanan (*File Explorer*), atau menghapus riwayat dari tabel.
* **Auto-Extract ZIP:** Mendeteksi file berformat `.zip` secara otomatis dan menyediakan opsi untuk mengekstrak isi file ke dalam folder tujuan (*Directory*).

## 💻 Detail Implementasi Teknis
* **GUI:** Java Swing (`javax.swing.*`) dengan penyesuaian tema sistem operasi lokal.
* **Jaringan:** `java.net.URL` dan `URLConnection` untuk membuka komunikasi HTTP dan mengambil metadata (ukuran file).
* **Stream I/O:** `BufferedInputStream` dan `BufferedOutputStream` untuk mempercepat proses baca-tulis (*read/write*) file lokal.
* **Proses Latar Belakang:** Menggunakan blok `Thread` untuk memisahkan proses *Stream* dari *Event Dispatch Thread* utama sehingga antarmuka tidak *freeze*.

## 🚀 Cara Menjalankan Program
1. Pastikan **Java Development Kit (JDK)** sudah terinstal di komputer Anda.
2. Buka Terminal atau Command Prompt, lalu navigasikan ke folder tempat proyek ini disimpan.
3. Jalankan perintah kompilasi berikut:
   ```bash
   javac AplikasiDownloadFile.java