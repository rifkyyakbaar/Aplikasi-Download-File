import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.zip.*;
import java.awt.Desktop;

public class AplikasiDownloadFile extends JFrame {

    private JTextField txtUrl;
    private JButton btnDownload;
    private JProgressBar progressBar;
    private JLabel lblStatus;
    
    private JTable tableHistory;
    private DefaultTableModel tableModel;

    public AplikasiDownloadFile() {
        setTitle("Aplikasi Download File");
        setSize(700, 450); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));
        setLocationRelativeTo(null);

        Font fontUtama = new Font("Segoe UI", Font.PLAIN, 14);

        JPanel panelAtas = new JPanel(new BorderLayout(10, 10));
        JLabel lblUrl = new JLabel("Masukkan URL:");
        lblUrl.setFont(fontUtama);
        txtUrl = new JTextField();
        txtUrl.setFont(fontUtama);
        panelAtas.add(lblUrl, BorderLayout.WEST);
        panelAtas.add(txtUrl, BorderLayout.CENTER);

        JPanel panelProgress = new JPanel(new GridLayout(2, 1, 5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));

        lblStatus = new JLabel("Status: Menunggu input URL...", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblStatus.setForeground(Color.GRAY);
        
        panelProgress.add(progressBar);
        panelProgress.add(lblStatus);

        JPanel panelKontrol = new JPanel(new BorderLayout(10, 10));
        panelKontrol.add(panelAtas, BorderLayout.NORTH);
        panelKontrol.add(panelProgress, BorderLayout.SOUTH);

        String[] namaKolom = {"URL", "Nama File", "Ukuran", "Jenis File", "Status"};
        tableModel = new DefaultTableModel(namaKolom, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; 
            }
        };
        tableHistory = new JTable(tableModel);
        tableHistory.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tableHistory.setRowHeight(25); 
        JScrollPane scrollPane = new JScrollPane(tableHistory); 

        tableHistory.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int barisTerpilih = tableHistory.getSelectedRow();
                if (barisTerpilih != -1) {
                    String urlDariHistory = tableModel.getValueAt(barisTerpilih, 0).toString();
                    txtUrl.setText(urlDariHistory);
                }
            }
        });

        btnDownload = new JButton("Mulai Download");
        btnDownload.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnDownload.setBackground(new Color(41, 128, 185)); 
        btnDownload.setForeground(Color.BLACK); 
        btnDownload.setFocusPainted(false); 
        btnDownload.setCursor(new Cursor(Cursor.HAND_CURSOR)); 

        btnDownload.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mulaiDownload();
            }
        });

        add(panelKontrol, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER); 
        add(btnDownload, BorderLayout.SOUTH);
        
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
        btnDownload.setBackground(Color.GRAY);
        progressBar.setValue(0);
        progressBar.setString("Menghubungkan...");
        lblStatus.setText("Status: Menghubungkan ke server...");
        lblStatus.setForeground(Color.BLUE);

        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                prosesDownloadDanEkstrak(urlString);
            }
        });
        downloadThread.start();
    }

    private void prosesDownloadDanEkstrak(String urlString) {
        String namaFileUntukHistory = "-";
        String ukuranUntukHistory = "Tidak diketahui";
        String jenisUntukHistory = "Tidak diketahui";
        try {
            URL url = new URI(urlString).toURL();
            HttpURLConnection koneksi = (HttpURLConnection) url.openConnection();
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

            InputStream inputStream = koneksi.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(namaFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesDiterima = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
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

            outputStream.close();
            inputStream.close();
            koneksi.disconnect();

            tambahKeHistory(urlString, namaFileUntukHistory, ukuranUntukHistory, jenisUntukHistory, "Berhasil");

            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("Status: Selesai!");
                lblStatus.setForeground(new Color(39, 174, 96)); 
                kembalikanTombolSemula();
                JOptionPane.showMessageDialog(AplikasiDownloadFile.this, "Download Selesai!\nFile tersimpan sebagai: " + namaFile, "Sukses", JOptionPane.INFORMATION_MESSAGE);
            });

            if (namaFile.toLowerCase().endsWith(".zip")) {
                int pilihan = JOptionPane.showConfirmDialog(
                        AplikasiDownloadFile.this,
                        "File yang didownload berformat ZIP.\nApakah kamu ingin mengekstraknya sekarang?",
                        "Ekstrak File ZIP",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

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
                JOptionPane.showMessageDialog(AplikasiDownloadFile.this, "Gagal mendownload file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void tambahKeHistory(String url, String namaFile, String ukuran, String jenis, String status) {
        SwingUtilities.invokeLater(() -> {
            tableModel.addRow(new Object[]{url, namaFile, ukuran, jenis, status});
        });
    }

    private void kembalikanTombolSemula() {
        btnDownload.setEnabled(true);
        btnDownload.setText("Mulai Download");
        btnDownload.setBackground(new Color(41, 128, 185)); 
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
                    FileOutputStream fos = new FileOutputStream(fileBaru);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
            JOptionPane.showMessageDialog(this, "Ekstraksi berhasil ke folder: " + namaFolderTarget, "Info Ekstrak", JOptionPane.INFORMATION_MESSAGE);
            
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folderTarget);
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Gagal mengekstrak: " + e.getMessage(), "Error Ekstrak", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new AplikasiDownloadFile().setVisible(true);
            }
        });
    }
}