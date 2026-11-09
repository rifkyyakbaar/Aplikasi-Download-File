import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;

public class AplikasiDownloadFile extends JFrame {

    private static final Color BG        = new Color(243, 244, 246);
    private static final Color CARD      = Color.WHITE;
    private static final Color PRIMARY   = new Color(37, 99, 235);
    private static final Color PRIMARY_H = new Color(29, 78, 216);
    private static final Color ACCENT    = new Color(99, 102, 241);
    private static final Color TEXT      = new Color(17, 24, 39);
    private static final Color MUTED     = new Color(107, 114, 128);
    private static final Color LINE      = new Color(229, 231, 235);
    private static final Color ZEBRA     = new Color(249, 250, 251);
    private static final Color TRACK     = new Color(226, 232, 240);
    private static final Color DANGER    = new Color(220, 38, 38);
    private static final Color SUCCESS   = new Color(39, 174, 96);
    private static final Color WARNING   = new Color(217, 119, 6);

    /** Ukuran buffer baca-tulis (8 KB, lebih efisien daripada 1 KB). */
    private static final int UKURAN_BUFFER = 8192;
    /** Batas waktu menunggu server (milidetik). */
    private static final int TIMEOUT_KONEKSI = 15_000;
    private static final int TIMEOUT_BACA    = 30_000;

    /** Folder tempat file disimpan (folder kerja program). */
    private static final File FOLDER_DOWNLOAD = new File(System.getProperty("user.dir"));

    private JTextField txtUrl;
    private JButton btnDownload, btnTempel, btnBukaFile, btnBukaFolder, btnHapus;
    private JProgressBar progressBar;
    private JLabel lblStatus;

    private JTable tableHistory;
    private DefaultTableModel tableModel;

    private JLabel lblStatusKiri, lblStatusKanan;

    // ---- Status download (dibaca/ditulis dari dua thread, jadi pakai volatile) ----
    /** true selama ada download yang berjalan. */
    private volatile boolean sedangDownload = false;
    /** Diset true oleh tombol "Batalkan"; dicek oleh thread download di setiap putaran loop. */
    private volatile boolean dibatalkan = false;
    /** Koneksi yang sedang aktif, supaya bisa diputus paksa saat dibatalkan. */
    private volatile URLConnection koneksiAktif = null;

    /** Exception khusus untuk membedakan "dibatalkan pengguna" dari error biasa. */
    private static class DownloadDibatalkanException extends IOException {
        private static final long serialVersionUID = 1L;
        DownloadDibatalkanException() { super("Download dibatalkan oleh pengguna"); }
    }

