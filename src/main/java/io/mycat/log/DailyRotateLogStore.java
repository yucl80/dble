package io.mycat.log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DailyRotateLogStore {
    private static final String FILE_SEPARATOR = String.valueOf(File.separatorChar);
    private String prefix;
    private String suffix;
    private String fileName;
    private final String mode;
    private long maxFileSize;
    private int currentIndex;
    private long nextCheckTime;
    private Calendar cal;
    private Date now;
    private SimpleDateFormat dateFormat;
    private String dateString;
    private long pos;
    private FileChannel channel;

    /**
     * @param baseDir
     * @param baseName
     * @param suffix
     * @param rolateSize unit:M
     */
    public DailyRotateLogStore(String baseDir, String baseName, String suffix, int rolateSize) {
        if (!baseDir.endsWith(FILE_SEPARATOR)) {
            baseDir += FILE_SEPARATOR;
        }
        this.prefix = baseDir + baseName;
        this.suffix = suffix;
        this.fileName = this.prefix + "." + suffix;
        this.mode = "rw";
        this.maxFileSize = 1024L * 1024 * rolateSize;
        this.nextCheckTime = System.currentTimeMillis() - 1;
        this.cal = Calendar.getInstance();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        this.dateString = dateFormat.format(new Date());
    }

    public void open() throws IOException {
        File f = new File(fileName);
        createDirectorys(f.getAbsoluteFile().getParentFile().getAbsolutePath());
        if (now == null) {
            if (f.exists() && System.currentTimeMillis() > f.lastModified()) {
                now = new Date(f.lastModified());
            } else {
                now = new Date();
            }
        }
        nextCheckTime = calculateNextCheckTime(now);
        dateString = dateFormat.format(now);
        if (f.exists()) {
            indexRollOver();
        } else {
            channel = new RandomAccessFile(fileName, mode).getChannel();
        }
    }

    public void close() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                //ignore error
            }
        }
    }

    public void createDirectorys(String dir) {
        if (dir != null) {
            File f = new File(dir);
            if (!f.exists()) {
                String parent = f.getParent();
                createDirectorys(parent);
                f.mkdir();
            }
        }
    }

    public void write(ByteBuffer buffer) throws IOException {
        long n = System.currentTimeMillis();
        if (n >= nextCheckTime) {
            now.setTime(n);
            nextCheckTime = calculateNextCheckTime(now);
            dateRollOver();
        }

        do {
            if (maxFileSize > pos + buffer.remaining()) {
                pos += channel.write(buffer);
            } else if (maxFileSize == pos) {
                // create new file
                indexRollOver();
                pos = 0;
            } else {
                int length = (int) (maxFileSize - pos);
                int limit = buffer.limit();
                buffer.limit(length + buffer.position());
                pos += channel.write(buffer);
                buffer.limit(limit);
            }
        } while (buffer.hasRemaining());
    }

    public void force(boolean metaData) throws IOException {
        if (channel != null) {
            channel.force(metaData);
        }
    }

    private void dateRollOver() throws IOException {
        if (new File(fileName).length() > 0)
            indexRollOver();
        dateString = dateFormat.format(now);
        currentIndex = 0;
    }

    @SuppressWarnings("resource")
    private void indexRollOver() throws IOException {
        currentIndex++;
        File target;
        while (true) {
            target = new File(buildRollFileName(currentIndex));
            if (target.exists()) {
                currentIndex++;
            } else {
                break;
            }
        }
        force(false);
        close();

        File file;
        file = new File(fileName);
        file.renameTo(target);
        channel = new RandomAccessFile(fileName, mode).getChannel();
    }

    private String buildRollFileName(int index) {
        String buf = prefix + '_' +
                dateString +
                '.' +
                index +
                '.' +
                suffix;
        return buf;
    }

    private long calculateNextCheckTime(Date now) {
        cal.setTime(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, 1);
        return cal.getTimeInMillis();
    }
}
