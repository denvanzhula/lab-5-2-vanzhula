package ua.edu.chmnu.network.java;

import java.io.*;
import java.net.*;

public class DownloadTask implements Runnable {
    private final String fileURL;
    private final String saveFilePath;
    private final int numThreads;
    private volatile boolean stopRequested = false;

    public DownloadTask(String fileURL, String saveFilePath, int numThreads) {
        this.fileURL = fileURL;
        this.saveFilePath = saveFilePath;
        this.numThreads = numThreads;
    }

    @Override
    public void run() {
        try {
            URL url = new URL(fileURL);
            URLConnection connection = url.openConnection();
            int fileSize = connection.getContentLength();
            int chunkSize = fileSize / numThreads;

            RandomAccessFile outputFile = new RandomAccessFile(saveFilePath, "rw");
            outputFile.setLength(fileSize);
            outputFile.close();

            for (int i = 0; i < numThreads; i++) {
                int startByte = i * chunkSize;
                int endByte = (i == numThreads - 1) ? fileSize - 1 : (startByte + chunkSize - 1);
                new Thread(new DownloadChunk(fileURL, saveFilePath, startByte, endByte, i + 1)).start();
            }
        } catch (Exception e) {
            System.err.println("Error downloading file: " + e.getMessage());
        }
    }

    public void stopDownload() {
        stopRequested = true;
    }
}

class DownloadChunk implements Runnable {
    private final String fileURL;
    private final String saveFilePath;
    private final int startByte;
    private final int endByte;
    private final int threadId;

    public DownloadChunk(String fileURL, String saveFilePath, int startByte, int endByte, int threadId) {
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
                outputFile.write(buffer, 0, bytesRead);
            }

            outputFile.close();
            inputStream.close();

            System.out.println("Thread " + threadId + " completed downloading its chunk.");
        } catch (Exception e) {
            System.err.println("Thread " + threadId + " failed: " + e.getMessage());
        }
    }
}
