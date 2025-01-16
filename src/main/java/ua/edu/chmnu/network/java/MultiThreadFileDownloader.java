package ua.edu.chmnu.network.java;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class MultiThreadFileDownloader extends JFrame {
    private JTextField urlField;
    private JTextField saveDirField;
    private JButton downloadButton;
    private JButton stopButton;
    private JProgressBar progressBar;
    private JTextArea logArea;

    private ExecutorService executorService;
    private volatile boolean isDownloading;
    private volatile boolean isPaused;
    private String fileURL;
    private String saveDir;

    public MultiThreadFileDownloader() {
        setTitle("Multi-Threaded File Downloader");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new GridLayout(3, 2));
        inputPanel.add(new JLabel("File URL:"));
        urlField = new JTextField();
        inputPanel.add(urlField);
        inputPanel.add(new JLabel("Save Directory:"));
        saveDirField = new JTextField();
        inputPanel.add(saveDirField);
        downloadButton = new JButton("Download");
        inputPanel.add(downloadButton);
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        inputPanel.add(stopButton);
        add(inputPanel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        add(progressBar, BorderLayout.CENTER);

        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        add(logScrollPane, BorderLayout.SOUTH);

        downloadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                downloadFiles();
            }
        });

        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopDownload();
            }
        });
    }

    private void downloadFiles() {
        fileURL = urlField.getText();
        saveDir = saveDirField.getText();

        if (fileURL.isEmpty() || saveDir.isEmpty()) {
            logArea.append("Please provide a valid URL and save directory.\n");
            return;
        }

        if (checkIfFileExistsAndSame(fileURL, saveDir)) {
            logArea.append("File already exists and is up to date.\n");
            return;
        }

        executorService = Executors.newFixedThreadPool(4);
        isDownloading = true;
        isPaused = false;
        downloadButton.setEnabled(false);
        stopButton.setEnabled(true);

        try {
            URL url = new URL(fileURL);
            URLConnection connection = url.openConnection();
            int fileSize = connection.getContentLength();
            if (fileSize <= 0) {
                logArea.append("Invalid file size or inaccessible URL.\n");
                return;
            }

            String fileName = new File(url.getPath()).getName();
            String saveFilePath = saveDir + File.separator + fileName;
            RandomAccessFile outputFile = new RandomAccessFile(saveFilePath, "rw");
            outputFile.setLength(fileSize);
            outputFile.close();

            int chunkSize = fileSize / 4;
            for (int i = 0; i < 4; i++) {
                int startByte = i * chunkSize;
                int endByte = (i == 3) ? fileSize - 1 : (startByte + chunkSize - 1);
                executorService.submit(new DownloadTask(fileURL, saveFilePath, startByte, endByte, i + 1));
            }
        } catch (Exception ex) {
            logArea.append("Error: " + ex.getMessage() + "\n");
        }
    }

    private void stopDownload() {
        isDownloading = false;
        executorService.shutdownNow();
        downloadButton.setEnabled(true);
        stopButton.setEnabled(false);
        logArea.append("Download stopped.\n");
    }

    private boolean checkIfFileExistsAndSame(String fileURL, String saveDir) {
        try {
            String fileName = new File(fileURL).getName();
            Path saveFilePath = Paths.get(saveDir, fileName);
            File saveFile = saveFilePath.toFile();

            if (saveFile.exists()) {
                URL url = new URL(fileURL);
                URLConnection connection = url.openConnection();
                long remoteFileSize = connection.getContentLengthLong();

                if (saveFile.length() == remoteFileSize) {
                    return true;
                }
            }
        } catch (IOException ex) {
            logArea.append("Error checking file existence: " + ex.getMessage() + "\n");
        }

        return false;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MultiThreadFileDownloader frame = new MultiThreadFileDownloader();
                frame.setVisible(true);
            }
        });
    }

    class DownloadTask implements Runnable {
        private String fileURL;
        private String saveFilePath;
        private int startByte;
        private int endByte;
        private int threadId;

        public DownloadTask(String fileURL, String saveFilePath, int startByte, int endByte, int threadId) {
            this.fileURL = fileURL;
            this.saveFilePath = saveFilePath;
            this.startByte = startByte;
            this.endByte = endByte;
            this.threadId = threadId;
        }

        @Override
        public void run() {
            try {
                URL url = new URL(fileURL);
                URLConnection connection = url.openConnection();
                connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

                InputStream inputStream = connection.getInputStream();
                RandomAccessFile outputFile = new RandomAccessFile(saveFilePath, "rw");
                outputFile.seek(startByte);

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (!isDownloading) break;
                    outputFile.write(buffer, 0, bytesRead);
                }

                outputFile.close();
                inputStream.close();

                logArea.append("Thread " + threadId + " completed downloading its chunk.\n");
                updateProgressBar();
            } catch (Exception e) {
                logArea.append("Thread " + threadId + " failed: " + e.getMessage() + "\n");
            }
        }

        private void updateProgressBar() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    int totalProgress = (int) (100 * (startByte + (endByte - startByte)) / (float) getFileSize());
                    progressBar.setValue(totalProgress);
                }
            });
        }

        private int getFileSize() {
            try {
                URL url = new URL(fileURL);
                URLConnection connection = url.openConnection();
                return connection.getContentLength();
            } catch (IOException ex) {
                return 0;
            }
        }
    }
}