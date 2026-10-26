import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.zip.*;
import javax.swing.*;
import javax.swing.table.*;

public class AplikasiDownloadFile extends JFrame {

    private JTextField txtUrl;
    private JButton btnDownload, btnTempel, btnBukaFile, btnBukaFolder, btnHapus;
    private JProgressBar progressBar;
    private JLabel lblStatus;
    
    private JTable tableHistory;
    private DefaultTableModel tableModel;
    
    private JLabel lblStatusKiri, lblStatusTengah, lblStatusKanan;

    public AplikasiDownloadFile() {
        setTitle("VelocityFetch - Advanced Download Manager");
        setSize(850, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setLocationRelativeTo(null);

        Font fontUtama = new Font("Segoe UI", Font.PLAIN, 14);
        Font fontBold = new Font("Segoe UI", Font.BOLD, 14);

        JPanel panelInput = new JPanel(new BorderLayout(10, 10));
        JLabel lblUrl = new JLabel("Masukkan URL:");
        lblUrl.setFont(fontBold);
        txtUrl = new JTextField();
        txtUrl.setFont(fontUtama);
        txtUrl.setMargin(new Insets(5, 5, 5, 5));
        
        btnTempel = new JButton("Tempel");
        btnTempel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnTempel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        btnTempel.addActionListener(e -> {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable contents = clipboard.getContents(null);
                if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String pasteString = (String) contents.getTransferData(DataFlavor.stringFlavor);
                    txtUrl.setText(pasteString);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Gagal menempelkan teks!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panelInput.add(lblUrl, BorderLayout.WEST);
        panelInput.add(txtUrl, BorderLayout.CENTER);
        panelInput.add(btnTempel, BorderLayout.EAST);

        JPanel panelProgress = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        progressBar.setForeground(new Color(0, 120, 215));
        progressBar.setBackground(Color.WHITE);
        progressBar.setPreferredSize(new Dimension(100, 30));

        lblStatus = new JLabel("Status: Menunggu input URL...", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        lblStatus.setForeground(new Color(39, 174, 96));
        
        panelProgress.add(progressBar, BorderLayout.CENTER);
        panelProgress.add(lblStatus, BorderLayout.SOUTH);

        JPanel panelAtas = new JPanel(new BorderLayout(10, 15));
        panelAtas.add(panelInput, BorderLayout.NORTH);
        panelAtas.add(panelProgress, BorderLayout.CENTER);

        String[] namaKolom = {"URL", "Nama File", "Ukuran", "Jenis File", "Status"};
        tableModel = new DefaultTableModel(namaKolom, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tableHistory = new JTable(tableModel);
        tableHistory.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tableHistory.setRowHeight(25);
        tableHistory.setSelectionBackground(new Color(0, 120, 215));
        tableHistory.setSelectionForeground(Color.WHITE);
        tableHistory.setShowVerticalLines(false); 

        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    if ("Berhasil".equals(value)) {
                        c.setForeground(new Color(39, 174, 96));
                    } else if ("Gagal".equals(value)) {
                        c.setForeground(Color.RED);
                    } else {
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        };
        tableHistory.getColumnModel().getColumn(4).setCellRenderer(statusRenderer);

        JScrollPane scrollPane = new JScrollPane(tableHistory);
        scrollPane.getViewport().setBackground(Color.WHITE);

        tableHistory.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int baris = tableHistory.getSelectedRow();
                if (baris != -1) {
                    txtUrl.setText(tableModel.getValueAt(baris, 0).toString());
                }
            }
        });

        JPanel panelAksi = new JPanel(new BorderLayout(10, 10));
        
        btnDownload = new JButton("Mulai Download");
        btnDownload.setFont(fontBold);
        btnDownload.setBackground(Color.WHITE);
        btnDownload.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDownload.setPreferredSize(new Dimension(400, 35));
        btnDownload.addActionListener(e -> mulaiDownload());

        JPanel panelTombolKanan = new JPanel(new GridLayout(1, 3, 5, 0));
        btnBukaFile = new JButton("Buka File");
        btnBukaFolder = new JButton("Buka Folder");
        btnHapus = new JButton("Hapus");
        
        btnBukaFile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBukaFolder.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnHapus.setCursor(new Cursor(Cursor.HAND_CURSOR));

        panelTombolKanan.add(btnBukaFile);
        panelTombolKanan.add(btnBukaFolder);
        panelTombolKanan.add(btnHapus);

        panelAksi.add(btnDownload, BorderLayout.CENTER);
        panelAksi.add(panelTombolKanan, BorderLayout.EAST);

        btnBukaFolder.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(System.getProperty("user.dir")));
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
            try {
                String namaFile = tableModel.getValueAt(baris, 1).toString();
                Desktop.getDesktop().open(new File(namaFile));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "File tidak ditemukan atau gagal dibuka!", "Error", JOptionPane.ERROR_MESSAGE);
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
        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        
        lblStatusKiri = new JLabel("Siap");
        lblStatusKiri.setForeground(Color.GRAY);
        
        JPanel statusKanan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        lblStatusTengah = new JLabel("Koneksi: Terhubung");
        lblStatusTengah.setForeground(Color.GRAY);
        lblStatusKanan = new JLabel("Total: 0 Unduhan");
        lblStatusKanan.setForeground(Color.GRAY);
        
        statusKanan.add(lblStatusTengah);
        statusKanan.add(lblStatusKanan);
        
        statusBar.add(lblStatusKiri, BorderLayout.WEST);
        statusBar.add(statusKanan, BorderLayout.EAST);

        JPanel panelBawah = new JPanel(new BorderLayout(0, 10));
        panelBawah.add(panelAksi, BorderLayout.CENTER);
        panelBawah.add(statusBar, BorderLayout.SOUTH);

        add(panelAtas, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER); 
        add(panelBawah, BorderLayout.SOUTH);
        
        getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
    }

    private void mulaiDownload() {
        String urlString = txtUrl.getText().trim();
        if (urlString.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Masukkan URL terlebih dahulu!", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnDownload.setEnabled(false);
        btnDownload.setText("Sedang Mendownload...");
        progressBar.setValue(0);
        progressBar.setString("Menghubungkan...");
        lblStatus.setText("Status: Menghubungkan ke server...");
        lblStatus.setForeground(new Color(0, 120, 215));

        Thread downloadThread = new Thread(() -> prosesDownloadDanEkstrak(urlString));
        downloadThread.start();
    }

    private void prosesDownloadDanEkstrak(String urlString) {
        String namaFileUntukHistory = "-";
        String ukuranUntukHistory = "Tidak diketahui";
        String jenisUntukHistory = "Unknown";

        try {
            URL url = new URI(urlString).toURL();
            URLConnection koneksi = url.openConnection();
            int ukuranFileTotal = koneksi.getContentLength(); 

            String tempNama = urlString.substring(urlString.lastIndexOf("/") + 1);
            if (tempNama.isEmpty() || !tempNama.contains(".")) {
                tempNama = "downloaded_file.zip"; 
            }
            final String namaFile = tempNama; 
            namaFileUntukHistory = namaFile; 

            if (namaFile.contains(".")) {
                jenisUntukHistory = namaFile.substring(namaFile.lastIndexOf(".") + 1).toUpperCase();
            }

            if (ukuranFileTotal > 0) {
                double mbTotal = (double) ukuranFileTotal / 1048576;
                ukuranUntukHistory = String.format("%.2f MB", mbTotal);
            }

            SwingUtilities.invokeLater(() -> lblStatus.setText("Status: Mengunduh file " + namaFile + "..."));

            BufferedInputStream inStream = new BufferedInputStream(koneksi.getInputStream());
            BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(namaFile));

            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytesDiterima = 0;

            while ((bytesRead = inStream.read(buffer, 0, 1024)) != -1) {
                outStream.write(buffer, 0, bytesRead);
                totalBytesDiterima += bytesRead;

                if (ukuranFileTotal > 0) {
                    int persentase = (int) ((totalBytesDiterima * 100) / ukuranFileTotal);
                    double mbDiterima = (double) totalBytesDiterima / 1048576;
                    double mbTotal = (double) ukuranFileTotal / 1048576;
                    String progressTeks = String.format("%d%% (%.2f MB / %.2f MB)", persentase, mbDiterima, mbTotal);

                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(persentase);
                        progressBar.setString(progressTeks);
                    });
                }
            }

            outStream.flush();
            outStream.close();
            inStream.close();

            tambahKeHistory(urlString, namaFileUntukHistory, ukuranUntukHistory, jenisUntukHistory, "Berhasil");

            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("Status: Selesai!");
                lblStatus.setForeground(new Color(39, 174, 96));
                kembalikanTombolSemula();
            });

            if (namaFile.toLowerCase().endsWith(".zip")) {
                int pilihan = JOptionPane.showConfirmDialog(this,
                        "File yang didownload berformat ZIP.\nApakah kamu ingin mengekstraknya sekarang?",
                        "Ekstrak File ZIP", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (pilihan == JOptionPane.YES_OPTION) {
                    ekstrakZip(namaFile);
                }
            }

        } catch (Exception e) {
            tambahKeHistory(urlString, namaFileUntukHistory, ukuranUntukHistory, jenisUntukHistory, "Gagal");

            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("Status: Gagal mengunduh!");
                lblStatus.setForeground(Color.RED);
                progressBar.setString("Error!");
                kembalikanTombolSemula();
                JOptionPane.showMessageDialog(this, "Gagal mendownload: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            });
        }
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

    private void kembalikanTombolSemula() {
        btnDownload.setEnabled(true);
        btnDownload.setText("Mulai Download");
    }

    private void ekstrakZip(String namaFileZip) {
        String namaFolderTarget = namaFileZip.substring(0, namaFileZip.length() - 4);
        File folderTarget = new File(namaFolderTarget);
        if (!folderTarget.exists()) {
            folderTarget.mkdir();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(namaFileZip))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File fileBaru = new File(folderTarget, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    fileBaru.mkdirs();
                } else {
                    new File(fileBaru.getParent()).mkdirs();
                    
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileBaru));
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        bos.write(buffer, 0, len);
                    }
                    bos.flush();
                    bos.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            JOptionPane.showMessageDialog(this, "Ekstraksi berhasil ke folder: " + namaFolderTarget, "Info", JOptionPane.INFORMATION_MESSAGE);
            
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folderTarget);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Gagal mengekstrak: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
}