package wxgaly.android.facedetectiondemo;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

//import android.R;

/**
 * UncaughtException处理类,当系统发生Uncaught异常时，将相关log和机器信息保存并上传到服务器
 *
 * @author 陈志泉
 * @ClassName CrashHandler
 * @date 2015年4月7日 上午10:30:54
 */
public class CrashHandler implements UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static final int MAX_CRASH_LOG_SIZE = 10 * 1024 * 1024;
    private UncaughtExceptionHandler mDefaultHandler;// 系统默认的UncaughtException处理类
    private static CrashHandler INSTANCE = new CrashHandler();// CrashHandler实例
    private Context mContext;
    private Map<String, String> info = new HashMap<String, String>();// 用来存储设备信息和异常信息
    private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");// 用于格式化日期

    private Lock lock = null;//防止多线程中的异常导致读写不同步问题的lock

    private static final String LOG_PATH = "/sdcard/wxgaly" + File.separator + "crashlog";


    private CrashHandler() {
        lock = new ReentrantLock(true);
    }

    public static CrashHandler getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        mContext = context;
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        //clearOldCrashLog();
    }

    /*
     * 当UncaughtException发生时会转入该重写的方法来处理
     */
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(ex) && mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(thread, ex);
        } else {
        }

        try {
            thread.sleep(2000);// 让程序继续运行2秒再退出
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        exitApp(mContext);
    }

    /**
     * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
     *
     * @param ex 异常信息
     * @return true 如果处理了该异常信息;否则返回false.
     */
    public boolean handleException(Throwable ex) {
        if (ex == null)
            return false;
        ex.printStackTrace();

        // 收集设备参数信息
        collectDeviceInfo(mContext);

        // 保存日志文件
        String filePath = saveCrashInfo2File(ex);

        return true;
    }

    private String readLog(String filePath) {
        String path = filePath;
        //将“+=”改为StringBuilder.append方法 --bcc
        StringBuilder log = null;

        File file = new File(path);
        if (file.isDirectory()) {
        } else {
            try {
                InputStream instream = new FileInputStream(file);
                if (null != instream) {
                    InputStreamReader inputreader = new InputStreamReader(instream);
                    BufferedReader buffreader = new BufferedReader(inputreader);
                    String line;
                    while (null != (line = buffreader.readLine())) {
                        log.append(line).append("\n");
                    }
                    instream.close();
                }
            } catch (java.io.FileNotFoundException e) {
                return null;
            } catch (IOException e) {
            }
        }

        return log.toString();
    }

    /**
     * 检查缓存的crashLog,如果大小超过MAX_CRASH_LOG_SIZE（10M），
     * 就删除早期的log，缓存的crashLog可导出，用于分析。
     */
    private void clearOldCrashLog() {
        List<File> crashFiles = new ArrayList<File>();
        File[] files = new File(getCashLogBackupDir()).listFiles();
        for (File tmpfile : files) {
            if (tmpfile.getName().indexOf("crash-") >= 0 && tmpfile.getName().indexOf(".log") >= 0) {
                crashFiles.add(tmpfile);
            }
        }
        sortCrashLogByCreateDate(crashFiles);
        long size = 0;
        int i = 0;
        for (; i < crashFiles.size(); i++) {
            if (crashFiles.get(i) != null) {
                size += crashFiles.get(i).length();
            }
            if (size > MAX_CRASH_LOG_SIZE) {
                break;
            }
        }
        if ((i + 1) < crashFiles.size()) {
            for (; i < crashFiles.size(); i++) {
                if (crashFiles.get(i) != null) {
                    crashFiles.get(i).delete();
                }
            }
        }
    }

    private void sortCrashLogByCreateDate(List<File> crashFiles) {
        Collections.sort(crashFiles, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                String timestamp1 = splitTimestamp(lhs.getName());
                String timestamp2 = splitTimestamp(rhs.getName());
                if (!TextUtils.isEmpty(timestamp1) && !TextUtils.isEmpty(timestamp2)
                        && isNumeric(timestamp1) && isNumeric(timestamp2)) {
                    long time1 = Long.parseLong(timestamp1);
                    long time2 = Long.parseLong(timestamp2);
                    if (time1 > time2) {
                        return -1;
                    } else if (time1 < time2) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
                return 0;
            }
        });
    }

    private String splitTimestamp(String source) {
        String result = null;
        if (source != null) {
            String temp = source.split("\\.")[0];
            String[] temps = temp.split("-");
            if (temps.length > 0) {
                result = temps[temps.length - 1];
            }
        }
        return result;
    }

    /**
     * 收集设备参数信息
     *
     * @param context
     */
    public void collectDeviceInfo(Context context) {
        try {
            PackageManager pm = context.getPackageManager();// 获得包管理器
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_ACTIVITIES);// 得到该应用的信息，即主Activity
            if (pi != null) {
                String versionName = pi.versionName == null ? "null"
                        : pi.versionName;
                String versionCode = pi.versionCode + "";
                info.put("versionName", versionName);
                info.put("versionCode", versionCode);
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        Field[] fields = Build.class.getDeclaredFields();// 反射机制
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                info.put(field.getName(), field.get("").toString());
//				Log.d(TAG, field.getName() + ":" + field.get(""));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private String saveCrashInfo2File(Throwable ex) {
        File saveFile = null;
        PrintWriter printWriter = null;
        long timetamp = System.currentTimeMillis();
        String time = format.format(new Date());
        String fileName = "crash-" + time + "-" + timetamp + ".log";

        FileOutputStream fos = null;
        try {
            lock.tryLock();
            StringBuffer sb = new StringBuffer();
            for (Map.Entry<String, String> entry : info.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                sb.append(key + "=" + value + "\r\n");
            }

            File file = new File(LOG_PATH + File.separator + fileName);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try {
                    if (file.exists()) {
                        file.delete();
                    }
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            fos = new FileOutputStream(LOG_PATH + File.separator + fileName);
            String result = formatException(ex);
            fos.write(result.getBytes());
            fos.flush();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            lock.unlock();
        }

        return saveFile != null ? saveFile.getAbsolutePath() : null;
    }

    /**
     * 格式化异常信息
     *
     * @param e
     * @return
     */
    private String formatException(Throwable e) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null) {
            String timeStramp = format.format(new Date(System.currentTimeMillis()));
            String format = String.format("DateTime:%s\nExceptionName:%s\n\n", timeStramp, e.getLocalizedMessage());
            sb.append(format);
            for (int i = 0; i < stackTrace.length; i++) {
                StackTraceElement traceElement = stackTrace[i];
                String fileName = traceElement.getFileName();
                int lineNumber = traceElement.getLineNumber();
                String methodName = traceElement.getMethodName();
                String className = traceElement.getClassName();
                sb.append(String.format("%s\t%s[%d].%s \n", className, fileName, lineNumber, methodName));
            }
            sb.append(String.format("\n%s", e.getMessage()));
            Writer stringWriter = new StringWriter();
            PrintWriter pw = new PrintWriter(stringWriter);
            e.printStackTrace(pw);
            pw.flush();
            pw.close();

            sb.append("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
            sb.append(stringWriter.toString());
        }
        return sb.toString();
    }

    private String getCashLogBackupDir() {
        String path = mContext.getFilesDir().getAbsolutePath() + File.separator + "backup";
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return path;
    }

    /**
     * 退出应用程序
     */
    public void exitApp(Context context) {
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (Exception e) {
        }
    }

    private final static Pattern numericPattern = Pattern.compile("[0-9]*");

    /**
     * 判断字符串是否全数字
     *
     * @param str
     * @return
     */
    public static boolean isNumeric(String str) {
        if (str == null || str.trim().length() == 0)
            return false;
        return numericPattern.matcher(str).matches();
    }

}
