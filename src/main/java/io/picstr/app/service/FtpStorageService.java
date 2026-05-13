package io.picstr.app.service;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.picstr.app.config.StorageProperties;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "ftp")
public class FtpStorageService implements StorageService {

    private final StorageProperties properties;

    public FtpStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) {
        FTPClient ftpClient = new FTPClient();
        try {
            var ftpProps = properties.getFtp();

            // Connect to FTP server
            ftpClient.connect(ftpProps.getHost(), ftpProps.getPort());
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                throw new RuntimeException("FTP server refused connection. Reply code: " + replyCode);
            }

            // Login
            if (!ftpClient.login(ftpProps.getUsername(), ftpProps.getPassword())) {
                throw new RuntimeException("Failed to login to FTP server");
            }

            // Set binary file type
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

            // Change to base directory
            if (!ftpClient.changeWorkingDirectory(ftpProps.getBasePath())) {
                log.warn("Base path does not exist on FTP server, trying to create it");
                if (!ftpClient.makeDirectory(ftpProps.getBasePath())) {
                    throw new RuntimeException("Failed to create base directory on FTP server");
                }
                ftpClient.changeWorkingDirectory(ftpProps.getBasePath());
            }

            // Upload file
            if (!ftpClient.storeFile(key, content)) {
                throw new RuntimeException("Failed to upload file to FTP server");
            }

            log.info("File uploaded to FTP server: {}", key);

        } catch (Exception e) {
            log.error("Failed to upload file to FTP server: {}", key, e);
            throw new RuntimeException("Failed to upload file: " + key, e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (Exception e) {
                log.error("Failed to close FTP connection", e);
            }
        }
    }

    @Override
    public Optional<StorageObject> get(String key) {
        FTPClient ftpClient = new FTPClient();
        try {
            var ftpProps = properties.getFtp();

            ftpClient.connect(ftpProps.getHost(), ftpProps.getPort());
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                throw new RuntimeException("FTP server refused connection. Reply code: " + replyCode);
            }

            if (!ftpClient.login(ftpProps.getUsername(), ftpProps.getPassword())) {
                throw new RuntimeException("Failed to login to FTP server");
            }

            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

            if (!ftpClient.changeWorkingDirectory(ftpProps.getBasePath())) {
                return Optional.empty();
            }

            var inputStream = ftpClient.retrieveFileStream(key);
            if (inputStream == null) {
                return Optional.empty();
            }

            var bytes = inputStream.readAllBytes();
            inputStream.close();
            if (!ftpClient.completePendingCommand()) {
                throw new RuntimeException("FTP transfer did not complete successfully");
            }

            var contentType = Files.probeContentType(Path.of(key));
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return Optional.of(new StorageObject(new ByteArrayInputStream(bytes), bytes.length, contentType));
        } catch (Exception e) {
            log.error("Failed to get file from FTP storage: {}", key, e);
            throw new RuntimeException("Failed to get file: " + key, e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (Exception e) {
                log.error("Failed to close FTP connection", e);
            }
        }
    }

    @Override
    public List<String> listKeys() {
        FTPClient ftpClient = new FTPClient();
        try {
            var ftpProps = properties.getFtp();

            ftpClient.connect(ftpProps.getHost(), ftpProps.getPort());
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                throw new RuntimeException("FTP server refused connection. Reply code: " + replyCode);
            }

            if (!ftpClient.login(ftpProps.getUsername(), ftpProps.getPassword())) {
                throw new RuntimeException("Failed to login to FTP server");
            }

            if (!ftpClient.changeWorkingDirectory(ftpProps.getBasePath())) {
                return List.of();
            }

            return List.of(ftpClient.listFiles())
                    .stream()
                    .filter(file -> file.isFile())
                    .map(file -> file.getName())
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list files from FTP storage", e);
            throw new RuntimeException("Failed to list files from FTP storage", e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (Exception e) {
                log.error("Failed to close FTP connection", e);
            }
        }
    }

    @Override
    public void delete(String key) {
        FTPClient ftpClient = new FTPClient();
        try {
            var ftpProps = properties.getFtp();

            ftpClient.connect(ftpProps.getHost(), ftpProps.getPort());
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                throw new RuntimeException("FTP server refused connection. Reply code: " + replyCode);
            }

            if (!ftpClient.login(ftpProps.getUsername(), ftpProps.getPassword())) {
                throw new RuntimeException("Failed to login to FTP server");
            }

            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

            if (!ftpClient.changeWorkingDirectory(ftpProps.getBasePath())) {
                return;
            }

            if (!ftpClient.deleteFile(key)) {
                var deleteReplyCode = ftpClient.getReplyCode();
                if (deleteReplyCode != 550) {
                    throw new RuntimeException("Failed to delete file from FTP server: " + key + " (reply " + deleteReplyCode + ")");
                }
            }
        } catch (Exception e) {
            log.error("Failed to delete file from FTP storage: {}", key, e);
            throw new RuntimeException("Failed to delete file: " + key, e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (Exception e) {
                log.error("Failed to close FTP connection", e);
            }
        }
    }
}