    public AplikasiDownloadFile() {
        setTitle("VelocityFetch - Advanced Download Manager");
        setSize(900, 620);
        setMinimumSize(new Dimension(780, 520));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        setContentPane(root);

        Font fontUtama = new Font("Segoe UI", Font.PLAIN, 14);
        Font fontBold = new Font("Segoe UI", Font.BOLD, 14);

        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(30, 64, 175), getWidth(), 0, new Color(99, 102, 241)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setBorder(BorderFactory.createEmptyBorder(18, 25, 18, 25));
        JLabel lblJudul = new JLabel("VelocityFetch");
        lblJudul.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblJudul.setForeground(Color.WHITE);
        JLabel lblSub = new JLabel("Advanced Download Manager");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(new Color(219, 234, 254));
        JPanel boxJudul = new JPanel(new GridLayout(2, 1, 0, 2));
        boxJudul.setOpaque(false);
        boxJudul.add(lblJudul);
        boxJudul.add(lblSub);
        header.add(boxJudul, BorderLayout.WEST);

        JPanel panelInput = new JPanel(new BorderLayout(10, 8));
        panelInput.setOpaque(false);
        JLabel lblUrl = new JLabel("Masukkan URL");
        lblUrl.setFont(fontBold);
        lblUrl.setForeground(TEXT);
        txtUrl = new JTextField();
        txtUrl.setFont(fontUtama);
        txtUrl.setForeground(TEXT);
        txtUrl.setCaretColor(PRIMARY);
        txtUrl.setBorder(new RoundBorder(LINE));
        txtUrl.setPreferredSize(new Dimension(100, 42));
        txtUrl.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { txtUrl.setBorder(new RoundBorder(PRIMARY)); }
            @Override public void focusLost(FocusEvent e)   { txtUrl.setBorder(new RoundBorder(LINE)); }
        });
        // Tekan Enter di kolom URL = mulai download
        txtUrl.addActionListener(e -> { if (!sedangDownload) mulaiDownload(); });

        btnTempel = new RoundedButton("Tempel", Color.WHITE, new Color(239, 246, 255), PRIMARY, PRIMARY);
        btnTempel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnTempel.setPreferredSize(new Dimension(95, 42));

        btnTempel.addActionListener(e -> {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable contents = clipboard.getContents(null);
                if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String pasteString = (String) contents.getTransferData(DataFlavor.stringFlavor);
                    txtUrl.setText(pasteString.trim());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gagal menempelkan teks!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panelInput.add(lblUrl, BorderLayout.NORTH);
        panelInput.add(txtUrl, BorderLayout.CENTER);
        panelInput.add(btnTempel, BorderLayout.EAST);

        JPanel panelProgress = new JPanel(new BorderLayout(5, 8));
        panelProgress.setOpaque(false);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setBorderPainted(false);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        progressBar.setPreferredSize(new Dimension(100, 28));
        progressBar.setUI(new ModernProgressBarUI());

        lblStatus = new JLabel("Status: Menunggu input URL...", SwingConstants.LEFT);
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblStatus.setForeground(SUCCESS);

        panelProgress.add(progressBar, BorderLayout.CENTER);
        panelProgress.add(lblStatus, BorderLayout.SOUTH);

        JPanel panelAtas = new JPanel(new BorderLayout(10, 16));
        panelAtas.setOpaque(false);
        panelAtas.add(panelInput, BorderLayout.NORTH);
        panelAtas.add(panelProgress, BorderLayout.CENTER);

        RoundedPanel cardAtas = new RoundedPanel(new BorderLayout());
        cardAtas.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        cardAtas.add(panelAtas, BorderLayout.CENTER);

        String[] namaKolom = {"URL", "Nama File", "Ukuran", "Jenis File", "Status"};
        tableModel = new DefaultTableModel(namaKolom, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tableHistory = new JTable(tableModel);
        tableHistory.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tableHistory.setRowHeight(34);
        tableHistory.setSelectionBackground(PRIMARY);
        tableHistory.setSelectionForeground(Color.WHITE);
        tableHistory.setShowGrid(false);
        tableHistory.setShowVerticalLines(false);
        tableHistory.setShowHorizontalLines(false);
        tableHistory.setIntercellSpacing(new Dimension(0, 0));
        tableHistory.setFillsViewportHeight(true);
        tableHistory.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader th = tableHistory.getTableHeader();
        th.setReorderingAllowed(false);
        th.setPreferredSize(new Dimension(100, 38));
        th.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
                l.setOpaque(true);
                l.setBackground(ZEBRA);
                l.setForeground(MUTED);
                l.setFont(new Font("Segoe UI", Font.BOLD, 12));
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                        BorderFactory.createEmptyBorder(0, 12, 0, 12)));
                l.setHorizontalAlignment(column >= 2 ? SwingConstants.CENTER : SwingConstants.LEFT);
                return l;
            }
        });

        tableHistory.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                l.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                l.setHorizontalAlignment(column >= 2 ? SwingConstants.CENTER : SwingConstants.LEFT);
                if (!isSelected) {
                    l.setBackground(row % 2 == 0 ? Color.WHITE : ZEBRA);
                    l.setForeground(TEXT);
                }
                return l;
            }
        });

        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                l.setFont(new Font("Segoe UI", Font.BOLD, 13));
                l.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                if (!isSelected) {
                    l.setBackground(row % 2 == 0 ? Color.WHITE : ZEBRA);
                    if ("Berhasil".equals(value)) {
                        l.setForeground(SUCCESS);
                    } else if ("Gagal".equals(value)) {
                        l.setForeground(DANGER);
                    } else if ("Dibatalkan".equals(value)) {
                        l.setForeground(WARNING);
                    } else {
                        l.setForeground(TEXT);
                    }
                }
                return l;
            }
        };
        tableHistory.getColumnModel().getColumn(4).setCellRenderer(statusRenderer);

        tableHistory.getColumnModel().getColumn(0).setPreferredWidth(300);
        tableHistory.getColumnModel().getColumn(1).setPreferredWidth(170);
        tableHistory.getColumnModel().getColumn(2).setPreferredWidth(90);
        tableHistory.getColumnModel().getColumn(3).setPreferredWidth(80);
        tableHistory.getColumnModel().getColumn(4).setPreferredWidth(90);

        JScrollPane scrollPane = new JScrollPane(tableHistory);
        scrollPane.setBorder(BorderFactory.createLineBorder(LINE));
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));

        tableHistory.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int baris = tableHistory.getSelectedRow();
                if (baris != -1) {
                    txtUrl.setText(tableModel.getValueAt(baris, 0).toString());
                }
            }
        });

        JLabel lblRiwayat = new JLabel("Riwayat Unduhan");
        lblRiwayat.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblRiwayat.setForeground(TEXT);
        lblRiwayat.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        RoundedPanel cardTabel = new RoundedPanel(new BorderLayout());
        cardTabel.setBorder(BorderFactory.createEmptyBorder(16, 20, 18, 20));
        cardTabel.add(lblRiwayat, BorderLayout.NORTH);
        cardTabel.add(scrollPane, BorderLayout.CENTER);

        JPanel panelTengah = new JPanel(new BorderLayout(0, 16));
        panelTengah.setOpaque(false);
        panelTengah.setBorder(BorderFactory.createEmptyBorder(20, 25, 10, 25));
        panelTengah.add(cardAtas, BorderLayout.NORTH);
        panelTengah.add(cardTabel, BorderLayout.CENTER);

        JPanel panelAksi = new JPanel(new BorderLayout(12, 10));
        panelAksi.setOpaque(false);
        panelAksi.setBorder(BorderFactory.createEmptyBorder(6, 25, 14, 25));

        btnDownload = new RoundedButton("Mulai Download", PRIMARY, PRIMARY_H, Color.WHITE, null);
        btnDownload.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnDownload.setPreferredSize(new Dimension(400, 44));
        // Satu tombol, dua fungsi: mulai download, atau batalkan kalau sedang berjalan
        btnDownload.addActionListener(e -> {
            if (sedangDownload) batalkanDownload();
            else mulaiDownload();
        });

        JPanel panelTombolKanan = new JPanel(new GridLayout(1, 3, 8, 0));
        panelTombolKanan.setOpaque(false);
        btnBukaFile = new RoundedButton("Buka File", Color.WHITE, new Color(243, 244, 246), TEXT, LINE);
        btnBukaFolder = new RoundedButton("Buka Folder", Color.WHITE, new Color(243, 244, 246), TEXT, LINE);
        btnHapus = new RoundedButton("Hapus", Color.WHITE, new Color(254, 242, 242), DANGER, new Color(252, 165, 165));

        panelTombolKanan.add(btnBukaFile);
        panelTombolKanan.add(btnBukaFolder);
        panelTombolKanan.add(btnHapus);

        panelAksi.add(btnDownload, BorderLayout.CENTER);
        panelAksi.add(panelTombolKanan, BorderLayout.EAST);

        btnBukaFolder.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(FOLDER_DOWNLOAD);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gagal membuka folder!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnBukaFile.addActionListener(e -> {
            int baris = tableHistory.getSelectedRow();
            if (baris == -1) {
                JOptionPane.showMessageDialog(this, "Pilih file di tabel terlebih dahulu!", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (!"Berhasil".equals(tableModel.getValueAt(baris, 4))) {
                JOptionPane.showMessageDialog(this, "Download untuk baris ini gagal, jadi tidak ada file yang bisa dibuka.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            File file = new File(FOLDER_DOWNLOAD, tableModel.getValueAt(baris, 1).toString());
            if (!file.exists()) {
                JOptionPane.showMessageDialog(this, "File tidak ditemukan:\n" + file.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gagal membuka file: " + pesanError(ex), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnHapus.addActionListener(e -> {
            int baris = tableHistory.getSelectedRow();
            if (baris != -1) {
                tableModel.removeRow(baris);
                updateStatusBar();
            } else {
                JOptionPane.showMessageDialog(this, "Pilih riwayat yang ingin dihapus!", "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(Color.WHITE);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                BorderFactory.createEmptyBorder(8, 25, 8, 25)));

        Font fontStatus = new Font("Segoe UI", Font.PLAIN, 12);
        lblStatusKiri = new JLabel("Siap");
        lblStatusKiri.setFont(fontStatus);
        lblStatusKiri.setForeground(MUTED);

        JPanel statusKanan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        statusKanan.setOpaque(false);
        // Label "Koneksi: Terhubung" dihapus karena hanya teks statis yang tidak pernah dicek.
        lblStatusKanan = new JLabel("Total: 0 Unduhan");
        lblStatusKanan.setFont(fontStatus);
        lblStatusKanan.setForeground(MUTED);
        statusKanan.add(lblStatusKanan);

        statusBar.add(lblStatusKiri, BorderLayout.WEST);
        statusBar.add(statusKanan, BorderLayout.EAST);

        JPanel panelBawah = new JPanel(new BorderLayout(0, 0));
        panelBawah.setOpaque(false);
        panelBawah.add(panelAksi, BorderLayout.CENTER);
        panelBawah.add(statusBar, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(panelTengah, BorderLayout.CENTER);
        add(panelBawah, BorderLayout.SOUTH);
    }

    // =====================================================================
    //                              LOGIKA DOWNLOAD
    // =====================================================================

    private void mulaiDownload() {
        String urlString = txtUrl.getText().trim();
        if (urlString.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Masukkan URL terlebih dahulu!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        sedangDownload = true;
        dibatalkan = false;
        btnDownload.setText("Batalkan Download");
        progressBar.setValue(0);
        progressBar.setString("Menghubungkan...");
        lblStatus.setText("Status: Menghubungkan ke server...");
        lblStatus.setForeground(new Color(0, 120, 215));
        lblStatusKiri.setText("Mengunduh...");

        Thread downloadThread = new Thread(() -> prosesDownloadDanEkstrak(urlString), "download-thread");
        downloadThread.setDaemon(true); // thread ikut berhenti kalau jendela ditutup
        downloadThread.start();
    }

    /** Dipanggil dari EDT saat tombol "Batalkan Download" ditekan. */
    private void batalkanDownload() {
        dibatalkan = true; // thread download akan melihat ini di putaran loop berikutnya
        btnDownload.setEnabled(false);
        btnDownload.setText("Membatalkan...");
        lblStatus.setText("Status: Membatalkan download...");
        lblStatus.setForeground(WARNING);

        // Kalau thread sedang "macet" menunggu data dari server (read() memblokir),
        // memutus koneksi akan membuat read() langsung melempar exception.
        URLConnection k = koneksiAktif;
        if (k instanceof HttpURLConnection) {
            new Thread(((HttpURLConnection) k)::disconnect, "putus-koneksi").start();
        }
    }

    /** Berjalan di thread terpisah. Semua perubahan GUI lewat SwingUtilities.invokeLater. */
    private void prosesDownloadDanEkstrak(String urlString) {
        String namaFileUntukHistory = "-";
        String ukuranUntukHistory = "Tidak diketahui";
        String jenisUntukHistory = "Unknown";
        File fileTujuan = null;
        boolean downloadSelesai = false;

        try {
            // ---------- 1. Siapkan & buka koneksi ----------
            URL url = new URI(urlString).toURL();
            URLConnection koneksi = url.openConnection();
            koneksiAktif = koneksi; // simpan supaya tombol Batalkan bisa memutusnya
            koneksi.setConnectTimeout(TIMEOUT_KONEKSI);
            koneksi.setReadTimeout(TIMEOUT_BACA);
            // Beberapa server menolak User-Agent bawaan Java, jadi kita isi sendiri
            koneksi.setRequestProperty("User-Agent", "VelocityFetch/1.0");

            // ---------- 2. Cek kode status HTTP ----------
            if (koneksi instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection) koneksi;
                int kode = http.getResponseCode(); // baris ini yang benar-benar menyambung ke server
                if (kode < 200 || kode >= 300) {
                    throw new IOException("Server membalas kode " + kode + " (" + http.getResponseMessage() + ")");
                }
            }

            if (dibatalkan) throw new DownloadDibatalkanException();

            // long, bukan int, supaya file > 2 GB tetap terbaca benar (-1 = tidak diketahui)
            long ukuranFileTotal = koneksi.getContentLengthLong();

            // ---------- 3. Tentukan nama file ----------
            // getURL() setelah tersambung = URL akhir (sesudah redirect, kalau ada)
            String namaAsli = ambilNamaFileDariUrl(koneksi.getURL());
            fileTujuan = buatFileUnik(namaAsli); // hindari menimpa file lama
            final File file = fileTujuan;
            final String namaFile = file.getName();
            namaFileUntukHistory = namaFile;

            if (namaFile.contains(".")) {
                jenisUntukHistory = namaFile.substring(namaFile.lastIndexOf('.') + 1).toUpperCase();
            }
            if (ukuranFileTotal > 0) {
                ukuranUntukHistory = formatMB(ukuranFileTotal);
            }

            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("Status: Mengunduh file " + namaFile + "...");
                if (ukuranFileTotal <= 0) progressBar.setString("Ukuran file tidak diketahui...");
            });

            // ---------- 4. Salin byte dari jaringan ke file ----------
            // try-with-resources: kedua stream PASTI ditutup, baik sukses maupun error
            long totalBytesDiterima = 0;
            try (BufferedInputStream inStream = new BufferedInputStream(koneksi.getInputStream());
                 BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file))) {

                byte[] buffer = new byte[UKURAN_BUFFER];
                int bytesRead;
                int persenTerakhir = -1;
                long waktuUpdateTerakhir = 0;

                while ((bytesRead = inStream.read(buffer)) != -1) {
                    // Cek tombol Batalkan di setiap putaran (setiap ~8 KB)
                    if (dibatalkan) throw new DownloadDibatalkanException();

                    outStream.write(buffer, 0, bytesRead);
                    totalBytesDiterima += bytesRead;

                    if (ukuranFileTotal > 0) {
                        // Update GUI hanya kalau persentase berubah (maks. 100x per file)
                        int persen = (int) Math.min(100, totalBytesDiterima * 100 / ukuranFileTotal);
                        if (persen != persenTerakhir) {
                            persenTerakhir = persen;
                            String teks = String.format("%d%% (%s / %s)", persen,
                                    formatMB(totalBytesDiterima), formatMB(ukuranFileTotal));
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(persen);
                                progressBar.setString(teks);
                            });
                        }
                    } else {
                        // Ukuran tidak diketahui: tampilkan jumlah yang sudah diterima, maks. 5x per detik
                        long sekarang = System.currentTimeMillis();
                        if (sekarang - waktuUpdateTerakhir >= 200) {
                            waktuUpdateTerakhir = sekarang;
                            String teks = formatMB(totalBytesDiterima) + " diterima";
                            SwingUtilities.invokeLater(() -> progressBar.setString(teks));
                        }
                    }
                }
            } // <- stream ditutup otomatis di sini (close() sudah termasuk flush())

            if (dibatalkan) throw new DownloadDibatalkanException();

            // Kalau server mengirim Content-Length tapi data yang diterima kurang, berarti terputus
            if (ukuranFileTotal > 0 && totalBytesDiterima < ukuranFileTotal) {
                throw new IOException("Koneksi terputus: hanya " + formatMB(totalBytesDiterima)
                        + " dari " + formatMB(ukuranFileTotal) + " yang diterima");
            }

            downloadSelesai = true;
            if (ukuranFileTotal <= 0) {
                ukuranUntukHistory = formatMB(totalBytesDiterima);
            }

            tambahKeHistory(urlString, namaFileUntukHistory, ukuranUntukHistory, jenisUntukHistory, "Berhasil");

            final String ukuranAkhir = ukuranUntukHistory;
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(100);
                progressBar.setString("100% (" + ukuranAkhir + ")");
                lblStatus.setText("Status: Selesai! Tersimpan sebagai " + namaFile);
                lblStatus.setForeground(SUCCESS);
                kembalikanTombolSemula();

                // ---------- 5. Tawarkan ekstrak ZIP (dialog di EDT) ----------
                if (namaFile.toLowerCase().endsWith(".zip")) {
                    int pilihan = JOptionPane.showConfirmDialog(this,
                            "File yang didownload berformat ZIP.\nApakah kamu ingin mengekstraknya sekarang?",
                            "Ekstrak File ZIP", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (pilihan == JOptionPane.YES_OPTION) {
                        // Ekstraksi bisa lama, jadi dijalankan di thread terpisah
                        Thread t = new Thread(() -> ekstrakZip(file), "ekstrak-thread");
                        t.setDaemon(true);
                        t.start();
                    }
                }
            });

        } catch (Exception e) {
            // Hapus file setengah jadi supaya tidak dikira file utuh
            if (!downloadSelesai && fileTujuan != null && fileTujuan.exists()) {
                fileTujuan.delete();
            }

            // Kalau pengguna menekan Batalkan, exception apa pun yang muncul
            // (termasuk SocketException karena koneksi diputus) dianggap "Dibatalkan", bukan error.
            if (dibatalkan) {
                tambahKeHistory(urlString, namaFileUntukHistory, ukuranUntukHistory, jenisUntukHistory, "Dibatalkan");
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Status: Download dibatalkan.");
                    lblStatus.setForeground(WARNING);
                    progressBar.setValue(0);
                    progressBar.setString("Dibatalkan");
                    kembalikanTombolSemula();
                });
                return;
            }

            tambahKeHistory(urlString, namaFileUntukHistory, ukuranUntukHistory, jenisUntukHistory, "Gagal");

            String pesan = pesanError(e);
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("Status: Gagal mengunduh!");
                lblStatus.setForeground(DANGER);
                progressBar.setValue(0);
                progressBar.setString("Error!");
                kembalikanTombolSemula();
                JOptionPane.showMessageDialog(this, "Gagal mendownload:\n" + pesan, "Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    /** Ambil nama file dari bagian path URL, decode %20 dsb., dan buang karakter terlarang. */
    private String ambilNamaFileDariUrl(URL url) {
        String path = url.getPath();
        String nama = path.substring(path.lastIndexOf('/') + 1);
        try {
            nama = URLDecoder.decode(nama, StandardCharsets.UTF_8.name()); // "laporan%20akhir.pdf" -> "laporan akhir.pdf"
        } catch (Exception ignored) {
            // biarkan nama apa adanya
        }
        nama = nama.replaceAll("[\\\\/:*?\"<>|]", "_").trim(); // karakter yang tidak boleh di nama file Windows
        if (nama.isEmpty() || !nama.contains(".")) {
            nama = "downloaded_file.bin";
        }
        return nama;
    }

    /** Kalau nama sudah dipakai, tambahkan nomor: "data.pdf" -> "data (1).pdf". */
    private File buatFileUnik(String nama) {
        File file = new File(FOLDER_DOWNLOAD, nama);
        if (!file.exists()) return file;

        int titik = nama.lastIndexOf('.');
        String dasar = titik > 0 ? nama.substring(0, titik) : nama;
        String ekstensi = titik > 0 ? nama.substring(titik) : "";
        int nomor = 1;
        while (file.exists()) {
            file = new File(FOLDER_DOWNLOAD, dasar + " (" + nomor++ + ")" + ekstensi);
        }
        return file;
    }

    private static String formatMB(long bytes) {
        return String.format("%.2f MB", bytes / 1048576.0);
    }

    private static String pesanError(Exception e) {
        if (e instanceof UnknownHostException)    return "Server tidak ditemukan: " + e.getMessage();
        if (e instanceof SocketTimeoutException)  return "Server terlalu lama merespons (timeout).";
        if (e instanceof URISyntaxException)      return "Format URL tidak valid.";
        if (e instanceof MalformedURLException)   return "URL tidak dikenali (pastikan diawali http:// atau https://).";
        if (e instanceof IllegalArgumentException) return "URL tidak lengkap (pastikan diawali http:// atau https://).";
        String pesan = e.getMessage();
        return (pesan == null || pesan.isEmpty()) ? e.getClass().getSimpleName() : pesan;
    }

    private void tambahKeHistory(String url, String namaFile, String ukuran, String jenis, String status) {
        SwingUtilities.invokeLater(() -> {
            tableModel.addRow(new Object[]{url, namaFile, ukuran, jenis, status});
            updateStatusBar();
        });
    }

    private void updateStatusBar() {
        lblStatusKanan.setText("Total: " + tableModel.getRowCount() + " Unduhan");
    }

    /** Harus dipanggil dari EDT. */
    private void kembalikanTombolSemula() {
        sedangDownload = false;
        koneksiAktif = null;
        btnDownload.setEnabled(true);
        btnDownload.setText("Mulai Download");
        lblStatusKiri.setText("Siap");
    }

    // =====================================================================
    //                              EKSTRAK ZIP
    // =====================================================================

    /** Berjalan di thread terpisah. Semua dialog lewat SwingUtilities.invokeLater. */
    private void ekstrakZip(File fileZip) {
        String namaZip = fileZip.getName();
        File folderTarget = new File(fileZip.getParentFile(), namaZip.substring(0, namaZip.length() - 4));

        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("Status: Mengekstrak " + namaZip + "...");
            lblStatus.setForeground(new Color(0, 120, 215));
            lblStatusKiri.setText("Mengekstrak...");
        });

        try {
            if (!folderTarget.isDirectory() && !folderTarget.mkdirs()) {
                throw new IOException("Gagal membuat folder " + folderTarget.getName());
            }
            String batasAman = folderTarget.getCanonicalPath();
            int jumlahFile = 0;

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(fileZip)))) {
                byte[] buffer = new byte[UKURAN_BUFFER];
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    File fileBaru = new File(folderTarget, entry.getName());

                    // Cegah "Zip Slip": entry seperti "../../virus.exe" tidak boleh keluar dari folder target
                    String pathBaru = fileBaru.getCanonicalPath();
                    if (!pathBaru.equals(batasAman) && !pathBaru.startsWith(batasAman + File.separator)) {
                        throw new IOException("Entry ZIP mencurigakan ditolak: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        if (!fileBaru.isDirectory() && !fileBaru.mkdirs()) {
                            throw new IOException("Gagal membuat folder: " + entry.getName());
                        }
                    } else {
                        File parent = fileBaru.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) {
                            throw new IOException("Gagal membuat folder: " + parent.getName());
                        }
                        // Stream output untuk setiap file juga pakai try-with-resources
                        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileBaru))) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                bos.write(buffer, 0, len);
                            }
                        }
                        jumlahFile++;
                    }
                }
            }

            final int total = jumlahFile;
            SwingUtilities.invokeLater(() -> {
                lblStatusKiri.setText("Siap");
                if (total == 0) {
                    lblStatus.setText("Status: Tidak ada file yang diekstrak.");
                    lblStatus.setForeground(DANGER);
                    JOptionPane.showMessageDialog(this,
                            "Tidak ada file yang bisa diekstrak.\nKemungkinan file ini bukan arsip ZIP yang valid.",
                            "Info", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                lblStatus.setText("Status: " + total + " file diekstrak ke folder " + folderTarget.getName());
                lblStatus.setForeground(SUCCESS);
                JOptionPane.showMessageDialog(this,
                        "Ekstraksi berhasil (" + total + " file) ke folder:\n" + folderTarget.getAbsolutePath(),
                        "Info", JOptionPane.INFORMATION_MESSAGE);
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(folderTarget);
                    }
                } catch (IOException ignored) {
                    // folder sudah berhasil dibuat, gagal membuka jendela Explorer saja tidak masalah
                }
            });

        } catch (IOException e) {
            String pesan = pesanError(e);
            SwingUtilities.invokeLater(() -> {
                lblStatusKiri.setText("Siap");
                lblStatus.setText("Status: Gagal mengekstrak!");
                lblStatus.setForeground(DANGER);
                JOptionPane.showMessageDialog(this, "Gagal mengekstrak:\n" + pesan, "Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new AplikasiDownloadFile().setVisible(true));
    }

    // =====================================================================
    //                    KOMPONEN GUI KUSTOM (HANYA TAMPILAN)
    // =====================================================================

    /** Tombol dengan sudut membulat + efek hover/pressed. */
    private static class RoundedButton extends JButton {
        private final Color bg, hover, fg, border;
        private boolean over = false;

        RoundedButton(String text, Color bg, Color hover, Color fg, Color border) {
            super(text);
            this.bg = bg;
            this.hover = hover;
            this.fg = fg;
            this.border = border;
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { over = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            Color fill;
            if (!isEnabled())                fill = new Color(148, 163, 184);
            else if (getModel().isPressed()) fill = hover.darker();
            else if (over)                   fill = hover;
            else                             fill = bg;

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            if (border != null && isEnabled()) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(isEnabled() ? fg : Color.WHITE);
            g2.drawString(t, tx, ty);
            g2.dispose();
        }
    }

    private static class RoundedPanel extends JPanel {
        RoundedPanel(LayoutManager lm) {
            super(lm);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class RoundBorder extends AbstractBorder {
        private final Color color;
        RoundBorder(Color color) { this.color = color; }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) { return new Insets(8, 12, 8, 12); }
    }

    private static class ModernProgressBarUI extends BasicProgressBarUI {
        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = c.getWidth(), h = c.getHeight();
            g2.setColor(TRACK);
            g2.fillRoundRect(0, 0, w, h, h, h);

            int fill = (int) (w * progressBar.getPercentComplete());
            if (fill > 0) {
                g2.setPaint(new GradientPaint(0, 0, PRIMARY, w, 0, ACCENT));
                g2.fillRoundRect(0, 0, Math.max(fill, h), h, h, h);
            }

            if (progressBar.isStringPainted()) {
                String s = progressBar.getString();
                g2.setFont(progressBar.getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(s)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();

                g2.setClip(0, 0, fill, h);
                g2.setColor(Color.WHITE);
                g2.drawString(s, tx, ty);
                g2.setClip(fill, 0, w - fill, h);
                g2.setColor(TEXT);
                g2.drawString(s, tx, ty);
            }
            g2.dispose();
        }

        @Override
        protected void paintIndeterminate(Graphics g, JComponent c) {
            paintDeterminate(g, c);
        }
    }

    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(203, 213, 225);
            trackColor = Color.WHITE;
        }

        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private JButton zeroButton() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d);
            b.setMinimumSize(d);
            b.setMaximumSize(d);
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }
}